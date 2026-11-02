# MSA Dockerfile 및 실행 조건

## 빌드 대상

Java 17 실행 JAR을 미리 생성한 뒤, 각 모듈 폴더를 Docker 빌드 컨텍스트로 사용한다.
Dockerfile 안에서는 Gradle을 실행하지 않는다.

| 모듈 | JAR (모듈 기준) | 기본 포트 |
| --- | --- | --- |
| services/user-service | build/libs/user-service.jar | 8082 |
| services/blog-service | build/libs/blog-service.jar | 8084 |
| services/ai-service | build/libs/ai-service.jar | 8083 |
| platform/api-gateway | build/libs/api-gateway.jar | 8080 |
| platform/config-server | build/libs/config-server.jar | 8888 |
| platform/discovery-server | build/libs/discovery-server.jar | 8761 |

`platform/bus-support`는 라이브러리다. Discovery를 제외한 5개 실행 JAR에 포함되므로 별도 이미지를 만들지 않는다.
각 모듈의 `.dockerignore`는 해당 실행 JAR만 빌드 컨텍스트에 허용한다.
이미지는 Java 17 JRE와 실행 JAR을 사용하며 UID/GID 10001로 실행한다.
UTF-8, Asia/Seoul, JVM 최대 힙 비율 75%는 기존 루트 Dockerfile과 동일하게 설정했다.

## 실행 환경

아래 내용은 저장소의 모듈 설정과 `bus.yml` 기준이다. 중앙 Config 저장소에서 덮어쓰는 값은 배포 환경에서 별도로 확인한다.
비밀번호·토큰·암호화 키의 실제 값은 이미지에 넣지 않고 실행 환경에서 주입한다.

| 대상 | 환경변수 및 조건 |
| --- | --- |
| 전체 | `SERVER_PORT`로 포트 변경 가능. EXPOSE는 기본 포트 안내이며 호스트 포트를 게시하지 않는다. |
| Gateway, User, Blog, AI | `SPRING_PROFILES_ACTIVE`로 배포 프로필을 지정한다. 기본은 `local`. `CONFIG_SERVER_URL`, `CONFIG_USER`, `CONFIG_PASSWORD`, `CONFIG_GIT_LABEL`을 환경에 맞춘다. Config 연결은 필수이며 기본 URL은 `http://localhost:8888`, label은 `main`. |
| Config 및 위 4개 클라이언트 | `EUREKA_URL`을 Discovery 주소로 지정한다. 기본은 `http://localhost:8761/eureka/`. |
| Config Server | `CONFIG_USER`, `CONFIG_PASSWORD`, `ENCRYPT_KEY`를 제공한다. 암호화 키는 공백일 수 없다. `CONFIG_GIT_URI`는 기본 ReCu 설정 저장소, `CONFIG_GIT_LABEL`은 기본 `main`이며 시작 시 저장소 복제가 필요하다. 비공개 저장소는 `CONFIG_GIT_USERNAME`, `CONFIG_GIT_TOKEN`도 제공한다. |
| Config 관리 기능 | `CONFIG_ADMIN_USER`, `CONFIG_ADMIN_PASSWORD`를 함께 제공한다. 생략하면 관리 계정을 만들지 않는다. 관리 계정과 읽기 계정 이름은 달라야 한다. |
| Bus 사용 모듈 5개 | 기본 `BUS_ENABLED=false`. 사용하려면 `BUS_ENABLED=true`, `KAFKA_BOOTSTRAP_SERVERS`, 환경별 공통 `BUS_DESTINATION`을 지정한다. `BUS_INSTANCE_ID` 기본값에는 애플리케이션 이름·HOSTNAME·포트가 포함된다. Kafka의 해당 토픽은 미리 준비한다(자동 생성 비활성화). |
| Kafka 인증 | 기본 `KAFKA_SECURITY_PROTOCOL=PLAINTEXT`, `KAFKA_SASL_MECHANISM=PLAIN`. 인증 환경은 `KAFKA_SASL_JAAS_CONFIG`와 프로토콜·메커니즘을 실행 환경에 주입한다. |
| Discovery Server | Config Server·Kafka에 의존하지 않는다. 자신을 Eureka에 등록하거나 레지스트리를 가져오지 않는다. |

컨테이너 안의 `localhost`는 해당 컨테이너 자신이다. Config·Eureka·Kafka 연결에는 컨테이너 네트워크 또는 Kubernetes Service에서 접근 가능한 주소를 사용한다.
기본 시작 순서는 Discovery → Config Server → Gateway 및 업무 서비스다. Bus를 켜면 Kafka와 토픽도 먼저 준비한다.
User·Blog·AI에는 `standalone` 프로필이 있어 Config 연결을 제외할 수 있다. Gateway에는 같은 예외 프로필이 없다.
현재 업무 서비스 소스는 애플리케이션 시작 클래스 단계이며, 모듈 의존성에 DB/JPA가 없다. DB·JWT·AI 키가 필요한 기존 루트 모놀리스와 실행 조건을 구분한다.

## 다음 단계의 이미지 빌드 명령

저장소 루트에서 실행한다. 마지막 인자가 각 모듈 폴더여야 한다.

```powershell
docker build -t recu/user-service:local services/user-service
docker build -t recu/blog-service:local services/blog-service
docker build -t recu/ai-service:local services/ai-service
docker build -t recu/api-gateway:local platform/api-gateway
docker build -t recu/config-server:local platform/config-server
docker build -t recu/discovery-server:local platform/discovery-server
```

상태 확인 경로는 `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`다.
Config Server의 health 경로는 인증 없이 접근할 수 있다.

## 이번 확인 범위

- 실행 JAR 6개의 Boot Launcher, Start-Class, 시작 클래스 포함 여부와 Java 17 클래스 버전을 검사했다.
- 패키징된 클래스는 현재 `build/classes/java/main` 결과와, 리소스는 현재 `src/main/resources`와 일치했다.
- Bus 사용 모듈 5개의 실행 JAR에 `bus-support.jar`가 포함되어 있음을 확인했다.
- 위 검사는 JAR 구조·내용 검사다. 소스 재컴파일, 이미지 빌드, 컨테이너 기동, 외부 서비스 연결 성공을 의미하지 않는다.
