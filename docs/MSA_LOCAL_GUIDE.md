# MSA 로컬 실행·검증

[문서 목차](README.md) · [Config 설정](CONFIG_SERVER_GUIDE.md)

## 서비스 계약

| 기능 | Gradle 모듈 | 앱 이름 | 기본 포트 | 실행 클래스 |
|---|---|---|---:|---|
| 검색·등록 | :platform:discovery-server | discovery-server | 8761 | DiscoveryServerApplication |
| 중앙 설정 | :platform:config-server | config-server | 8888 | ConfigServerApplication |
| 외부 API 진입 | :platform:api-gateway | api-gateway | 8080 | ApiGatewayApplication |
| 인증·회원·북마크·알림 | :services:user-service | user-service | 8082 | UserServiceApplication |
| AI 가이드 | :services:ai-service | ai-service | 8083 | AiServiceApplication |
| 게시글·기업·기술 태그·수집 | :services:blog-service | blog-service | 8084 | BlogServiceApplication |

독립 Auth 모듈과 8081은 사용하지 않는다. 기존 /api/auth 및 하위 경로는 User로 전달한다. 루트 BackendApplication(8000)은 별도 모놀리스다. 현재 세 업무 모듈에는 main 클래스·설정만 있으므로 업무 URL의 404만으로 플랫폼 실패를 판단하지 않는다. 로그인·JWT·실제 업무 API 개발 상태는 백엔드 팀과 확인한다.

## IntelliJ 실행

처음 한 번(backend 루트, Windows는 Git Bash):

```bash
git clone https://github.com/LGCNS-MiniProject2-team6/recu-config.git ../recu-config
bash scripts/local-setup.sh
```

- 첫 줄은 Config Server가 읽을 설정 저장소를 backend 옆에 받는다. 옆에 있으면 GitHub 토큰 없이 로컬 파일로 읽는다.
- 둘째 줄은 로컬 전용 비밀값을 무작위로 만들어 .env.local* 파일(커밋 제외)에 저장하고, 팀 MariaDB 이미지와 Kafka를 Docker로 띄운다. 3306을 이미 쓰고 있으면 DB_PORT=3307을 앞에 붙인다.
- 가이드 생성을 테스트하려면 .env.local.ai의 GEMINI_API_KEY를 본인 키로 바꾼다.
- 옆 폴더 recu-config를 읽을 때 Config 로그의 `Could not fetch remote ... recu-config.git` WARN은 무시한다. 원격 fetch만 건너뛰고 로컬 파일로 동작한다. 최신 설정은 그 폴더에서 git pull 한다.

실행:

1. backend 루트를 열고 Gradle의 Reload All Gradle Projects를 실행한다. Project SDK와 Gradle JVM은 JDK 17을 선택한다.
2. Run 목록의 `1. Platform (Eureka + Config)`를 실행하고 Started ConfigServerApplication을 확인한다. 실행 구성은 레포의 .run/에 있고 .env.local* 파일을 자동으로 읽는다. 환경변수를 직접 넣지 않는다.
3. `2. Services (Gateway + User + AI + Blog)`를 실행한다. Config보다 먼저 시작하면 필수 설정 조회가 실패하므로 2번이 끝난 뒤 실행한다.
4. 직접 만든 기존 Run Configuration(AuthServiceApplication, 환경변수 없는 UserServiceApplication 등)은 삭제한다.
5. http://localhost:8761 에서 CONFIG-SERVER/API-GATEWAY/USER-SERVICE/AI-SERVICE/BLOG-SERVICE가 UP인지 확인한다. 기동 직후 1~2분은 Gateway 경유 호출이 503일 수 있다(Eureka 목록 갱신 전).
6. 개발이 끝나면 `bash scripts/local-setup.sh down`, DB를 초기 데이터로 되돌리려면 `reset` 후 다시 실행한다.
7. 변수 의미는 [Config 안내](CONFIG_SERVER_GUIDE.md), Kafka·Bus는 [Bus 안내](SPRING_CLOUD_BUS_GUIDE.md)를 본다.
8. 각 앱의 /actuator/health, /actuator/health/liveness, /actuator/health/readiness와 /actuator/info를 확인한다. User의 config.service는 user-service다. 실제 비밀값을 info에 추가하지 않는다.

인증 코드의 권장 위치는 com.recu.user.auth다. UserServiceApplication의 기본 스캔 루트는 com.recu.user이며 별도 com.recu.auth 최상위 패키지는 자동 포함되지 않는다.

## 자동 검증

IntelliJ 하단 Terminal에서 실행한다. 빌드를 먼저 완료한 다음 검증한다. 실행 중인 검증 JAR를 다시 빌드해 덮어쓰지 않는다.

```bash
cd /Users/hy/Desktop/miniproject2/backend
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
bash ./gradlew test spotlessCheck bootJar :platform:config-server:writeBusTestClasspath --no-daemon
```

| 명령 | 검사 범위 |
|---|---|
| python3 scripts/verify-msa.py | Config/Kafka 없는 standalone: Eureka + 세 업무 앱, probe·등록 포트·Auth 미등록 |
| python3 scripts/verify-config.py | 실제 JAR 5개, 임시 Git, 3개 서비스 × local/dev/prod 설정 9개·인증·fail-fast |
| python3 scripts/verify-gateway.py | 실제 플랫폼 JAR 3개 + 테스트 downstream 3개, 라우팅 15개·CORS·헤더·관리 경로 차단 |
| python3 scripts/verify-config.py --verify-encryption | JAR 6개, 암호문 저장·세 서비스 복호화 수신·권한·필수 키 누락 거부 |
| python3 scripts/verify-config.py --verify-bus | JAR 6개 + 실제 테스트 Kafka, 네 클라이언트 v1→v2 및 PID/startTime 유지 |
| python3 scripts/verify-config.py --verify-encryption --verify-bus --profile dev | dev 프로필에서 암호문 변경·Bus 한 번 호출·새 복호화 값 수신 |

각 명령은 빈 포트와 임시 Git/계정을 사용하며 본인이 띄운 프로세스만 정리한다. 자동 검증이 Config 전용 임시 키를 생성하므로 실제 키·GitHub 토큰·Docker·AWS가 필요 없다. Gateway 테스트의 업무 서버는 stub이므로 로그인 기능 검증이 아니다. Bus 테스트의 Kafka는 실제 KRaft 브로커지만 배포용 Kafka는 아니다.

명령 종료 직후 echo $?가 0이고 해당 PASS가 모두 있는지 확인한다. 마지막 정리 메시지는 실패 때도 출력되므로 그것만 보지 않는다. 시작 시 표시되는 Logs 경로에서 오류를 확인하되 공유 전 실제 비밀값을 점검한다.

기본 포트로 독립 진단하려면 먼저 해당 포트의 개인 실행 앱을 중지하고 python3 scripts/verify-msa.py --fixed-ports를 사용한다. 기본 검증은 빈 포트이므로 기존 8761 브라우저 화면과 별개다. 더 구체적인 원격 Git·Postman 검증은 각 기능 문서를 따른다.
