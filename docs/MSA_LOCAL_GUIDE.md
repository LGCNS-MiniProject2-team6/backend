# MSA 로컬 실행: User(인증 포함)·AI·Blog

#17부터 독립 Auth 실행 구성을 제거한다. 현재 기준 문서는 [Auth·User 통합 안내](AUTH_USER_COMBINE_GUIDE.md)다.

## 모듈·포트 계약

| 역할 | 모듈/앱 이름 | 기본 포트 | 실행 클래스 |
|---|---|---|---|
| 서비스 검색 | platform:discovery-server | 8761 | DiscoveryServerApplication |
| 중앙 설정 | platform:config-server | 8888 | ConfigServerApplication |
| 외부 진입 | platform:api-gateway | 8080 | ApiGatewayApplication |
| 인증·회원·북마크·알림 | services:user-service | 8082 | UserServiceApplication |
| AI | services:ai-service | 8083 | AiServiceApplication |
| 게시글 | services:blog-service | 8084 | BlogServiceApplication |

프로젝트 이름은 user-service로 유지한다. 이름을 임의 변경하면 Config 파일명과 Eureka·Gateway 목적지가 불일치한다. 기존 모놀리스 루트 BackendApplication(8000)은 별도이며 MSA 실행 목록에 넣지 않는다.

## IntelliJ

1. backend 루트를 열고 Gradle Reload All Projects를 실행한다. Project SDK와 Gradle JVM은 JDK 17을 선택한다.
2. 기존 AuthServiceApplication 실행을 중지하고 해당 Run Configuration을 삭제한다. Gradle에서 제거된 모듈이 보이면 프로젝트를 다시 동기화한다.
3. 정상 통합 실행은 Eureka → Config → User/AI/Blog → Gateway 순서다. Compound에는 세 도메인 앱을 넣을 수 있다.
4. Config에는 기존 Git 인증·설정 읽기 계정을 주입한다. User/AI/Blog/Gateway에는 기존 CONFIG_USER, CONFIG_PASSWORD, CONFIG_SERVER_URL, CONFIG_GIT_LABEL, SPRING_PROFILES_ACTIVE, EUREKA_URL을 사용한다. Git 토큰과 Bus 관리 계정은 Config에만 넣는다.
5. Kafka가 없으면 BUS_ENABLED=false를 유지한다. Bus를 켤 때는 Config·Gateway·세 서비스의 주소와 토픽을 맞춘다.
6. Eureka에서 USER-SERVICE, AI-SERVICE, BLOG-SERVICE 및 CONFIG-SERVER, API-GATEWAY를 확인한다. 독립 AUTH-SERVICE는 없어야 한다. 기존 로컬 Auth 프로세스를 켜 두면 예전 등록이 남을 수 있다.
7. http://localhost:8082/actuator/info 에서 config.service=user-service를 확인한다. AI는 8083, Blog는 8084다.

[Config 실행](CONFIG_SERVER_GUIDE.md), [Gateway/CORS](GATEWAY_GUIDE.md), [Kafka Bus](SPRING_CLOUD_BUS_GUIDE.md)를 참고한다. .env 파일은 자동 로딩되지 않는다.

## Config/Kafka를 끈 독립 진단

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
bash ./gradlew test spotlessCheck bootJar --no-daemon
python3 scripts/verify-msa.py
# 기본 포트를 쓰려면 해당 포트의 직접 실행 앱을 먼저 중지
python3 scripts/verify-msa.py --fixed-ports
```

이 스크립트는 standalone 프로필로 Eureka + User/AI/Blog의 네 JAR만 띄운다. health/liveness/readiness와 등록 포트, 독립 Auth 미등록을 확인한다. 빈 포트가 기본이며 기존 앱은 정지하지 않는다.

## 백엔드 개발 위치

인증 코드는 com.recu.user.auth 아래에 배치한다. UserServiceApplication의 기본 스캔 범위는 com.recu.user다. 현재 모듈에는 업무 로직이 없으며, 모놀리스 로그인·JWT·DB 엔티티를 이번 작업에서 이관하지 않았다. 신규 기능 구현은 백엔드 팀 범위다.
