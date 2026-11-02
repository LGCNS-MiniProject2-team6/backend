# API Gateway 경로·CORS·검증

[문서 목차](README.md) · [로컬 실행](MSA_LOCAL_GUIDE.md)

Gateway는 기본 8080에서 /api 경로를 담당 서비스로 전달한다. Config Server(8888)에서 라우팅/CORS를 읽고 Eureka의 인스턴스를 lb://로 선택한다. Config 직접 실행에는 ENCRYPT_KEY가 필수이며 Gateway에는 넣지 않는다.

## 라우팅 계약

원천은 별도 recu-config의 api-gateway.yml, 환경별 api-gateway-local/dev/prod.yml이다. backend 템플릿은 검증 fixture다. **/api 접두사를 제거하지 않는다.**

| 요청 경로 | 목적지 | 비고 |
|---|---|---|
| /api/auth 및 하위 | lb://user-service | 인증은 User에 포함 |
| /api/profile 및 하위 | lb://user-service | 회원 |
| /api/bookmarks 및 하위 | lb://user-service | 북마크 |
| /api/notifications 및 하위 | lb://user-service | 알림 |
| /api/posts/*/guide | lb://ai-service | order=-10, Blog보다 우선 |
| /api/posts, /api/companies, /api/tech-tags 및 각각의 하위 | lb://blog-service | order=0 |
| /api/admin/collect 및 하위 | lb://blog-service | 수집 |

본문·쿼리·Authorization·Refresh-Token은 보존한다. 외부 X-User-Id는 제거한다. 현재 검증된 사용자 식별값을 다시 넣는 필터나 JWT 검증은 구현하지 않았으므로 이 헤더 제거만으로 인증이 완성된 것은 아니다. 자동 Discovery 라우트는 비활성화하여 Config·Eureka·관리 endpoint를 업무 경로로 공개하지 않는다.

## CORS와 부팅 변수

Config 읽기 인증·주소·label은 [Config 안내](CONFIG_SERVER_GUIDE.md)의 클라이언트 값을 사용한다. Gateway에는 FRONTEND_ORIGIN을 지정한다. local 기본은 http://localhost:5173, dev/prod는 실제 프론트 Origin을 반드시 입력한다. 프로토콜·호스트·포트가 일치해야 하며 경로를 붙이지 않는다.

| 항목 | 현재 설정 |
|---|---|
| 대상 | /api/**; 라우트 미매칭 OPTIONS 사전 요청도 처리 |
| 메서드 | GET, POST, PUT, PATCH, DELETE, OPTIONS |
| 요청 헤더 | Authorization, Content-Type, Refresh-Token, X-Request-Id |
| 노출 응답 헤더 | Authorization, Refresh-Token |
| credentials | true; Origin * 사용하지 않음 |
| 중복 응답 헤더 | Allow-Origin/Allow-Credentials 중복 정리 |

프론트가 로컬 8080을 사용한다면 Gateway만 SERVER_PORT=18080으로 바꾸고 프론트 API 주소도 맞춘다. FRONTEND_ORIGIN은 계속 실제 프론트 주소를 사용한다. 이 예시를 확정된 프론트 배포 주소로 보지 않는다. CORS는 브라우저 정책이며 인증·서비스 직접 접근 차단을 대신하지 않는다.

Gateway 기본 연결 timeout 5초·응답 10초, AI 경로 응답 timeout 120초다. 클라우드 Ingress/LB의 timeout 예산은 역할 2·백엔드와 합의한다. 측정된 응답 성능 보장값이 아니다.

## 실행·확인

Eureka → Config 준비 후 Gateway와 업무 앱을 실행한다. Gateway가 반드시 업무 앱보다 먼저일 필요는 없다. Config의 route 목록이 비어 있으면 Gateway는 명확한 오류로 기동 실패한다. 순간 Eureka 등록만 보지 말고 Started 로그·실제 요청까지 확인한다.

브라우저에서 http://localhost:8080/actuator/info 의 config.service=api-gateway, profile을 확인한다. 업무 코드가 없는 골격에서 /api/auth 요청 404는 로그인 기능 검증 성공/실패의 증거가 아니다.

현재 자동 검증은 실제 Eureka·Config·Gateway JAR와 User/AI/Blog 테스트 downstream을 사용한다. 인증 루트/하위 경로를 포함한 요청 15개, 대상 서비스·AI 우선순위·경로·쿼리·메서드·본문·토큰 헤더, X-User-Id 제거, CORS 허용/거부·사전 요청, 관리 경로 404, route 누락 fail-fast를 검사한다.

```bash
# 빌드는 로컬 실행 안내에 따라 먼저 완료
python3 scripts/verify-gateway.py
# 설정 저장소의 작업 파일을 복사본에서 dev 계약으로 검사
python3 scripts/verify-gateway.py --config-repo ../recu-config --profile dev
```

후보 디렉터리의 YAML은 원본을 수정하지 않고 임시 Git으로 복사한다. Config 키·읽기 인증은 테스트가 임시 주입한다. 도메인 업무 로직·실제 JWT 인증·AWS Ingress가 검증되었다고 기록하지 않는다.

수동 preflight 예시(local 기본 Origin 기준):

```bash
curl -i -X OPTIONS http://localhost:8080/api/profile \
  -H 'Origin: http://localhost:5173' \
  -H 'Access-Control-Request-Method: GET' \
  -H 'Access-Control-Request-Headers: authorization'
```

허용 Origin 응답과 다른 Origin 거부를 함께 확인한다. Gateway 자체 /actuator/info·health는 업무 라우트와 별개이므로 외부 Ingress는 관리 경로를 차단해야 한다. 라우팅 변경의 동적 갱신은 demo 설정의 Bus 검증만으로 보장하지 않는다. 현재 절차는 재시작 후 실제 요청으로 확인하며 [검토 목록](REVIEW_CHECKLIST.md)에 별도 검증 항목을 둔다.
