# 설정 암호화·키 주입·Postman 검증

[문서 목차](README.md) · [Config 인증](CONFIG_SERVER_GUIDE.md) · [Bus](SPRING_CLOUD_BUS_GUIDE.md)

## 동작과 키

파일 전체가 아니라 Git에 저장하는 개별 설정값을 암호화한다. Config가 {cipher} 값을 복호화해 클라이언트에 전달한다. 사용자 JWT·전송 TLS·Kubernetes Secret 보호와는 별도 기능이다.

| 변수 | 용도 | 대상 |
|---|---|---|
| ENCRYPT_KEY | 설정 암호화·복호화 | Config만, 필수 |
| CONFIG_ADMIN_USER / CONFIG_ADMIN_PASSWORD | /encrypt·/decrypt·Bus 관리 API | Config만, 선택적 한 쌍 |
| CONFIG_USER / CONFIG_PASSWORD | 설정 읽기 | Config와 네 클라이언트 |

키 누락·빈 값·미해결 placeholder는 ConfigEncryptionKeyValidation에서 기동을 거부한다. 같은 환경의 Config replica에는 같은 키를 쓰고 재시작마다 새 키를 만들지 않는다. 키를 Git에 저장하지 않는다.

macOS에서 최초 키를 생성하는 예시:

```bash
openssl rand -hex 32
```

생성값은 비밀 보관 위치와 Config 실행 구성/Secret에만 보관한다. IntelliJ Config 실행 구성에는 ENCRYPT_KEY와 기존 Git/읽기 인증을 유지하고, 관리자 계정이 이미 있으면 재사용한다. 관리자는 읽기 계정과 다른 아이디를 쓰고 두 변수를 함께 설정한다. 둘 다 생략해도 필수 키가 있으면 설정 제공은 되지만 관리 API를 호출할 수 없다. .env는 자동 로딩되지 않는다.

## Postman 수동 검증 (Kafka 불필요)

Eureka → Config를 실행하고 Config Started 로그·health UP을 확인한다. Kafka가 없으면 BUS_ENABLED=false로 둔다.

| 항목 | 암호화 요청 | 복호화 요청 |
|---|---|---|
| Method | POST | POST |
| URL | http://localhost:8888/encrypt | http://localhost:8888/decrypt |
| Authorization | Basic Auth, Config 관리자 | 같은 관리자 |
| Content-Type | text/plain | text/plain |
| Body | raw → Text, verification-only-value | /encrypt가 반환한 암호문만 |
| 성공 | 200과 암호문 | 200과 verification-only-value |

/decrypt 본문에는 {cipher}를 붙이지 않는다. 별도로 No Auth는 401, 읽기 계정은 403, 관리 GET은 거부, Gateway 8080의 /encrypt·/decrypt는 404인지 확인한다. 403이면 관리자 인증·최신 Config 재시작 여부를 확인하고, 아이디 중복 오류는 읽기/관리자 아이디를 다르게 지정한다.

## Git 암호문과 서비스 수신

저장 형식은 아래와 같다. 문법 예시이므로 그대로 활성 설정에 넣지 않는다.

```yaml
crypto-demo:
  secret: '{cipher}해당환경_encrypt응답'
```

- 테스트는 테스트 브랜치/프로필과 비밀이 아닌 값으로 먼저 수행한다. 임의 키의 암호문을 공유 main의 공통 application.yml에 넣지 않는다.
- dev/prod 키가 다르면 각 환경의 YAML과 암호문도 대응시킨다. 다른 키로 읽는 공통 암호문은 복호화에 실패한다.
- Config 조회가 200이어도 복호화 실패 시 속성이 빠지거나 invalid.*가 생길 수 있다. 필수 속성 수신·실제 기동을 확인하고 전체 설정 응답을 공개하지 않는다.
- 실제 DB/JWT/API 키의 설정명과 주입 방식은 백엔드·역할 3과 확정한다. 현재 골격에 DB 비밀번호 속성을 임의로 추가하지 않는다. 환경변수와 Git에 같은 속성을 중복하면 우선순위가 달라질 수 있다.
- 실제 비밀을 /actuator/info·Controller·로그에 출력하지 않는다. 자동 검증의 info marker는 임시 Git의 비밀이 아닌 테스트 문자열만 사용한다.

## 자동 검증

[공통 빌드](MSA_LOCAL_GUIDE.md)를 먼저 실행한다.

```bash
# Kafka 없는 암복호화·권한·세 서비스 수신·키 누락 거부
python3 scripts/verify-config.py --verify-encryption
# 실제 테스트 Kafka의 암호문 변경 후 Bus 한 번 갱신
python3 scripts/verify-config.py --verify-encryption --verify-bus --profile dev
```

테스트는 실제 앱 JAR 6개와 임시 Git을 사용하고 Config에만 임시 키·관리자를 주입한다. 세 서비스 × local/dev/prod의 9개 응답, 클라이언트 수신·관리 경로 차단을 확인한다. 결합 모드에서는 네 클라이언트의 새 복호화 값과 demo v2, 동일 PID/startTime을 검사한다. 외부 --git-uri와 병용할 수 없다. 실제 운영 키·GitHub·AWS가 필요하지 않다.

## Kubernetes 인계·키 교체

역할 2는 환경별 Secret의 ENCRYPT_KEY를 Config에만 주입한다. 예시 이름 config-encryption과 실제 Namespace·Deployment·컨테이너 이름을 먼저 맞춘다. [encryption-env.patch.yaml](../platform/config-server/k8s/encryption-env.patch.yaml)은 기존 Deployment에 합치는 ENCRYPT_KEY 전용 patch이며 단독 apply하지 않는다. 기존 Bus 관리자 참조를 덮어쓰지 않는다. 관리자·Git 토큰·키를 Gateway·도메인에 전달하지 않는다.

키 분실 시 기존 암호문을 복호화할 수 없다. 교체할 때는 새 키로 재암호화한 설정과 새 Secret을 함께 준비하고 롤백을 계획한다. 키만 교체하거나 Bus로 암호화 키 자체를 바꾸지 않는다. 애플리케이션 환경변수 변경은 재배포/재시작으로 반영한다.

로컬 자동 검증과 사용자 Postman의 왕복·401/403은 확인했다. 실제 Secret 생성·운영 암호문 반영·TLS/클라우드 검증은 [검토 목록](REVIEW_CHECKLIST.md)에 따라 수행한다.
