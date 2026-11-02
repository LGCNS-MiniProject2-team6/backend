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

1. backend 루트를 열고 Gradle의 Reload All Gradle Projects를 실행한다. Project SDK와 Gradle JVM은 JDK 17을 선택한다.
2. 기존 AuthServiceApplication 프로세스를 중지하고 Run Configuration/Compound에서 제거한다. UserServiceApplication을 유지한다.
3. [Config 안내](CONFIG_SERVER_GUIDE.md)의 서버·클라이언트 변수를 각각의 Run → Edit Configurations → Environment variables에 넣는다. .env/.env.example은 자동으로 읽지 않는다.
4. Eureka → Config 순서로 실행한다. Config에 ENCRYPT_KEY와 읽기/Git 인증이 있어야 한다. Started ConfigServerApplication과 8888의 health UP을 확인한다.
5. User·AI·Blog와 Gateway를 실행한다. Config와 업무 앱을 동시에 시작하면 필수 설정 조회가 먼저 실패할 수 있다. 업무 앱과 Gateway 사이에는 필수 시작 순서가 없다.
6. Kafka가 없으면 BUS_ENABLED=false를 유지한다. Kafka를 쓸 때는 [Bus 안내](SPRING_CLOUD_BUS_GUIDE.md)를 먼저 따른다.
7. http://localhost:8761 에서 CONFIG-SERVER/API-GATEWAY/USER-SERVICE/AI-SERVICE/BLOG-SERVICE가 UP인지 확인한다. AUTH-SERVICE가 남으면 이전 앱을 중지하고 lease 정리를 기다린다.
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
