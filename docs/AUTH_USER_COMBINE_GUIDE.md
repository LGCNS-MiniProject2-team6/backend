# Auth·User 통합 실행·인계 안내 (#17)

## 확정 구조

| 서비스 | 기능 개발 범위 | 이름·기본 포트 |
|---|---|---|
| User | Google 로그인·JWT·회원/프로필·북마크·알림 | user-service / 8082 |
| AI | AI 가이드 | ai-service / 8083 |
| Blog | 게시글·기업·기술 태그·수집 | blog-service / 8084 |

플랫폼은 Eureka 8761, Config 8888, Gateway 8080이다. 인증 URL은 /api/auth 및 /api/auth/**를 유지하며 Gateway 목적지가 User로 바뀐다. User URL과 토큰 헤더·CORS 계약은 유지한다. 인증과 회원을 같은 JVM 안에서 처리하며 인증 때문에 별도 Eureka 인스턴스나 프로세스를 띄우지 않는다.

Auth 모듈과 User 모듈에는 main 클래스·설정·의존성만 있었다. 독립 Auth의 세 파일과 Gradle 등록을 제거했고 옮길 도메인 업무 구현은 없었다. 기존 루트 모놀리스 src의 인증/회원·DB 엔티티·보안 코드는 수정하지 않았다. User에 컨트롤러가 아직 없으므로 /api/auth/**가 실제 앱에서 404를 반환할 수 있다. 로그인 기능 완료를 의미하지 않는다.

## IntelliJ에서 지금 할 일

1. backend를 열고 오른쪽 Gradle 패널의 Reload All Gradle Projects를 실행한다. JDK와 Gradle JVM은 17이다.
2. 실행 중인 기존 AuthServiceApplication을 중지하고 Run → Edit Configurations에서 해당 실행 구성을 삭제한다. AI·Blog·User 실행 구성은 유지한다.
3. 새 recu-config 브랜치의 YAML은 원격 main에 자동 적용되지 않는다. 기존 백엔드와 새 설정을 섞지 않도록 두 저장소 PR의 반영 시점을 맞춘다.
4. Config가 읽는 원격 main에 통합 설정을 반영한 뒤 Eureka → Config → User/AI/Blog → Gateway 순서로 실행한다. 로컬 후보만 확인하려면 아래 자동 검증을 먼저 쓴다.
5. User의 환경변수는 기존 CONFIG_USER, CONFIG_PASSWORD, CONFIG_SERVER_URL, CONFIG_GIT_LABEL, SPRING_PROFILES_ACTIVE, EUREKA_URL을 그대로 쓴다. 별도 Auth 포트 환경변수를 복사하지 않는다. SERVER_PORT를 지정했다면 8082인지 확인한다.
6. Config·User·AI·Blog·Gateway의 Kafka 연결 변수를 일치시키고 Bus를 검증할 때만 BUS_ENABLED=true로 설정한다. Kafka가 없으면 기본 false다. Git 토큰·Bus 관리 계정은 Config에만 넣는다.
7. Eureka에서 USER-SERVICE/AI-SERVICE/BLOG-SERVICE와 CONFIG-SERVER/API-GATEWAY가 UP인지 확인한다. AUTH-SERVICE가 남으면 기존 실행 프로세스가 켜져 있는지 보고, 중지 후 lease 정리를 기다린다.
8. http://localhost:8082/actuator/info 에서 config.service=user-service를 확인한다. Bus를 켰다면 refresh 항목도 나타난다.

UserServiceApplication의 컴포넌트 스캔 루트는 com.recu.user다. 인증 기능은 com.recu.user.auth 아래에서 구현한다. 기존 com.recu.auth 최상위 패키지를 그대로 만들면 기본 스캔 범위 밖이다. Auth용 별도 main 클래스를 다시 만들지 않는다.

## 자동 검증 명령

backend 루트의 IntelliJ Terminal에서 실행한다. 실행 중인 개인 앱은 정지시키지 않으며, 스크립트가 빈 포트의 임시 앱과 Git을 사용한다. 빌드 도중 검증 JAR를 덮어쓰지 않도록 빌드를 먼저 끝낸다.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
bash ./gradlew test spotlessCheck bootJar :platform:config-server:writeBusTestClasspath --no-daemon
# Config/Kafka 없는 독립 실행 및 Auth 미등록
python3 scripts/verify-msa.py
# 모든 local/dev/prod의 중앙 설정 및 세 서비스 기동
python3 scripts/verify-config.py
# 실제 recu-config 후보 YAML로 User 인증/회원 라우팅·CORS 검증
python3 scripts/verify-gateway.py --config-repo ../recu-config --profile dev
# 실제 테스트 Kafka + 관리자 한 번 호출 + 네 클라이언트 무재시작 갱신
python3 scripts/verify-config.py --verify-bus
```

Gateway 검증은 User·AI·Blog 세 개의 테스트 downstream과 실제 Gateway·Config·Eureka를 사용한다. /api/auth 루트와 Google·refresh 경로 및 기존 User 경로가 같은 User로 전달되는지 확인한다. 실제 업무 컨트롤러·JWT 발급 검증은 아니다. Bus 검증은 실제 도메인 골격 JAR를 띄우고 demo.config-version의 v1→v2 변경과 동일 PID/startTime을 확인한다. 암호화 키 없이 실행된다.

로컬 설정 후보를 Git 커밋 단위로 검증할 수도 있다. CANDIDATE_SHA는 방금 recu-config에 만든 커밋의 실제 SHA로 바꾼다. slash가 포함된 브랜치명 대신 SHA를 쓴다.

```bash
python3 scripts/verify-config.py \
  --git-uri file:///Users/hy/Desktop/miniproject2/recu-config \
  --git-label CANDIDATE_SHA --expected-commit CANDIDATE_SHA --profile dev
```

이 명령은 개발자 설정 저장소의 checkout이 바뀌지 않도록 복제본을 읽는다. GitHub 토큰 없이 후보를 검증한다. 실제 비밀값과 .env는 변경하지 않는다.

## 설정·배포 인계

- recu-config의 auth-service-local/dev/prod.yml 삭제. 기존 내용은 포트·Eureka·관측 서비스 이름뿐이어서 User 설정에 별도로 옮길 비밀값/기능 설정은 없었다.
- api-gateway.yml의 auth 라우트 ID와 경로는 유지하고 uri만 lb://user-service로 변경. 호환 URL을 유지하므로 프론트 인증 주소를 바꿀 필요가 없다.
- User/AI/Blog의 환경별 파일은 유지한다. 부팅 이름·설정 인증은 각 앱이 알아야 한다.
- 역할 2/4는 이미지 빌드 대상과 Deployment/Service 목록에서 독립 Auth를 제외한다. 현재 저장소에 Auth용 배포/CI manifest는 없으므로 다른 저장소가 있다면 별도로 맞춘다. User 이미지·포트는 기존 계약을 사용한다.
- Bus 참여자는 Config+Gateway+세 서비스의 다섯 앱이다. 동일 환경 토픽과 앱별 고유 Bus ID를 유지한다. 검증 수신자는 세 서비스+Gateway의 네 앱이다.
- Config 읽기/Bus 관리자 인증은 통합과 별개이며 기존 정책을 유지한다. 사용자 JWT 인증 구현은 백엔드 팀 작업이다.
- backend PR #16이 아직 미머지이므로 이번 로컬 브랜치는 선행 Bus 커밋을 포함한다. GitHub에서 강제로 머지하지 않았다. #16을 먼저 검토·반영하고 #17 변경을 리뷰하면 통합 변경만 확인하기 쉽다.
- recu-config 변경도 refactor/17/auth-user-combine 브랜치에서 별도 커밋했다. 두 저장소 모두 이번 작업에서는 푸시하지 않는다.

현재 실제 배포 Kafka·AWS 및 사용자 IntelliJ 직접 검증은 후속 통합 대상이다. 자동 검증 결과와 변경 파일별 요약은 아래에 기록한다.

## 자동 검증 결과 (2026-11-01 KST)

- Java 17: test spotlessCheck bootJar 및 Kafka 테스트 classpath 생성 통과.
- Config/Kafka 없는 독립 진단: 세 서비스 health/liveness/readiness, 정확한 등록 포트 및 Auth 미등록 통과.
- 실제 KRaft Kafka: User·AI·Blog·Gateway 네 클라이언트 v1→v2, PID·시작 시각·Bus ID 유지. 관리자 정상 호출 한 번, 무인증 401·읽기 계정 403 확인.
- 실제 recu-config 후보 YAML의 dev 환경: 인증 루트·하위 경로 포함 15개 Gateway 라우팅, CORS·헤더·쿼리·본문 보존, 관리 경로 404, 빈 라우팅 fail-fast 통과.
- 실제 recu-config 후보 커밋 9e2526c를 복제본에서 조회: 세 서비스 × local/dev/prod의 9개 설정·Git commit 일치, dev 프로필 실제 기동·중앙 설정 수신·Eureka 등록·잘못된 인증 fail-fast 통과.
- backend fixture와 recu-config 후보의 모든 YAML 내용이 동일함을 확인.

## 변경 파일별 한 줄 요약

| 파일 | 한 줄 요약 |
|---|---|
| `config-repository-template/README.md` | 3개 서비스 기준의 설정·실행·인계 안내로 갱신 |
| `config-repository-template/api-gateway.yml` | 인증 URL 목적지를 User로 변경 |
| `config-repository-template/auth-service-dev.yml` | 불필요한 Auth 전용 설정 삭제 |
| `config-repository-template/auth-service-local.yml` | 불필요한 Auth 전용 설정 삭제 |
| `config-repository-template/auth-service-prod.yml` | 불필요한 Auth 전용 설정 삭제 |
| `docs/AUTH_USER_COMBINE_GUIDE.md` | 통합 구조·IntelliJ 실행·검증 결과·배포 인계 정리 |
| `docs/CONFIG_SERVER_GUIDE.md` | 3개 서비스 기준의 설정·실행·인계 안내로 갱신 |
| `docs/GATEWAY_GUIDE.md` | 3개 서비스 기준의 설정·실행·인계 안내로 갱신 |
| `docs/MSA_LOCAL_GUIDE.md` | 3개 서비스 기준의 설정·실행·인계 안내로 갱신 |
| `docs/REMOTE_CONFIG_GUIDE.md` | 3개 서비스 기준의 설정·실행·인계 안내로 갱신 |
| `docs/SPRING_CLOUD_BUS_GUIDE.md` | 3개 서비스 기준의 설정·실행·인계 안내로 갱신 |
| `docs/history/PRE_AUTH_USER_COMBINE_VALIDATION.md` | 통합 이전 실제 검증 기록을 현재 안내와 분리 보관 |
| `platform/discovery-server/README.md` | 3개 서비스 기준의 설정·실행·인계 안내로 갱신 |
| `scripts/verify-config.py` | 9개 중앙 설정 및 네 클라이언트 Bus 갱신으로 검증 기준 변경 |
| `scripts/verify-gateway.py` | 인증·회원 URL의 동일 User 전달과 인증 루트 요청 검증 |
| `scripts/verify-msa.py` | 3개 서비스 독립 실행과 Auth 미등록 검증 |
| `services/auth-service/build.gradle` | 독립 Auth 실행 골격 삭제 |
| `services/auth-service/src/main/java/com/recu/auth/AuthServiceApplication.java` | 독립 Auth 실행 골격 삭제 |
| `services/auth-service/src/main/resources/application.yml` | 독립 Auth 실행 골격 삭제 |
| `services/user-service/README.md` | 인증 포함 User의 기능 범위·패키지·실행 계약 안내 |
| `settings.gradle` | Auth Gradle 모듈 제거, User·AI·Blog 유지 |

별도 recu-config: api-gateway.yml의 인증 라우트는 User로 변경, auth-service-local/dev/prod.yml 삭제, README.md에 통합 계약·반영 순서 안내.
