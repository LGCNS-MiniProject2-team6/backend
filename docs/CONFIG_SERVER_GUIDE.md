# Config Server 실행·설정 저장소 인계

**이슈 #13 이후:** Config Server에는 ENCRYPT_KEY가 필수다. 읽기 인증은 유지하고, 관리 API/Secret 주입은 [암호화 안내](CONFIG_ENCRYPTION_GUIDE.md)를 참고한다.

Gateway를 포함한 후속 실행·라우팅·CORS는 [Gateway 안내](GATEWAY_GUIDE.md)를 참고한다.

**원격 저장소 연결:** 별도 Private 저장소가 생성됐다. 현재 연결·검증 절차는 [원격 Config 안내](REMOTE_CONFIG_GUIDE.md)를 우선 참고한다. 아래 로컬 Git 절차는 계정 없이 검증하는 대안으로 유지한다.

관련 이슈: #7. Eureka → Config Server → Auth/User/AI/Blog 순서로 실행한다. Config Server는 8888, Eureka는 8761, 도메인 서비스는 기존 8081~8084를 사용한다. 기존 모놀리스의 `.env`와 실행 설정은 변경하지 않았다.

## 이번에 구현한 것

- platform/config-server: Git 기반 Config Server, Eureka Client, HTTP Basic 인증, Health Probe
- 네 서비스: Config Client와 필수 Config import 적용(서버 장애·잘못된 인증 시 정상 기동을 허용하지 않음)
- config-repository-template: 별도 recu-config 저장소의 공통·서비스별 local/dev/prod YAML 초안
- scripts/verify-config.py: 임시 로컬 Git 및 실제 JAR 6개로 인증/설정/등록 검증

Gateway·암호화·Bus·DB·AI 공급자 전환은 후속 이슈다. 아직 변경값 자동 전파 기능은 없으므로 Git 설정 변경 후 서비스 재시작이 필요하다.

## 1. 별도 Config 저장소 준비

현재 backend 저장소 안에 중첩 `.git`을 만들지 않는다. IntelliJ에서 프로젝트를 정상적으로 열려면 backend 전체를 유지한다.

먼저 GitHub의 별도 비공개 `recu-config` 저장소가 있으면 clone한다. 없으면 아래 로컬 Git 저장소로 먼저 검증할 수 있다. 명령은 **backend 루트**에서 시작한다. 예시 대상 경로가 이미 존재하면 새로 만들거나 초기화하지 말고 내용을 먼저 확인한다.

```bash
# backend 밖의 형제 디렉터리. 없을 때만 만든다.
mkdir ../recu-config
cp config-repository-template/*.yml ../recu-config/
git -C ../recu-config init -b main
git -C ../recu-config add '*.yml'
git -C ../recu-config commit -m 'chore(config): 서비스별 초기 설정 추가'
```

Config Server의 `CONFIG_GIT_URI`는 이 로컬 Git 경로의 file URI다.

```text
file:///Users/hy/Desktop/miniproject2/recu-config
```

다른 위치를 쓰면 실제 절대경로로 바꾼다. 변경 YAML은 commit한 뒤 조회한다. 원격 저장소 사용 시 실제 Git URL로 바꾸고 commit/push해야 반영된다. main 브랜치를 사용하고 master로 자동 fallback하지 않는다.

GitHub 저장소 생성/원격 push는 이번 로컬 구현 과정에서 수행하지 않았다. 이슈 #7 구현 당시에는 조회할 수 없었지만 이슈 #9에서 Private recu-config 저장소와 Git 읽기 접근을 확인했다. 실제 설정 업로드/원격 main 검증 상태는 원격 Config 안내에서 관리한다. 템플릿 파일을 복사한 로컬 Git으로도 동일한 Git backend 경로를 검증할 수 있다.

## 2. 환경변수 입력

첨부 환경변수 파일의 DB/Google/Gemini 값은 Config Server 인증과 별개다. DB_DRIVER는 기존 모놀리스 YAML에 driver가 고정되어 있어 현재 코드에서 참조하지 않는다. DB 비밀번호/JWT/Gemini의 xxxx는 미발급 값이며 앱 비밀값으로 사용하지 않았다.

새 변수는 `platform/config-server/.env.example`에 변수명과 placeholder만 기록했다. **앱은 이 파일이나 `.env`를 자동으로 읽지 않는다.** IntelliJ에서 각 Run Configuration의 Environment variables에 입력한다. 실제 비밀번호/토큰을 커밋하거나 스크린샷으로 공유하지 않는다.

| 변수 | Config Server | 네 도메인 서비스 |
|---|---|---|
| CONFIG_USER | 예: recu-config | 서버와 동일 |
| CONFIG_PASSWORD | 새로 정한 충분히 긴 비밀번호 | 서버와 동일 |
| CONFIG_GIT_URI | 실제 HTTPS Git URL 또는 위 file URI | 불필요 |
| CONFIG_GIT_LABEL | main(기본값) | main(기본값) |
| CONFIG_GIT_USERNAME | private HTTPS Git 인증 시만 입력 | 불필요 |
| CONFIG_GIT_TOKEN | private 저장소 읽기 토큰, local/public이면 빈값 | 불필요 |
| CONFIG_SERVER_URL | 불필요(자기 자신을 import하지 않음) | http://localhost:8888(기본값) |
| EUREKA_URL | http://localhost:8761/eureka/(기본값) | 같은 값 |
| SPRING_PROFILES_ACTIVE | 필요 시 local/dev/prod | local(기본값), dev/prod는 명시 |

채팅/예시에서 새로 발급할 값은 다음처럼 xxxx로 표시한다. **xxxx는 실제 운영값이 아니므로 실행 전에 교체한다.** CONFIG_PASSWORD는 외부 업체에서 발급받는 API 키가 아니라 팀이 직접 정하는 Config 전용 비밀번호다. CONFIG_GIT_TOKEN만 저장소 접근 정책에 따라 발급받는다.

```dotenv
CONFIG_USER=recu-config
CONFIG_PASSWORD=xxxx
CONFIG_GIT_URI=xxxx
CONFIG_GIT_LABEL=main
CONFIG_GIT_USERNAME=
CONFIG_GIT_TOKEN=
```

private Git 사용 시 CONFIG_GIT_USERNAME/CONFIG_GIT_TOKEN에 실제 값을 입력한다. 실토큰을 Git URL에 포함하지 않는다. Config 인증값은 앱과 서버 간, Git 토큰은 서버와 Git 간으로 서로 다른 자격 증명이다.

## 3. IntelliJ 실행

1. backend 루트를 열고 Gradle Reload. Gradle JVM/Project SDK는 JDK 17.
2. DiscoveryServerApplication 실행.
3. ConfigServerApplication 실행 구성에 위 서버 환경변수 입력 후 실행.
4. 네 Application의 실행 구성에 동일한 CONFIG_USER/CONFIG_PASSWORD 입력 후 실행.
5. Eureka 대시보드에서 CONFIG-SERVER 및 기존 네 서비스가 UP인지 확인.
6. 각 서비스 `/actuator/health`와 `/actuator/info` 확인.

Auth 예시:

```text
http://localhost:8081/actuator/health
http://localhost:8081/actuator/info
```

info에 다음 값이 보이면 중앙 Git 설정을 실제로 받아왔다는 근거다. 코드에 이 응답을 하드코딩한 Controller는 없다. 원격 YAML이 Actuator info contributor와 공개 확인값을 설정한다.

```json
{"config":{"source":"recu-config","version":"v1","profile":"local","service":"auth-service"}}
```

User/AI/Blog도 service 필드가 각각 일치해야 한다. **info에 DB 비밀번호/API 키를 추가하지 않는다.** 아직 도메인 Security는 없으며 info는 이 비밀 아닌 설정 확인값만 노출한다.

Config Server는 `/actuator/health/**`만 무인증으로 허용하고 설정 조회는 인증을 요구한다. 그 밖의 POST/변경 작업은 이번 범위에서 허용하지 않는다. 암호화·Bus 구현 시 해당 관리 API의 별도 권한/CSRF 정책을 설계한다.

## 4. Config 설정 조회 및 인증 검사

HTTP Basic 비밀번호를 브라우저나 curl prompt로 입력한다. 아래 명령은 비밀번호를 shell history에 직접 적지 않는다.

```bash
# 기대: 401
curl -i http://localhost:8888/auth-service/local/main

# 비밀번호 prompt 입력 후 서비스/프로필/commit만 출력한다.
curl -fsS -u recu-config http://localhost:8888/auth-service/local/main \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["name"],d["profiles"],d["version"])'
```

`/auth-service/dev/main`, `/user-service/prod/main` 등의 형식으로 조회한다. profile이나 서비스명이 잘못돼도 공통 application.yml만 반환되는 경우가 있으므로 파일 존재와 `info.config.profile/service`도 확인한다.

Config 응답에는 앞으로 복호화된 비밀값이 들어갈 수 있으므로 전체 응답을 로그/채팅/PR에 붙이지 않는다. Config Server는 클라우드에서 ClusterIP 등 내부 접근으로 제한하고 외부 Gateway 경로에 연결하지 않는다. HTTP Basic은 전송 암호화가 아니며 클라우드에서는 TLS 또는 팀 내부 통신 보호 정책을 적용한다.

## 5. 빌드·자동 검증

backend 루트에서:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
bash ./gradlew test spotlessCheck bootJar
python3 scripts/verify-config.py
```

실제 자격 증명을 사용하지 않고 매번 임시 Config 인증값과 Git 저장소를 만든다. 사용하는 포트도 자동 선택한다. 검증 항목:

- Config 설정 조회의 무인증/잘못된 인증은 401
- 4개 서비스 x local/dev/prod 12개 조합의 설정·Git commit 일치
- 네 서비스가 임시 Git에만 존재하는 고유 version 값을 /actuator/info에 반영
- 6개 앱 Health/liveness/readiness UP
- Config + 네 서비스 Eureka UP 및 등록 포트 일치
- 잘못된 Config 비밀번호로 실행한 서비스는 기동 실패
- 검증 프로세스 종료 및 임시 Git 정리(로그 경로는 화면에 출력)

Python 3 표준 라이브러리와 Git, Java 17만 필요하다. JVM 6개와 실패 검증 프로세스가 순차적으로 추가되므로 메모리 여유를 확보한다. 검증 스크립트는 운영 실행 도구가 아니다.

기존 `scripts/verify-msa.py`는 Config를 명시적으로 끈 **Eureka 단독 골격 진단용**으로 유지했다. 기본 앱 실행은 Config가 필수이므로 일상 통합 검증은 `verify-config.py`를 사용한다. Config 없이 개별 서비스만 진단하려면 실행 환경에 `SPRING_PROFILES_ACTIVE=standalone`을 명시하되 운영 환경에서 우회 기본값으로 사용하지 않는다.

## 장애 확인

- Config 401: 서버·클라이언트 CONFIG_USER/CONFIG_PASSWORD 일치 확인. Git 토큰과 혼동하지 않음.
- Git clone 실패: URI/branch/읽기 토큰/네트워크, local Git commit 존재 확인.
- 클라이언트 기동 실패: Config 서버를 먼저 띄웠는지 확인. 이번 기본 import는 optional이 아니므로 정상 동작.
- Config는 UP인데 서비스별 설정 누락: application.name과 `{service}-{profile}.yml` 파일명, active profile, label 확인.
- dev/prod Eureka 접속 실패: 로컬 PC에서 dev를 검증할 때는 EUREKA_URL을 localhost로 덮어쓴다. 기본값은 클러스터 내부 DNS다.
- 8888 중복: 기존 실행을 중지하거나 SERVER_PORT를 바꾸고 네 서비스 CONFIG_SERVER_URL도 같이 변경.

## 구현 검증 기록 (2026-10-31 KST)

- 전체 `test spotlessCheck bootJar` 성공(변경 없는 기존 테스트는 Gradle 캐시 사용)
- 임시 Git 저장소로 12개 서비스/프로필 조합 및 응답 Git commit 검증 성공
- 무인증·잘못된 인증의 설정 요청 401 확인
- 네 서비스의 고유 중앙 version 값 수신 및 Health/Probe UP 확인
- Config Server + 네 서비스의 Eureka UP 및 등록 포트 일치 확인
- 잘못된 Config 인증을 받은 클라이언트의 fail-fast 종료 확인
- 실제 첨부 비밀값은 테스트에 사용하거나 신규 소스/YAML에 복사하지 않음
- 별도 원격 Config 저장소 생성/업로드 및 GitHub HTTPS 인증은 미수행
- 최종 프로필 분리 후 `verify-config.py` 재검증 성공, `verify-msa.py`의 standalone 진단도 성공
- platform 단독 프로젝트에서 Config Server 포맷 검사·독립 JAR 빌드 성공
