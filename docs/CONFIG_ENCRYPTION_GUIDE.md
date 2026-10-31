# Config 값 암호화 및 키 주입

관련 이슈: #13. **Git에 저장하는 개별 비밀값만 `{cipher}`로 암호화**한다. 파일 전체를 암호화하는 것이 아니다. Git에서 암호문을 읽은 Config Server가 복호화하고 도메인 서비스에 전달한다. 전송 구간 보호나 사용자 JWT 암호화와 다른 기능이다.

## 이번부터 필요한 실행 설정

**Config Server에 ENCRYPT_KEY를 반드시 추가해야 한다.** 기존 Git 토큰, Config 읽기 계정과 비밀번호는 유지한다. 키를 지정하지 않으면 Config Server를 기동할 수 없다. 기본 키를 코드나 Git에 제공하지 않는다.

| 값 | 주입 대상 | 용도 |
|---|---|---|
| ENCRYPT_KEY | Config Server만 | `{cipher}` 복호화와 /encrypt 암호화에 사용하는 대칭키 |
| CONFIG_USER / CONFIG_PASSWORD | Config + 도메인 + Gateway | 기존 중앙 설정 읽기 인증, 변경 없음 |
| CONFIG_ADMIN_USER / CONFIG_ADMIN_PASSWORD | Config Server만, 선택 | 암복호화 관리 API 전용 계정 |
| CONFIG_GIT_USERNAME / CONFIG_GIT_TOKEN | Config Server만 | 기존 Private Git 읽기 인증 |

관리자 두 값은 함께 설정하거나 둘 다 생략한다. 생략하면 관리 API가 비활성 권한 상태가 되고 **저장된 암호문의 서버 복호화와 기존 서비스 읽기는 가능**하다. 읽기 계정과 관리자 사용자명은 달라야 하며, 한쪽 관리자 값만 있거나 동일 사용자명이면 오류로 종료한다.

IntelliJ: ConfigServerApplication → Edit Configurations → Environment variables에서 기존 값에 추가한다.

```text
ENCRYPT_KEY=xxxx;CONFIG_ADMIN_USER=config-admin;CONFIG_ADMIN_PASSWORD=xxxx
```

xxxx를 그대로 쓰지 않는다. ENCRYPT_KEY는 최소 32바이트 이상의 충분히 긴 무작위 문자열을 사용한다. macOS에서 다음 명령으로 64자리 hex 문자열을 생성해 **본인의 비밀 보관 위치와 Config Server 실행 구성에만** 보관할 수 있다.

```bash
openssl rand -hex 32
```

도메인/Gateway 실행 구성에는 위 세 값을 넣지 않는다. `.env.example`은 자동 로딩되지 않는다. 설정 예시와 소스에는 실제 키·관리자 비밀번호를 적지 않는다. 현재 테스트 도구는 테스트마다 임시 키·관리자 계정을 만들므로 사용자가 실키를 준비하지 않아도 자동 검증할 수 있다.

## API 접근 정책

| 요청 | 무인증 | 읽기 계정 | 관리자 |
|---|---|---|---|
| Health/Probe GET | 허용 | 허용 | 허용 |
| 설정 GET | 401 | 허용 | 허용 |
| /encrypt, /decrypt POST | 401 | 403 | 허용 |
| /encrypt, /decrypt GET 및 하위 변형 경로 | 거부 | 거부 | 거부 |
| 다른 POST/변경 요청 | 거부 | 거부 | 거부 |

관리자는 내부 Basic 인증 요청으로만 사용한다. Stateless로 처리하고 CSRF 예외는 `/encrypt`, `/decrypt` 두 관리 경로에만 제한한다. 다른 경로의 CSRF 설정을 전부 끄지 않는다. 일반 서비스가 가진 읽기 비밀번호만으로 임의 암복호화를 호출할 수 없다. 내부 네트워크 제한과 TLS도 함께 적용해야 하며 Basic 인증은 전송 암호화가 아니다.

## 테스트 문자열로 수동 확인

먼저 Eureka → 키와 관리자 환경변수를 넣은 Config Server 순서로 실행한다. 실제 비밀번호 대신 다음 **테스트 문자열**만 사용한다. curl은 관리자 비밀번호를 prompt로 입력받으므로 명령 기록에 직접 적지 않는다.

```bash
# 무인증 POST: 401
curl -i -X POST -H 'Content-Type: text/plain' \
  --data-binary 'verification-only-value' http://localhost:8888/encrypt

# 관리자는 암호문을 받을 수 있다. -u 사용자명 뒤 비밀번호는 prompt에 입력
printf '%s' 'verification-only-value' \
  | curl -fsS -u config-admin -H 'Content-Type: text/plain' \
    --data-binary @- http://localhost:8888/encrypt
```

출력된 암호문만 복사한다. 평문이나 키를 Config 저장소에 넣지 않는다. `Content-Type: text/plain`과 `--data-binary`를 사용하면 +, = 등 특수문자 변형을 피할 수 있다.

```bash
# 아래 암호문 부분을 /encrypt 출력으로 교체. 테스트 값에만 사용
printf '%s' '암호문' \
  | curl -fsS -u config-admin -H 'Content-Type: text/plain' \
    --data-binary @- http://localhost:8888/decrypt
```

원래 테스트 문자열이 반환되면 왕복 성공이다. 관리 비밀번호나 실제 복호화 응답을 채팅/PR/로그에 남기지 않는다.

## Git에 적용하는 형태

recu-config의 `examples/encrypted-setting.yml.example`은 **자동 로딩하지 않는 문법 예시**다. `{cipher}xxxx`를 활성 설정에 그대로 넣으면 복호화할 수 없으므로 실제 서비스 YAML에 복사하기 전 자기 환경의 /encrypt 결과로 바꿔야 한다.

```yaml
crypto-demo:
  secret: '{cipher}이환경의_encrypt결과'
```

실제 DB 연결에 적용한다면 백엔드·DB 담당과 합의한 `spring.datasource.password`에 암호문을 사용한다. 현재 DB 연결이 없는 골격에 DB 속성을 임의로 추가하지 않는다. 같은 비밀번호를 환경변수(DB_PASSWORD 또는 SPRING_DATASOURCE_PASSWORD)로도 주입하면 우선순위가 달라질 수 있으므로 전달 방식을 하나로 정리한다.

운영 Git에 임의 테스트 키로 만든 활성 암호문을 미리 넣지 않는다. local/dev/prod마다 사용하는 키와 암호문의 대응이 정해진 뒤 적용한다. 따라서 이번 예시는 recu-config main을 깨지 않으며, 자동 테스트에서만 임시 Git에 실제 암호문을 넣고 네 서비스의 수신을 검증한다.

## 자동 검증

backend 루트:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
bash ./gradlew test spotlessCheck bootJar
python3 scripts/verify-config.py --verify-encryption
```

실제 플랫폼/도메인 JAR 7개와 임시 Git을 사용한다. 키·관리자 비밀번호는 무작위로 생성하고 Config Server에만 주입한다. 검증은 원본 저장소를 변경하지 않도록 `--git-uri`와 병용할 수 없다.

1. 관리자 /encrypt·/decrypt 왕복, 무인증 401, 읽기 계정 403, 잘못된 관리자 인증 401 확인
2. 실제 암호문을 임시 Git에 commit하고 평문이 Git YAML에 없는지 검사
3. Config API에서 local/dev/prod x 네 서비스의 복호화 설정 확인
4. 네 도메인 서비스에서 복호화된 **테스트 값** 수신 확인
5. Gateway의 GET/POST /encrypt·/decrypt는 404 확인
6. Health/Probe와 Eureka 등록, 잘못된 Config 읽기 인증의 fail-fast 확인
7. 검증 프로세스·임시 Git 정리

테스트용 값은 임시 fixture에서만 info에 연결해 수신을 확인한다. **실제 비밀값을 info에 노출하는 코드를 추가한 것이 아니다.** 실제 앱 코드에 비밀값 확인용 Controller나 로그도 추가하지 않는다.

기존 Config/Gateway 통합 검증 도구는 별도 테스트 키를 Config Server에만 주입하도록 갱신했다.

## 역할 2 인계: Kubernetes Secret

`platform/config-server/k8s/encryption-env.patch.yaml`은 기존 Deployment에 병합할 env patch다. 단독 Deployment가 아니므로 바로 apply하지 않는다. 합의된 Namespace는 recu-dev, Deployment는 config-server, 컨테이너 이름은 app 기준이며 실제 환경과 다르면 역할 2가 조정한다.

Secret `config-encryption`의 키:

- ENCRYPT_KEY: 필수, 암호문 생성에 사용한 것과 동일한 키
- CONFIG_ADMIN_USER / CONFIG_ADMIN_PASSWORD: 관리 API를 열 때 둘 다 지정, 생략 가능

역할 2가 안전하게 준비한 env 파일로 생성하는 예시:

```bash
kubectl -n recu-dev create secret generic config-encryption \
  --from-env-file=/secure/config-encryption.env \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n recu-dev patch deployment config-server --type strategic \
  --patch-file platform/config-server/k8s/encryption-env.patch.yaml
kubectl -n recu-dev rollout status deployment/config-server
```

위 파일과 Secret 실값을 Git에 저장하지 않는다. 실제 생성·배포는 이번 로컬 작업에서 실행하지 않았다. Secret의 base64는 자체 암호화가 아니므로 클러스터의 Secret 읽기 권한도 제한한다. dev/prod는 별도 Secret과 키를 사용하고, 도메인/Gateway에는 ENCRYPT_KEY나 관리자 인증을 주입하지 않는다. Config 관리 API도 외부 Ingress/Gateway에 노출하지 않는다.

## 키 보관·분실·교체

키가 없으면 기존 암호문을 복호화할 수 없다. 재시작할 때마다 새 키를 생성하지 말고 Secret/비밀 관리자에 같은 값을 보관한다. Config Server가 여러 replica면 같은 환경에 동일 키를 주입한다.

단순히 ENCRYPT_KEY만 교체하면 기존 Git 암호문이 읽히지 않는다. 먼저 기존 키로 복호화 가능한지 확인하고 새 키로 재암호화할 설정을 준비한 뒤, 새 암호문과 새 키를 함께 반영하는 유지보수 계획을 정한다. 오류 시 기존 키·설정 commit으로 복원할 수 있도록 하되 키 자체를 Git에 남기지 않는다.

Config는 복호화 실패 시 원래 속성을 제거하고 `invalid.*` 속성으로 표시할 수 있다. 따라서 HTTP 200만으로 성공을 판단하지 말고 실제 필수 설정 수신/기동도 확인한다. 비밀값 자체를 출력하는 검증은 피한다. 키 교체나 DB/API 키 갱신이 모든 서비스에 자동 반영되는 것은 아니며 아직 Bus도 구현 전이다.

참고: [Config 4.2 암복호화](https://docs.spring.io/spring-cloud-config/reference/4.2/server/encryption-and-decryption.html), [키 관리](https://docs.spring.io/spring-cloud-config/reference/4.2/server/key-management.html)

## 구현 검증 기록 (2026-11-01 KST)

- 전체 Gradle test/spotlessCheck/bootJar 성공
- 실제 JAR 7개로 관리자 암복호화 왕복, 무인증 401·읽기 계정 403·잘못된 관리자 인증 401 확인
- 임시 Git에는 cipher 값만 commit되고 평문은 저장되지 않음 확인
- local/dev/prod x 4개 서비스의 Config 복호화 응답 및 실제 네 서비스 수신 확인
- 도메인/Gateway에는 ENCRYPT_KEY와 관리자 인증을 주입하지 않음
- 실제 Gateway에서 GET/POST /encrypt·/decrypt 404 확인
- 기존 관리자 미설정 상태의 Config 읽기 통합 검증 회귀 성공
- Gateway 14개 경로·CORS·관리 경로·라우팅 누락 실패 검증 회귀 성공
- Secret patch와 비활성 cipher 예시 YAML 문법 검사 통과
- 실제 Kubernetes Secret 생성/배포와 실제 DB 비밀번호 변경은 미수행
- backend/recu-config 작업 커밋만 준비했으며 원격 push/PR은 검토 승인 전이므로 미수행
