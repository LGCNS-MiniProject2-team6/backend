# Kafka 기반 Spring Cloud Bus 실행·검증 (#15)

이번 브랜치는 **Bus만** 구현한다. 암복호화 API·ENCRYPT_KEY·암호문 저장은 포함하지 않는다. 암호화 PR #14는 별도 작업이며 먼저 머지할 필요가 없다.

## 무엇이 바뀌는가

`recu-config 변경·commit·push → Config Server에 관리자 POST 1회 → Kafka Bus 토픽 → Auth/User/AI/Blog/Gateway 설정 재조회 → RefreshScope 객체 재생성`

Git에 push하는 것만으로 실행 중인 앱이 바뀌지는 않는다. Git webhook 자동 갱신은 이번 범위가 아니다. Eureka는 Bus 참여 대상이 아니다. Config Server는 Git을 제공하고 이벤트를 발행하며, 자기 설정을 자기 자신에게서 내려받지 않는다.

- 여섯 앱에 `spring-cloud-starter-bus-kafka`와 공통 `bus-support` 라이브러리를 적용한다.
- `bus.yml`은 각 앱의 **부팅 설정**이다. Config Server도 Kafka 연결 정보를 먼저 알아야 하므로 Kafka 주소·인증은 프로세스 환경변수/Secret으로 받는다.
- `BUS_ENABLED=false`가 기본이다. Kafka가 없는 기존 IntelliJ 실행은 유지된다. 실제 Bus 검증 때 **Config+Gateway+네 서비스 모두 true**로 설정한다.
- 테스트 값은 중앙 `application.yml`의 `demo.config-version: v1`이다. 기존 `info.config.version`과 구분한다.
- `@RefreshScope`가 적용된 InfoContributor가 이 값을 읽는다. 다섯 클라이언트의 기존 `/actuator/info`에 `refresh.version/busId/pid/startTime`을 추가한다. 일반 업무 API는 추가하지 않는다.
- DB 연결·서버 포트·인증 키·모든 일반 Bean이 자동으로 바뀌는 것은 아니다. 실제 업무 설정에는 해당 Bean의 갱신 가능 여부를 따로 확인한다.

## 1. 가장 쉬운 자동 검증 (GitHub 토큰·AWS·Docker 불필요)

IntelliJ 하단 Terminal에서 **backend 폴더**를 기준으로 실행한다. Java/Gradle JVM은 17이다.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
bash ./gradlew test spotlessCheck bootJar :platform:config-server:writeBusTestClasspath --no-daemon
python3 scripts/verify-config.py --verify-bus
```

스크립트는 테스트 의존성의 **실제 Kafka KRaft 브로커**와 임시 Git, 빈 포트의 Eureka·Config·네 서비스·Gateway를 띄운다. 실행 중인 IntelliJ 앱이나 실제 설정 Git을 수정하지 않는다. 테스트 Kafka 클래스는 배포 JAR에 들어가지 않는다. 종료 시 스스로 띄운 프로세스만 정리한다.

확인 항목:

1. 여섯 Bus 참여자의 Kafka partition 할당과 Eureka 등록.
2. 다섯 클라이언트 `refresh.version=v1` 확인.
3. 임시 Git에서만 v2로 commit. **호출 전에는 여전히 v1**인지 확인.
4. 무인증 POST 401, 읽기 계정 POST 403, 잘못된 관리자 비밀번호 401, 관리자 GET 403.
5. 정상 관리자 POST **한 번** 후 다섯 앱이 v2로 바뀌고 PID·프로세스 시작 시각·Bus ID는 유지.
6. Gateway의 Bus 관리 경로와 내부 확인용 업무 경로가 404.
7. 잘못된 Config 읽기 비밀번호를 넣은 별도 클라이언트는 기동 실패.

이 검증은 임시 자격증명을 사용하며 실제 토큰을 출력하지 않는다. `--verify-bus`와 외부 `--git-uri`는 함께 쓸 수 없다. 환경별 Kafka ACL/TLS·AWS 네트워크 검증은 실제 공통 Kafka 준비 후 진행한다.

## 2. IntelliJ에서 눈으로 확인하기

### 로컬 테스트 Kafka 준비

위 빌드를 마친 뒤 별도 Terminal에서 다음 명령을 실행한다. 개발 테스트용 브로커이며 역할 3이 구축할 배포 Kafka를 대체하지 않는다.

```bash
java -Xms128m -Xmx512m -cp "$(cat platform/config-server/build/bus-test-classpath.txt)" \
  com.recu.platform.config.support.LocalKafkaBroker recu-config-bus-local
```

로그의 `KAFKA_READY=localhost:숫자`에서 주소를 복사한다. local 토픽은 이 도구가 미리 만든다. 이 터미널을 켜 둔다. 종료는 Ctrl+C다.

### 환경변수 추가

**Run → Edit Configurations → 각 앱 → Modify options → Environment variables**에 기존 값과 함께 추가한다. 아래 `실제포트`는 위 로그의 숫자로 바꾼다.

Config Server·Gateway·Auth·User·AI·Blog **여섯 실행 구성 공통**:

```text
BUS_ENABLED=true;BUS_DESTINATION=recu-config-bus-local;KAFKA_BOOTSTRAP_SERVERS=localhost:실제포트
```

**Config Server에만** 다음 두 값을 추가한다. xxxx는 직접 정한 관리자 비밀번호로 바꾼다. 관리자 아이디는 기존 CONFIG_USER와 달라야 한다.

```text
CONFIG_ADMIN_USER=bus-admin;CONFIG_ADMIN_PASSWORD=xxxx
```

- 기존 `CONFIG_USER/CONFIG_PASSWORD`는 Config와 다섯 클라이언트에서 계속 일치해야 한다. 설정을 **읽는** 계정이다.
- `CONFIG_ADMIN_USER/CONFIG_ADMIN_PASSWORD`는 Config에만 넣는다. Bus를 **실행하는** 계정이다. 둘 중 하나만 넣거나 읽기 계정과 아이디가 같으면 Config가 기동 실패한다.
- `CONFIG_GIT_USERNAME/CONFIG_GIT_TOKEN`은 여전히 Config에만 필요하다.
- `BUS_INSTANCE_ID`를 생략하면 앱 이름:HOSTNAME:포트로 생성한다. 여러 인스턴스가 동일한 값을 공유하지 않는다. Kubernetes에서는 HOSTNAME이 Pod 이름이므로 Pod별로 구분된다.
- Kafka SASL의 JAAS 값에는 세미콜론이 들어갈 수 있으므로 IntelliJ 변수 **표**에서 별도 행으로 입력한다.

### 실행 및 갱신

1. 로컬 recu-config의 이번 변경을 검토하고 원격 Config가 읽는 브랜치(main)에 반영한다. 현재 작업 브랜치만 로컬에 있으므로 자동으로 원격에 적용되지는 않는다. 중앙 파일 `application.yml`에 demo.config-version=v1이 있어야 한다.
2. 테스트 Kafka → Eureka → Config → 네 서비스와 Gateway 순서로 실행한다.
3. Kafka 바인딩의 `partitions assigned` 로그를 여섯 앱에서 확인한다. Eureka UP만으로 Kafka 연결 성공을 판단하지 않는다.
4. 브라우저로 8081·8082·8083·8084·8080의 `/actuator/info`를 각각 열어 `refresh.version=v1`과 pid/startTime을 기록한다. 포트를 바꿨다면 실제 포트를 쓴다.
5. recu-config **공통 application.yml의 demo.config-version만 v2로 바꿔 commit/push**한다. 도메인 파일에 같은 키를 중복 선언하지 않는다. 서버가 읽는 CONFIG_GIT_LABEL과 반영 브랜치가 같아야 한다.
6. 아직 재시작하지 않는다. 아래 명령을 한 번 실행한다. curl이 요청하면 관리자 비밀번호를 입력한다.

```bash
curl -i -u bus-admin -H 'Content-Type: application/json' \
  -X POST http://localhost:8888/actuator/busrefresh
```

7. `HTTP 204` 후 다섯 `/actuator/info`를 새로고침한다. v2로 변경되고 pid/startTime이 동일해야 한다. Config 자체의 refresh 버전은 대상이 아니다. Config는 중앙 설정의 클라이언트가 아니다.

## 3. 역할 2·3에게 넘길 계약

| 항목 | 계약 |
|---|---|
| Kafka bootstrap | 여섯 앱 모두 접근 가능한 `KAFKA_BOOTSTRAP_SERVERS`. Pod에서 localhost는 Kafka EC2가 아님 |
| advertised.listeners | bootstrap 이후 반환되는 모든 broker 주소도 앱/Pod에서 도달 가능해야 함 |
| 토픽 | local `recu-config-bus-local`, dev `recu-config-bus-dev`, prod `recu-config-bus-prod`; 도메인 이벤트와 분리 |
| 토픽 생성 | 앱 binder의 autoCreateTopics/autoAddPartitions=false. 역할 3이 사전 생성. 로컬 도구만 테스트 토픽 생성 |
| partition/replica | 로컬 1/1로 검증. 배포 replication은 실제 broker 수와 합의. broker 1대에 replication 3 금지 |
| consumer group | Bus에는 동일한 고정 group을 주입하지 않음. 앱별 익명 group으로 모두 이벤트 수신; 같은 group이면 분배되어 일부 앱만 수신 |
| ACL | 토픽 Read/Write/Describe, 사용하는 개별 consumer group Read 권한. 토픽 자동 생성 권한 불필요. 익명 group 허용 범위는 역할 3과 조율 |
| 인증 | KAFKA_SECURITY_PROTOCOL(기본 PLAINTEXT), KAFKA_SASL_MECHANISM(기본 PLAIN), KAFKA_SASL_JAAS_CONFIG. 실제 값은 Secret; TLS 사용 시 truststore 마운트/두 Kafka 설정 경로에 적용하는 설정을 추가 합의 |
| Secret | 읽기 계정은 Config+다섯 앱, 관리 계정·Git 토큰은 Config에만. Kafka 인증은 필요한 여섯 앱에 주입 |
| 환경 일치 | 배포 여섯 앱 모두 BUS_ENABLED=true, BUS_DESTINATION을 환경 토픽으로 명시. Config는 중앙 profile 파일을 읽지 않으므로 특히 빠뜨리지 않음 |
| 관측·관리 접근 | Config /actuator/busrefresh는 내부 관리자만. 다른 앱에는 해당 endpoint 노출 안 함. 외부 프록시는 /actuator/**를 차단하고 내부 포트포워딩으로 info 확인 |

각 Config 클라이언트의 `application-{환경}.yml`은 기본 토픽을 제공하지만 **명시한 환경변수 BUS_DESTINATION을 우선**한다. 부팅·비밀 설정을 Git으로 옮겨 환경변수를 모두 없애지 않는다. 실제 YAML의 Secret 참조와 Kafka 보안 방식은 역할 2·3의 배포 설정에 맞춘다.

## 4. 실패하면 확인할 순서

- 기동 실패: Kafka 토픽 사전 생성 여부, 여섯 앱의 bootstrap·destination·enabled 확인.
- `204`인데 일부만 v1: 각 앱 구독 partition 할당, 동일 topic, 고유 Bus ID, 고정 공통 consumer group 유무 확인.
- 전부 v1: push 대상 브랜치/CONFIG_GIT_LABEL, 중앙 demo.config-version 키와 앱별 중복 키 확인.
- `401`: 관리자 아이디·비밀번호/Config 재시작 적용 확인. `403`: 읽기 계정을 썼는지 확인. `415`: 요청 Content-Type을 application/json으로 지정.
- 관리자는 선택사항이다. 관리자 두 변수를 모두 빼면 설정 조회는 되지만 Bus POST를 호출할 계정이 없다.
- Bus가 필요 없는 진단 실행은 BUS_ENABLED=false로 복귀한다. 애플리케이션의 모든 설정이 자동 갱신되는 것으로 해석하지 않는다.

## 5. 로컬 검증 결과 (2026-11-01)

- Java 17에서 `test spotlessCheck bootJar` 통과.
- `verify-config.py --verify-bus`: 실제 KRaft Kafka, 관리자 정상 호출 1회, 다섯 앱 v1→v2와 동일 PID/startTime 확인. 접근 제어와 Gateway 관리 경로 차단도 통과.
- `verify-gateway.py`: 14개 라우팅/CORS/헤더 보존·미매칭 404·라우팅 누락 fail-fast 통과.
- `verify-msa.py`: Config/Kafka 없는 standalone health/probe 및 Eureka 등록 통과.
- AWS·역할 3의 배포 Kafka·TLS/SASL 실접속은 공통 인프라 준비 후 후속 통합 대상이다.

## 6. 변경 파일별 한 줄 요약

| 파일 | 한 줄 요약 |
|---|---|
| `config-repository-template/README.md` | Bus 설정 저장소 사용·갱신 방법 안내 |
| `config-repository-template/application-dev.yml` | 환경별 Bus 토픽 기본값 제공 |
| `config-repository-template/application-local.yml` | 환경별 Bus 토픽 기본값 제공 |
| `config-repository-template/application-prod.yml` | 환경별 Bus 토픽 기본값 제공 |
| `config-repository-template/application.yml` | 중앙 갱신 확인용 demo.config-version 추가 |
| `docs/CONFIG_SERVER_GUIDE.md` | 새 Bus 실행 안내 문서 연결 |
| `docs/GATEWAY_GUIDE.md` | 새 Bus 실행 안내 문서 연결 |
| `docs/MSA_LOCAL_GUIDE.md` | 새 Bus 실행 안내 문서 연결 |
| `docs/REMOTE_CONFIG_GUIDE.md` | 새 Bus 실행 안내 문서 연결 |
| `docs/SPRING_CLOUD_BUS_GUIDE.md` | Kafka 계약·IntelliJ 실행·자동 검증과 인계 절차 작성 |
| `gradle/msa-bus.gradle` | 여섯 참여 앱의 Kafka Bus 및 공통 라이브러리 의존성 통일 |
| `platform/api-gateway/.env.example` | 실값 없이 Bus/Kafka 부팅 변수와 필요한 계정 예시 추가 |
| `platform/api-gateway/build.gradle` | Kafka Bus 공통 의존성 적용 |
| `platform/api-gateway/src/main/resources/application.yml` | 공통 Bus 부팅 설정 import |
| `platform/bus-support/build.gradle` | Java 17 기반 공통 자동 구성 라이브러리 빌드 정의 |
| `platform/bus-support/src/main/java/com/recu/platform/bus/BusObservationConfiguration.java` | Bus 활성화 시 갱신 가능한 관측 Bean 자동 구성 |
| `platform/bus-support/src/main/java/com/recu/platform/bus/RefreshVersionInfoContributor.java` | 버전·Bus ID·PID·시작 시각만 기존 info에 추가 |
| `platform/bus-support/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | 공통 관측 자동 구성 등록 |
| `platform/bus-support/src/main/resources/bus.yml` | Kafka 주소·인증·고유 Bus ID·활성화 부팅 계약 정의 |
| `platform/config-server/.env.example` | 실값 없이 Bus/Kafka 부팅 변수와 필요한 계정 예시 추가 |
| `platform/config-server/build.gradle` | Bus 의존성과 실제 Kafka 테스트 전용 classpath 작업 추가 |
| `platform/config-server/src/main/java/com/recu/platform/config/ConfigSecurity.java` | Bus POST를 관리자 전용으로 제한하고 읽기 계정 분리 |
| `platform/config-server/src/main/resources/application.yml` | 공통 Bus 설정 import 및 관리자 변수·busrefresh 노출 추가 |
| `platform/config-server/src/test/java/com/recu/platform/config/support/LocalKafkaBroker.java` | Docker 없이 쓰는 실제 Kafka KRaft 검증 브로커 추가 |
| `platform/settings.gradle` | Bus 공통 라이브러리 Gradle 모듈 등록 |
| `scripts/verify-config.py` | 실제 Kafka 단일 이벤트 전파·권한·무재시작 검증 추가 |
| `scripts/verify-gateway.py` | 기존 진단은 Bus 비활성으로 고정하여 Kafka 없이 실행 |
| `scripts/verify-msa.py` | 기존 진단은 Bus 비활성으로 고정하여 Kafka 없이 실행 |
| `services/ai-service/build.gradle` | Kafka Bus 공통 의존성 적용 |
| `services/ai-service/src/main/resources/application.yml` | 공통 Bus 부팅 설정 import |
| `services/auth-service/build.gradle` | Kafka Bus 공통 의존성 적용 |
| `services/auth-service/src/main/resources/application.yml` | 공통 Bus 부팅 설정 import |
| `services/blog-service/build.gradle` | Kafka Bus 공통 의존성 적용 |
| `services/blog-service/src/main/resources/application.yml` | 공통 Bus 부팅 설정 import |
| `services/user-service/build.gradle` | Kafka Bus 공통 의존성 적용 |
| `services/user-service/src/main/resources/application.yml` | 공통 Bus 부팅 설정 import |
| `settings.gradle` | Bus 공통 라이브러리 Gradle 모듈 등록 |

별도 recu-config 변경: application.yml은 demo 버전, application-local/dev/prod.yml은 환경 토픽, README.md는 갱신 절차를 추가했다. 실제 비밀값이나 암호문은 추가하지 않았다.
