# 원격 recu-config 연결 및 검증

**암호화 통합 후 Config에는 ENCRYPT_KEY가 필수다.** [암호화 검증 안내](CONFIG_ENCRYPTION_GUIDE.md)를 따른다.

현재 #17 기준: **User(인증 포함)·AI·Blog** 3개 서비스다. [통합 실행·검증 안내](AUTH_USER_COMBINE_GUIDE.md)를 먼저 참고한다. 이전 검증 기록은 [이력](history/PRE_AUTH_USER_COMBINE_VALIDATION.md)에 보관한다.

후속 #15의 Kafka Bus 실행·무재시작 갱신 검증은 [Spring Cloud Bus 안내](SPRING_CLOUD_BUS_GUIDE.md)를 참고한다. Kafka가 없으면 BUS_ENABLED=false 기본값을 사용한다.

관련 이슈: #9. 설정 저장소는 [LGCNS-MiniProject2-team6/recu-config](https://github.com/LGCNS-MiniProject2-team6/recu-config)이며 **Private, 기본 브랜치 main**이다. backend와 recu-config는 별개 Git 저장소로 관리한다.

## 검토와 반영 순서

1. recu-config의 작업 브랜치에서 YAML/README 변경을 검토한다.
2. 승인 후 해당 브랜치를 push하고 main 대상 PR로 반영한다. backend의 main 제한 Workflow를 이 설정 저장소에 복사하지 않는다. 여기에는 develop 브랜치를 요구하지 않는다.
3. Config Server에 아래 Git 읽기 인증값을 입력한다.
4. 실제 HTTPS 주소 및 main label로 통합 검증한다.
5. backend의 문서·검증 옵션 변경도 develop 대상 PR로 반영한다.
6. 실제 원격 main에서 설정 수신과 Eureka 등록이 통과한 뒤 backend 이슈 #9와 #7을 완료 처리한다.

**푸시 전 후보 검증을 원격 main 검증 성공이라고 기록하지 않는다.** 초기 main에는 README만 있으므로 설정 branch를 반영하기 전에는 9개 설정 조회가 성공하지 않는다. 실제 원격 검증은 코드 검토 및 업로드 승인 후 수행한다.

## 환경변수: 두 종류의 인증

Config Server 실행 구성(IntelliJ의 Environment variables 또는 배포 Secret):

```dotenv
CONFIG_GIT_URI=https://github.com/LGCNS-MiniProject2-team6/recu-config.git
CONFIG_GIT_LABEL=main
CONFIG_GIT_USERNAME=xxxx
CONFIG_GIT_TOKEN=xxxx
CONFIG_USER=recu-config
CONFIG_PASSWORD=xxxx
EUREKA_URL=http://localhost:8761/eureka/
```

- Git URI와 label은 기본값이 있어 생략 가능하다. 로컬 Git으로 검증할 때는 URI를 file URL로 덮어쓴다.
- xxxx는 미발급 예시이며 실행 전 실제 값으로 교체한다.
- CONFIG_GIT_USERNAME은 읽기 권한이 있는 GitHub 사용자명, CONFIG_GIT_TOKEN은 저장소 읽기 토큰이다.
- CONFIG_USER/CONFIG_PASSWORD는 팀이 정하는 Config Server 전용 Basic 인증이다. 외부 API 업체에서 발급받는 키가 아니다.
- 세 도메인 서비스에는 CONFIG_USER/CONFIG_PASSWORD만 동일하게 주고, Git 토큰은 Config Server에만 전달한다.
- .env.example이나 다운로드한 env 파일은 자동 로딩되지 않는다. 기존 모놀리스의 DB/Google/AI 변수는 이번 연결에 필요하지 않다.
- 개인 PC의 git clone 또는 gh 로그인 성공과 Java JGit의 인증 성공은 서로 다르다. 실제 앱에는 Git 인증값을 별도 주입한다.

GitHub의 fine-grained token을 사용할 경우 Resource owner를 팀 조직으로 선택하고, repository access를 recu-config로 제한하며 Contents를 Read-only로 지정한다. 만료일을 두고 조직 승인이 필요하면 승인 완료 후 사용한다. 계정이 outside collaborator이면 fine-grained 지원 제한이 있을 수 있으므로 조직 정책에 맞는 인증 방식을 사용한다. 토큰을 채팅/README/URL에 넣지 않는다.

[GitHub 공식 토큰 안내](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens)

## IntelliJ에서 직접 검증

1. backend 루트를 열고 Gradle Reload, JDK 17 선택.
2. Eureka 실행.
3. Config Server에 위 환경변수 입력 후 실행. Git 인증 실패 또는 main 설정 미등록이면 오류를 해결한다.
4. 세 도메인 앱에 동일 Config 인증값을 넣고 실행.
5. Eureka에 CONFIG-SERVER 및 세 서비스 UP 확인.
6. 각 서비스의 /actuator/info에 source=recu-config, profile=local, 해당 service 이름, version=v1 확인.

Git/서비스별 설정 조회는 Config Server의 `/{service}/{profile}/main`을 사용한다. 인증 없이 401이고 Basic 인증 후 해당 profile/service 설정이 있어야 한다. 응답 전체를 공유하지 말고 이름·프로필·Git commit만 확인한다. dev/prod는 클러스터 DNS가 기본값이므로 PC에서 실행하면 EUREKA_URL을 localhost로 덮어쓴다.

## 재사용 가능한 통합 검증

backend 루트에서 JAR를 먼저 빌드한다.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
bash ./gradlew test spotlessCheck bootJar
```

### 1. 기존 임시 Git 회귀 검증

```bash
python3 scripts/verify-config.py
```

테스트용 Git과 Config 인증을 매번 생성한다. GitHub 인증은 사용하지 않는다.

### 2. 미푸시 recu-config 후보 커밋 검증

```bash
CONFIG_CANDIDATE_SHA=$(git -C ../recu-config rev-parse HEAD)
python3 scripts/verify-config.py \
  --git-uri file:///Users/hy/Desktop/miniproject2/recu-config \
  --git-label "$CONFIG_CANDIDATE_SHA" \
  --expected-commit "$CONFIG_CANDIDATE_SHA" \
  --profile dev
```

본인의 clone 경로가 다르면 file URI를 바꾼다. file URI는 임시 snapshot으로 clone한 뒤 검사해 원래 저장소의 branch/worktree를 변경하지 않는다. 이 검증은 새 저장소의 YAML 및 후보 commit을 사용하는 검증이며 HTTPS 인증 검증은 아니다.

### 3. 업로드·main 반영 후 실제 원격 검증

macOS zsh에서 Git 자격 증명을 prompt로 입력한다. 토큰을 명령줄 문자열에 직접 넣지 않는다.

```bash
read 'CONFIG_GIT_USERNAME?GitHub 사용자명: '
read -s 'CONFIG_GIT_TOKEN?recu-config 읽기 토큰: '; echo
export CONFIG_GIT_USERNAME CONFIG_GIT_TOKEN
python3 scripts/verify-config.py \
  --git-uri https://github.com/LGCNS-MiniProject2-team6/recu-config.git \
  --git-label main \
  --profile local
unset CONFIG_GIT_TOKEN CONFIG_GIT_USERNAME
```

예상 Git commit을 고정하고 싶으면 `--expected-commit 전체SHA`도 전달한다. `--git-label`은 main, slash가 없는 tag, commit SHA를 받는다. 작업 브랜치에 slash가 있으므로 PR 전 원격 후보 검증은 branch명 대신 해당 commit SHA를 사용한다.

검증 스크립트는 다음을 검사한다.

- 무인증/잘못된 Config 인증 401
- 3개 서비스 x local/dev/prod 9개 설정과 Git commit 일치
- 선택한 profile로 네 앱이 실행되고 중앙 info 설정이 일치
- Config와 세 서비스 Eureka UP 및 실행 포트 일치
- 잘못된 Config 인증이면 앱 기동 실패

Config 인증은 임시 무작위 값으로 생성해 Git 자격 증명과 분리한다. Git 자격 증명은 호출 환경변수에서 받아 자식 JVM에만 전달하고 스크립트가 출력하거나 파일에 쓰지 않는다. 다른 저장소를 검증할 때도 URI에 username/token을 포함하지 않는다. Python은 HTTPS/file 외 scheme이나 URL 내 인증정보를 거부한다. 로그는 출력된 임시 폴더에서 확인하고 공유 전 비밀값을 점검한다.

## 설정의 원천

서비스별 local/dev/prod YAML의 운영 원천은 별도 recu-config다. backend/config-repository-template은 초기 예제 및 임시 Git 회귀 검증용 fixture로 유지한다. 배포 시 그 템플릿을 파일시스템에서 직접 읽는 것이 아니라 Config Server가 recu-config의 label을 읽는다. 설정 변경을 실반영하려면 Config 저장소 PR을 main에 반영해야 하며 Bus 도입 전에는 서비스 재시작도 필요하다.
