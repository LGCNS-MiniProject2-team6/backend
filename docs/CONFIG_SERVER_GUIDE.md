# Config Server와 원격 설정

[문서 목차](README.md) · [로컬 실행](MSA_LOCAL_GUIDE.md) · [암호화](CONFIG_ENCRYPTION_GUIDE.md)

## 원천과 역할

운영 설정 원천은 별도 Private 저장소 [recu-config](https://github.com/LGCNS-MiniProject2-team6/recu-config)다. backend/config-repository-template은 초기 예제이자 자동 검증 fixture이며 실행 앱이 직접 읽는 배포 설정은 아니다. 설정 변경은 설정 저장소의 PR로 반영하고 backend 코드와 호환성을 맞춘다.

앱은 부팅할 때 Config에서 자기 이름·프로필·label의 설정을 받는다. Config 주소·읽기 인증·앱 이름·프로필은 이 조회 전에 알아야 하므로 부팅 환경변수로 유지한다. Git push만으로 실행 중인 앱이 자동 변경되지는 않는다. 갱신 가능한 객체는 Bus로 갱신할 수 있고, 그 밖의 변경은 재배포/재시작이 필요하다.

## 네 가지 자격증명 구분

| 환경변수 | 용도 | 주입 대상 |
|---|---|---|
| CONFIG_USER / CONFIG_PASSWORD | Config 설정을 읽는 Basic 인증 | Config와 User·AI·Blog·Gateway에 같은 환경 값 |
| CONFIG_GIT_USERNAME / CONFIG_GIT_TOKEN | Config → Private Git 읽기 | Config만 |
| CONFIG_ADMIN_USER / CONFIG_ADMIN_PASSWORD | 암복호화·Bus 관리 호출 | Config만, 기존 관리자 계정 재사용 |
| ENCRYPT_KEY | Git 암호문 암복호화 | Config만, 필수 |

관리자 두 값은 함께 설정하며 읽기 계정과 사용자명이 달라야 한다. 모두 생략하면 설정 제공은 가능하지만 관리 API를 호출할 관리자 계정이 없다. Git 토큰의 소유자 계정은 Git 사용자명이며 조직 이름을 그대로 넣지 않는다. 저장소 읽기 권한·조직 승인·만료 여부를 확인한다. IntelliJ/git/gh의 로그인만으로 Java JGit 인증이 전달되지는 않는다.

## IntelliJ 부팅 변수

다음 예시는 변수 이름만 보여준다. xxxx는 실제 값으로 교체하며 Git에 저장하지 않는다. 앱은 .env와 .env.example을 자동 로딩하지 않는다.

ConfigServerApplication:

```text
CONFIG_USER=xxxx;CONFIG_PASSWORD=xxxx;ENCRYPT_KEY=xxxx;CONFIG_GIT_URI=https://github.com/LGCNS-MiniProject2-team6/recu-config.git;CONFIG_GIT_LABEL=main;CONFIG_GIT_USERNAME=xxxx;CONFIG_GIT_TOKEN=xxxx;EUREKA_URL=http://localhost:8761/eureka/;BUS_ENABLED=false
```

관리 API를 사용할 때 Config에만 추가한다.

```text
CONFIG_ADMIN_USER=config-admin;CONFIG_ADMIN_PASSWORD=xxxx
```

User·AI·Blog·Gateway 실행 구성 공통:

```text
CONFIG_USER=xxxx;CONFIG_PASSWORD=xxxx;CONFIG_SERVER_URL=http://localhost:8888;CONFIG_GIT_LABEL=main;SPRING_PROFILES_ACTIVE=local;EUREKA_URL=http://localhost:8761/eureka/;BUS_ENABLED=false
```

Gateway의 FRONTEND_ORIGIN은 [Gateway 안내](GATEWAY_GUIDE.md)를 따른다. dev/prod에서는 SPRING_PROFILES_ACTIVE를 각각 지정하고 서버·클라이언트 label을 맞춘다. 로컬 PC에서 dev/prod를 시험할 때 EUREKA_URL은 localhost로 덮어쓴다. 클러스터에서는 같은 Namespace의 Service DNS를 사용한다.

## 조회·실패 확인

Eureka → Config → 클라이언트 순서로 실행한다. 필수 Config import이므로 Config가 준비되지 않았거나 인증이 틀리면 클라이언트는 기동 실패한다. URL 형식은 /{앱이름}/{프로필}/{label}이며 예시는 http://localhost:8888/user-service/local/main 이다.

Postman GET에 Basic Auth로 읽기 계정을 입력한다. 인증 없음/잘못된 인증은 401이다. 응답의 name/profiles/version(Git SHA)과 유효한 info.config.service/profile을 확인한다. 공통 YAML만 반환되어도 HTTP 200일 수 있으므로 상태 코드만으로 서비스 파일 존재를 판단하지 않는다. 설정 응답에는 복호화된 값이 있을 수 있으므로 전체 JSON을 채팅/PR에 붙이지 않는다.

User 8082, AI 8083, Blog 8084, Gateway 8080의 /actuator/info에는 비밀이 아닌 config.source/service/profile/version 확인값이 있다. info.config.version은 진단용 값이고 API 응답의 version(Git SHA), Bus의 demo.config-version과 서로 다르다.

## 임시 Git·로컬 후보·실제 원격 검증

JAR 빌드 명령은 [로컬 안내](MSA_LOCAL_GUIDE.md)를 먼저 실행한다. 아래 명령은 backend 루트 기준이다.

```bash
# GitHub 인증 없는 임시 Git 검증
python3 scripts/verify-config.py
# 별도 설정 저장소 후보 커밋을 복제본에서 검증
CONFIG_CANDIDATE_SHA=$(git -C ../recu-config rev-parse HEAD)
python3 scripts/verify-config.py \
  --git-uri file:///Users/hy/Desktop/miniproject2/recu-config \
  --git-label "$CONFIG_CANDIDATE_SHA" --expected-commit "$CONFIG_CANDIDATE_SHA" --profile dev
```

로컬 후보는 작업 파일이 아니라 commit한 YAML을 읽는다. 원본 저장소 checkout은 바꾸지 않는다. 경로가 다르면 본인 절대경로를 사용한다. 이 결과는 HTTPS 인증 검증과 구분한다.

실제 원격 검증은 토큰을 명령줄에 넣지 않고 macOS zsh prompt로 입력한다.

```bash
read 'CONFIG_GIT_USERNAME?GitHub 사용자명: '
read -s 'CONFIG_GIT_TOKEN?recu-config 읽기 토큰: '
export CONFIG_GIT_USERNAME CONFIG_GIT_TOKEN
python3 scripts/verify-config.py \
  --git-uri https://github.com/LGCNS-MiniProject2-team6/recu-config.git --git-label main --profile local
unset CONFIG_GIT_TOKEN CONFIG_GIT_USERNAME
```

필요하면 --expected-commit 전체SHA를 지정한다. 이 스크립트의 --git-label은 slash 없는 tag/main/SHA를 받으므로 slash가 있는 작업 브랜치는 commit SHA로 검사한다. --verify-encryption/--verify-bus는 임시 Git을 수정하므로 외부 --git-uri와 병용할 수 없다. 자동 검증은 Config 전용 키·읽기 계정을 임시 생성한다. 외부 Git에 이미 특정 키의 암호문이 있다면 임시 키와 일치하지 않을 수 있으므로 해당 환경의 Config로 별도 검증한다.

## 장애 확인

- Connection refused: Config 실행 여부·8888 포트·Started 로그부터 확인.
- ENCRYPT_KEY 누락/빈 값: Config만 비밀 보관 키를 주입하고 재시작.
- Config 401: 읽기 계정 일치 확인. Git 인증·관리자 계정과 구분.
- 관리 POST 403: 읽기 계정이 아닌 공통 관리자 계정 사용. API별 Content-Type과 최신 Config 재시작 확인.
- Git clone 실패: 저장소 URI·읽기 권한·조직 승인·만료·label·네트워크 확인. 인증값을 URL에 넣지 않음.
- 서비스별 설정 누락: 앱 이름·파일 이름·프로필·label 확인.

Config는 내부 Service로 유지하고 외부 Gateway 라우트에 연결하지 않는다. Secret/네트워크/TLS·배포 계약은 [인계](PLATFORM_K8S_HANDOFF.md), 실제 확정이 필요한 항목은 [검토 목록](REVIEW_CHECKLIST.md)에 둔다.
