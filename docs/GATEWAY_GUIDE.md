# API Gateway 실행 및 검증

현재 #17 기준: **User(인증 포함)·AI·Blog** 3개 서비스다. [통합 실행·검증 안내](AUTH_USER_COMBINE_GUIDE.md)를 먼저 참고한다. 이전 검증 기록은 [이력](history/PRE_AUTH_USER_COMBINE_VALIDATION.md)에 보관한다.

후속 #15의 Kafka Bus 실행·무재시작 갱신 검증은 [Spring Cloud Bus 안내](SPRING_CLOUD_BUS_GUIDE.md)를 참고한다. Kafka가 없으면 BUS_ENABLED=false 기본값을 사용한다.

관련 이슈: #11. Gateway는 **프론트의 단일 API 진입점**이며, URL 경로를 보고 담당 서비스로 요청을 전달한다.

```text
프론트 → Gateway:8080 → Eureka에서 찾은 User/AI/Blog
                         ↑
                라우팅/CORS는 Config Server에서 수신
```

## 변경 파일

| 파일 | 한 줄 요약 |
|---|---|
| platform/api-gateway/build.gradle | Reactive Gateway·Eureka·LoadBalancer·Config Client 의존성 |
| ApiGatewayApplication.java | Gateway 실행 및 중앙 routes 누락 시 기동 실패 |
| platform/api-gateway/src/main/resources/application.yml | 포트·Config 인증·Eureka·Probe 부팅 설정 |
| platform/api-gateway/.env.example | Gateway에 필요한 환경변수 이름 안내 |
| settings.gradle, platform/settings.gradle | 루트/플랫폼 단독 빌드에 모듈 등록 |
| 별도 recu-config/api-gateway*.yml | 실제 운영할 라우팅과 환경별 Origin |
| config-repository-template/api-gateway*.yml | 같은 초기 설정의 테스트 fixture |
| scripts/verify-gateway.py | 실제 플랫폼과 임시 downstream으로 라우팅·CORS 검증 |

기존 모놀리스와 세 서비스 비즈니스 코드는 변경하지 않는다. Gateway에 spring-boot-starter-web, JPA, Servlet Security를 복사하지 않는다. 실제 JWT 검증은 백엔드 공통 담당과 별도 구현하며 이번 Gateway는 Authorization을 보존해서 전달한다. CORS는 브라우저 정책이며 curl/서버 요청을 인증하는 보안 기능이 아니다.

## 요청 경로 계약

| 요청 | 대상 |
|---|---|
| /api/auth 및 하위 | user-service:8082 |
| /api/profile 및 하위 | user-service:8082 |
| /api/bookmarks 및 하위 | user-service:8082 |
| /api/notifications 및 하위 | user-service:8082 |
| /api/posts/*/guide | ai-service:8083 |
| /api/posts 및 나머지 하위 | blog-service:8084 |
| /api/companies 및 하위 | blog-service:8084 |
| /api/tech-tags 및 하위 | blog-service:8084 |
| /api/admin/collect 및 하위 | blog-service:8084 |

AI order=-10, Blog order=0으로 겹치는 AI 가이드 경로를 먼저 매칭한다. StripPrefix를 사용하지 않으므로 /api 접두사·쿼리·메서드·본문을 그대로 전달한다. lb://서비스명은 Eureka 등록 주소를 조회한다. Kubernetes DNS로 고정 대상 하나만 호출하는 방식이 아니다.

요청 Authorization/Refresh-Token은 전달하고, 클라이언트가 위조한 X-User-Id는 제거한다. 이 헤더 제거를 JWT 검증 구현으로 간주하지 않는다. 백엔드에서 JWT를 검증하고 userId를 추출하는 계약이 필요하다. 기존 관리자 수집 API의 권한도 백엔드 담당이 확인해야 한다.

자동 Discovery 라우트는 비활성화한다. Config 조회, Eureka 관리, 암복호화, Bus, 내부 관측 경로를 외부 API로 라우팅하지 않는다. Gateway 자체의 health/info는 제공하지만 env·gateway routes 등의 관리 엔드포인트는 노출하지 않는다. 클라우드에서 health/info 노출 범위와 외부 프록시 경로 제한은 역할 2와 함께 정한다.

## CORS 계약과 포트

현재 작업 폴더에 프론트 코드/vite.config가 없어 실제 프론트 개발 포트는 확정 검증하지 못했다. local의 시작값은 http://localhost:5173이며, 실제 Origin은 FRONTEND_ORIGIN으로 변경한다. dev/prod는 이 변수를 반드시 입력한다. Origin은 프로토콜·호스트·포트까지 일치시킨다. 경로나 마지막 /는 붙이지 않는다.

- credentials 허용 시 Origin을 *로 지정하지 않는다.
- GET/POST/PUT/PATCH/DELETE/OPTIONS 허용
- 요청 Authorization/Content-Type/Refresh-Token/X-Request-Id 허용
- 응답 Authorization/Refresh-Token을 브라우저 JavaScript에서 읽도록 노출
- /api/**에만 CORS 적용, 라우트 미매칭 OPTIONS도 처리
- 기존 백엔드 CORS와 겹칠 때 중복 Allow-Origin/Allow-Credentials는 하나로 정리

Gateway 기본 8080과 프론트가 같은 PC의 8080을 동시에 사용할 수 없다. 프론트가 8080이면 **Gateway에 SERVER_PORT=18080, FRONTEND_ORIGIN=http://localhost:8080**을 지정하고 프론트의 API 주소를 http://localhost:18080으로 맞춘다. 프론트 5173이면 Gateway 8080을 그대로 사용할 수 있다.

일반 upstream 응답 timeout은 10초, AI 가이드 경로는 기존 AI 재시도 시간을 고려해 120초로 둔다. 클라우드 reverse proxy/LB의 제한이 더 짧으면 먼저 끊기므로 역할 2와 함께 실제 timeout 예산을 조정한다. 측정된 성능 보장 수치는 아니다.

## 설정 저장소 반영이 먼저다

이번 작업은 backend와 recu-config의 두 브랜치에 나뉜다.

1. recu-config의 Gateway YAML 변경 검토 → push/PR → main 반영
2. Gateway 실행 또는 기존 설정 저장소 전체를 가진 로컬 Git으로 후보 검증
3. backend 변경 검토 → push/PR → develop 반영

원격 main에 api-gateway.yml이 없으면 공통 설정만 반환될 수 있다. 빈 Gateway가 조용히 기동하지 않도록 routes가 비어 있으면 명확한 오류로 종료한다. 이때 Eureka에 잠깐 보였다 사라질 수 있으므로 Started 로그 및 실제 요청까지 확인한다. 갱신 가능한 설정은 Bus 활성 환경에서 관리자 호출로 반영할 수 있다. 라우팅 변경의 동적 반영은 이번 버전 갱신 검증 범위가 아니므로 재시작으로 반영하고 실요청을 확인한다.

## IntelliJ 실행 순서

1. backend 루트에서 Gradle Reload, JDK 17.
2. 기존 Eureka와 Config Server 실행.
3. 세 도메인 서비스 실행 및 등록 확인.
4. ApiGatewayApplication 실행 구성에 환경변수 입력.
5. Gateway 실행 후 Eureka에 API-GATEWAY가 UP인지 확인.
6. http://localhost:8080/actuator/health 및 /actuator/info 확인.

Gateway 환경변수는 다른 도메인 서비스와 같은 Config 계정·비밀번호를 사용한다. 실제 값은 아래 xxxx만 교체한다. Git 토큰은 Gateway에 넣지 않는다.

```text
CONFIG_USER=xxxx;CONFIG_PASSWORD=xxxx;CONFIG_SERVER_URL=http://localhost:8888;CONFIG_GIT_LABEL=main;SPRING_PROFILES_ACTIVE=local;EUREKA_URL=http://localhost:8761/eureka/;FRONTEND_ORIGIN=http://localhost:5173
```

포트 충돌 시 끝에 `;SERVER_PORT=18080` 추가. 모든 앱의 CONFIG_GIT_LABEL은 같은 후보 commit 또는 main으로 맞춘다. recu-config 작업 브랜치가 아직 원격에 없으면 main을 지정한다고 새 설정이 생기지 않는다. 로컬 Git 방식은 Config Server의 CONFIG_GIT_URI를 file URI로 바꿔 사용하되 JGit이 로컬 branch를 checkout할 수 있으므로 검증용 clone을 따로 쓰는 편이 안전하다.

info 기대값은 source=recu-config, service=api-gateway, profile=local, version=v1이다. Eureka는 Config+Gateway+도메인3개, 총 5개 등록을 기대한다(Discovery 서버 자신은 등록하지 않는다).

**현재 도메인 서비스에 실제 API가 없으므로 Gateway로 /api/...를 호출해 404가 나올 수 있다.** 이 404만 보고 라우팅 성공/실패를 구분할 수 없으므로 아래 자동 검증에서 도착 서비스와 받은 헤더·경로를 확인한다. 실제 기능 E2E는 백엔드 API 구현 후 수행한다.

## 터미널 빌드와 자동 검증

backend 루트:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
bash ./gradlew test spotlessCheck bootJar
python3 scripts/verify-gateway.py
# 별도 Config 저장소의 후보 YAML을 직접 검증
python3 scripts/verify-gateway.py --config-repo ../recu-config
# dev/prod에서도 같은 계약 확인 가능(테스트용 Origin을 명시적으로 주입)
python3 scripts/verify-gateway.py --config-repo ../recu-config --profile prod
```

기본 명령은 backend의 fixture를, --config-repo 옵션은 별도 폴더의 YAML을 임시 Git으로 복사한다. 원본 파일·브랜치를 수정하지 않는다. Java JAR 3개(Eureka/Config/Gateway), Python 표준 라이브러리·Git을 사용하고 포트와 인증값은 임시 생성한다.

도메인 API가 없기 때문에 **downstream은 테스트 전용 HTTP 서버 3개**를 Eureka에 등록한다. Gateway·Config·Eureka 자체는 실제 JAR다. 이 구조로 경로·쿼리·메서드·본문·토큰 전달, AI 우선순위, X-User-Id 제거, CORS 허용/거부, 응답 토큰 노출, 미등록 관리 경로, 중앙 routes 누락 시 기동 실패를 확인한다. 실도메인 비즈니스 동작을 검증했다고 표시하지 않는다.

테스트는 자신이 실행한 JVM·HTTP 서버만 종료하며 기존 IntelliJ 서비스는 건드리지 않는다. 로그는 화면에 출력된 임시 폴더에 보관한다. 로컬 PC에 실제 비밀값은 필요 없다.

## 직접 CORS 확인

Gateway가 8080이고 프론트 Origin이 5173인 경우:

```bash
curl -i -X OPTIONS http://localhost:8080/api/profile \
  -H 'Origin: http://localhost:5173' \
  -H 'Access-Control-Request-Method: GET' \
  -H 'Access-Control-Request-Headers: authorization'
```

200, Allow-Origin=http://localhost:5173, Allow-Credentials=true를 확인한다. 실제 브라우저에서는 프론트 API 호출 후 Network 패널에서 preflight와 응답 헤더를 보고, 로그인 API 구현 후 JavaScript가 Authorization/Refresh-Token 응답 헤더를 읽는지도 확인한다. 자동 HTTP 검증은 브라우저 UI 테스트를 대체한 결과라고 표기하지 않는다.

참고: [Spring Cloud Gateway 4.2 CORS](https://docs.spring.io/spring-cloud-gateway/reference/4.2/spring-cloud-gateway/cors-configuration.html)
