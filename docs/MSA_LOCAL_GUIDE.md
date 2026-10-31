# MSA 서비스 골격 실행 및 백엔드 인계 (이슈 #3 기준)

**현재 실행 순서 변경:** 이슈 #7부터 네 서비스의 기본 실행에는 Config Server가 필요하다. 먼저 [Config Server 실행 안내](CONFIG_SERVER_GUIDE.md)에 따라 Eureka → Config → 네 서비스를 실행한다. 아래 내용은 이슈 #3의 골격 구조와 Config를 명시적으로 끈 진단 실행을 설명한다.

관련 이슈: #3. 이번 범위는 **Eureka + Auth/User/AI/Blog의 독립 실행과 등록**이다. 기존 로그인/프로필/게시글/AI 로직을 새 서비스로 옮긴 상태는 아니다. Config Server·Gateway·Bus·DB·클라우드 배포는 후속 작업이다.

## 구조와 공통 계약

```text
backend/
├── build.gradle                     # 기존 모놀리스 설정 유지
├── settings.gradle                  # 모놀리스 + 플랫폼 + 4개 서비스 등록
├── gradle/msa-common.gradle          # MSA 모듈만 사용하는 공통 빌드 규칙
├── platform/
│   ├── settings.gradle              # Eureka 단독 프로젝트 열기도 유지
│   └── discovery-server/
├── services/
│   ├── auth-service/
│   ├── user-service/
│   ├── ai-service/
│   └── blog-service/
└── scripts/verify-msa.py             # 실제 JAR 기동·등록·종료 검증
```

| Gradle 모듈 | 서비스 이름 | 포트 | IntelliJ 실행 클래스 | 향후 담당 기능 |
|---|---|---:|---|---|
| `:platform:discovery-server` | discovery-server | 8761 | DiscoveryServerApplication | 서비스 등록부 |
| `:services:auth-service` | auth-service | 8081 | AuthServiceApplication | Google 로그인·JWT |
| `:services:user-service` | user-service | 8082 | UserServiceApplication | Profile·Bookmark·Notification |
| `:services:ai-service` | ai-service | 8083 | AiServiceApplication | AI 읽기 가이드·캐시 |
| `:services:blog-service` | blog-service | 8084 | BlogServiceApplication | 기업·태그·게시글·수집 |

기존 루트 `BackendApplication`은 8000 포트의 모놀리스다. 이슈 #3의 다섯 프로세스를 실행할 때는 선택하지 않는다. 기존 루트 `src`와 `build.gradle`의 비즈니스 코드·DB·보안 설정은 변경하지 않았다.

Java 17 / Spring Boot 3.4.5 / Spring Cloud 2024.0.2를 사용한다. 각 실행 모듈은 공통 스크립트로 toolchain, Cloud BOM, Actuator, 테스트, Spotless, JAR 이름을 설정한다. 모놀리스에는 이 스크립트를 적용하지 않는다. 여러 서비스의 실행 클래스를 하나의 공통 컴포넌트 스캔으로 묶지 않는다.

## IntelliJ에서 터미널 없이 실행

1. 기존 실행 프로세스를 정지한다.
2. **File → Open**에서 `backend` 폴더를 연다. 이번에는 루트 `settings.gradle`에 모든 모듈이 포함되어 있으므로 `platform`만 열 필요가 없다.
3. 프로젝트를 신뢰하고 Gradle 프로젝트로 가져온다. 이미 열어뒀다면 오른쪽 Gradle 패널에서 **Reload All Gradle Projects**를 누른다.
4. **Project Structure → Project SDK**와 **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM**에서 설치된 JDK 17을 선택한다.
5. Gradle 동기화가 끝나면 DiscoveryServerApplication의 main 옆 ▶로 실행한다.
6. AuthServiceApplication, UserServiceApplication, AiServiceApplication, BlogServiceApplication을 차례로 실행한다. 기존 프로세스를 중지할지 물으면 서비스를 각각 별도 실행 구성으로 만들었는지 확인한다.
7. 브라우저에서 `http://localhost:8761`을 연다. 최초 등록은 수십 초 걸릴 수 있다. 아래 네 서비스가 **UP**으로 보이는지 확인한다.

```text
AUTH-SERVICE
USER-SERVICE
AI-SERVICE
BLOG-SERVICE
```

등록이 안 보이면 각 서비스 실행 로그와 Eureka URL을 확인한다. health가 UP이어도 Eureka 연결 실패 상태일 수 있으므로 대시보드까지 확인해야 한다.

각 서비스의 브라우저 확인 주소:

- `http://localhost:8081/actuator/health`
- `http://localhost:8082/actuator/health`
- `http://localhost:8083/actuator/health`
- `http://localhost:8084/actuator/health`

모두 `status: UP`이면 기본 기동 성공이다. `/actuator/health/liveness`와 `/actuator/health/readiness`도 제공한다. 현재 `GET /`나 `/api/...`는 구현하지 않았으므로 **404가 정상**이다. Health UP을 로그인·AI 기능 구현 완료로 해석하지 않는다.

매번 다섯 번 실행하는 것이 번거로우면 다섯 개의 실행 구성을 만든 후 **Run → Edit Configurations → + → Compound**에서 추가할 수 있다. Compound는 동시에 시작하므로 Eureka가 먼저 준비되지 않아 초기 연결 재시도 로그가 나올 수 있다. Eureka를 먼저 실행하고 나머지 네 개를 Compound로 실행하면 확인이 쉽다.

## 빌드 및 자동 검증

명령은 `backend` 루트에서 실행한다. macOS 예시이며 다른 OS는 JDK 17 설치 경로로 JAVA_HOME을 지정한다.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"

# 기존 모놀리스를 포함한 전체 테스트/포맷/JAR 빌드
bash ./gradlew test spotlessCheck bootJar

# 모놀리스 빌드 없이 MSA 실행 JAR 5개만 빌드
bash ./gradlew :platform:discovery-server:bootJar \
  :services:auth-service:bootJar :services:user-service:bootJar \
  :services:ai-service:bootJar :services:blog-service:bootJar

# 사용 중인 포트를 피해 빈 포트에서 실제 JAR와 Eureka 등록 자동 검증
python3 scripts/verify-msa.py

# 기본 포트 8761/8081~8084에서도 검증하려면 먼저 IntelliJ 실행을 정지
python3 scripts/verify-msa.py --fixed-ports
```

Python 3 표준 라이브러리만 사용한다. 스크립트는 아래를 검사한 후 자신이 띄운 프로세스만 종료한다.

- 5개 JAR의 실제 실행 및 health/liveness/readiness UP
- Eureka 레지스트리에서 네 서비스가 UP이고 등록 포트가 실행 포트와 일치
- 준비 실패·프로세스 비정상 종료 시 0이 아닌 종료 코드 반환

로그는 실행 시 출력되는 `recu-msa-verify-*` 임시 폴더에 보관한다. 포트가 이미 사용 중이면 기존 프로그램을 강제 종료하지 않고 실패한다. 프로세스 5개를 띄우므로 메모리가 충분한 PC에서 실행한다. 이 자동 검증은 별도의 가짜 API나 mock 서비스로 대체하지 않고 **현재 서비스의 실제 JAR**를 실행한다.

독립 JAR 경로:

```text
platform/discovery-server/build/libs/discovery-server.jar
services/auth-service/build/libs/auth-service.jar
services/user-service/build/libs/user-service.jar
services/ai-service/build/libs/ai-service.jar
services/blog-service/build/libs/blog-service.jar
```

예: `java -jar services/auth-service/build/libs/auth-service.jar`로 실행 가능하다. IntelliJ Gradle 패널에서도 각 모듈의 `bootJar`, `bootRun`, `spotlessCheck` 작업을 실행할 수 있다.

## 환경변수와 비밀값

**현재 골격에는 .env, DB, Google/Gemini/OpenAI 키, AWS 계정이 필요 없다.** YAML의 기본값으로 실행된다. 서비스의 기본 프로필은 `local`이다. 아직 local/dev/prod별 설정 파일은 없고 Config 작업에서 분리한다.

| 변수 | 기본값 | 설정 위치 |
|---|---|---|
| `SERVER_PORT` | 각 서비스의 8081~8084, Eureka는 8761 | IntelliJ 실행 구성 Environment variables 또는 프로세스 환경 |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | 네 도메인 서비스 환경변수 |

예: Eureka 포트를 바꾸면 도메인 서비스에도 변경된 EUREKA_URL을 설정한다. Kubernetes에서는 `http://discovery-server:8761/eureka/`를 사용한다. 컨테이너의 localhost는 해당 컨테이너 자신이다.

현재 새 모듈에는 `.env` 자동 로딩을 추가하지 않았다. 기존 루트 모놀리스만 `.env`를 읽는다. 미래의 비밀값은 `.env.example`의 변수명 또는 YAML placeholder 계약으로 공유하고 실값은 커밋하지 않는다. 모놀리스의 기존 `.env`를 네 모듈에 복사하지 않는다.

## 백엔드 작업 시작 위치

- Auth: `services/auth-service/src/main/java/com/recu/auth`
- User: `services/user-service/src/main/java/com/recu/user`
- AI: `services/ai-service/src/main/java/com/recu/ai`
- Blog: `services/blog-service/src/main/java/com/recu/blog`

각 패키지 하위에 담당 API/서비스/DTO를 구현한다. 현재 JPA·Security·Feign·Kafka·Config Client는 의도적으로 추가하지 않았다. 이후 기능별 의존성과 설정을 담당자와 연결한다. 공통 빌드 스크립트에 DB/Security를 무조건 추가하면 필요하지 않은 모듈까지 기동 조건이 늘어나므로 서비스별 build.gradle에서 필요한 의존성을 추가한다.

- 기존 `app_user`의 인증/프로필 분리 및 userId 발급 주체는 Auth/User 담당자가 먼저 합의한다.
- 서비스별 DB를 사용하고 DB 간 직접 JOIN/FK를 만들지 않는다.
- User 알림은 Blog 게시 이벤트의 tagIds와 관심태그 매칭·멱등 처리를 백엔드가 구현한다.
- 실제 AI 공급자는 기존 Gemini와 OpenAI 전환 여부를 먼저 합의한다.
- JWT 검증을 적용할 때 Probe 경로의 접근 정책을 유지하고 health 상세·env를 외부 노출하지 않는다.
- Config/Gateway/암호화·Bus 및 클라우드 배포 성공은 이번 이슈의 완료 근거가 아니다.

플랫폼 단독 프로젝트(`platform` 열기 및 `bash ./gradlew -p platform :discovery-server:bootRun`)도 계속 지원한다. 다만 공통 스크립트가 저장소 `gradle/msa-common.gradle`에 있으므로 platform 폴더만 복사해 별도 저장소로 옮기는 방식은 지원하지 않는다.

## 구현 검증 기록 (2026-10-31 KST)

- `bash ./gradlew test spotlessCheck bootJar`: 모놀리스 포함 전체 검사 성공
- `bash ./gradlew -p platform :discovery-server:test :discovery-server:bootJar`: 기존 platform 단독 빌드·테스트 성공
- `python3 scripts/verify-msa.py --fixed-ports`: Eureka 8761, Auth 8081, User 8082, AI 8083, Blog 8084에서 health/liveness/readiness UP 확인
- 실제 Eureka 레지스트리에서 네 서비스 UP 및 포트 일치 확인
- 검증용 JVM 5개는 스크립트가 종료·정리함
- 기존 Eureka 테스트 종료 시 `Cannot clean connections / Client instance has been closed` 로그가 다시 관찰됐으나 테스트 실패는 없었음. 로그를 숨기거나 버전을 임의 변경하지 않았으며 후속 확인 항목으로 유지
- 도메인 비즈니스 기능·DB·Config·Gateway·클라우드 배포는 미구현/미검증이며 이슈 #3 범위 밖
