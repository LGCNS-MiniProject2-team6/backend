# ReCu Backend

> 개발자의 경력·보유 기술·관심 기술·희망 직무를 바탕으로 기업 기술 블로그를 추천하고, 사용자 수준에 맞는 AI 읽기 가이드를 제공하는 서비스의 MSA 백엔드입니다.

기존 ReCu 모놀리스의 기능을 User·Blog·AI로 분리하고, Eureka·Config Server·API Gateway를 공통 플랫폼으로 구성했습니다. 서비스별 데이터 소유권을 분리하고, 새 글 수집과 관심 사용자 알림을 Kafka 이벤트로 연결했습니다. 이번 프로젝트의 중심은 기존 서비스의 MSA 전환과 배포·운영 체계 구축입니다.

- 프로젝트 상태: MSA 전환 및 dev/prod 배포 보고 반영
- 서비스: [ReCu](https://recu.kro.kr)
- 문서: [로컬 실행](docs/MSA_LOCAL_GUIDE.md) · [문서 목차](docs/README.md)
- 문서 기준일: 2026-11-04

## 주요 기능

| 기능 | 사용자에게 제공하는 결과 |
| --- | --- |
| Google 로그인·회원 프로필 | 경력·희망 직무·보유 기술·관심 기술 등록, JWT 기반 인증 |
| 기업·게시글 추천 | 관심 기술에 맞는 기업 추천, 기업 상세·글 목록·본문·목차 조회 |
| AI 읽기 가이드 | 프로필과 게시글 구간을 바탕으로 Gemini 가이드 생성·저장·캐시 조회 |
| 북마크 | 관심 게시글 저장·목록 조회·삭제 |
| 새 글 알림 | 수집된 글의 분야·기술과 사용자 관심사를 비교해 알림 저장, 목록 조회·읽음 처리 |
| 비동기 수집 | 외부 수집 데이터 접수, Kafka 저장 요청·저장 완료 이벤트, 중복·재시도·DLT 처리 |

## 빠른 시작

### 사전 준비

| 항목 | 필요한 버전·조건 |
| --- | --- |
| Java | JDK 17. IDE의 Project SDK와 Gradle JVM도 동일하게 설정 |
| 빌드 도구 | 저장소의 Gradle Wrapper 사용 |
| Docker | 로컬 MariaDB·Kafka 컨테이너 실행 |
| 셸·IDE | Bash, IntelliJ IDEA. Windows 셸 명령은 Git Bash 기준 |
| 중앙 설정 | backend와 같은 상위 폴더에 `recu-config` 복제, 비공개 저장소 읽기 권한 필요 |
| 외부 연동 | Google OAuth 클라이언트, AI 기능에는 Gemini API 키 |

### 설치 및 설정

```bash
git clone -b develop https://github.com/LGCNS-MiniProject2-team6/backend.git
git clone -b main https://github.com/LGCNS-MiniProject2-team6/recu-config.git
cd backend
bash scripts/local-setup.sh
```

스크립트는 로컬 환경변수 파일을 생성하고 MariaDB·Kafka를 준비합니다. backend 옆의 recu-config가 있으면 Config Server가 로컬 Git을 읽도록 설정합니다. 기존 환경변수 파일은 자동 갱신되지 않으므로, 설정 계약이 바뀌었다면 필요한 항목을 별도로 맞춥니다. DB 포트가 충돌하면 최초 준비 시 `DB_PORT=3307 bash scripts/local-setup.sh`로 지정할 수 있습니다.

| 설정 이름 | 용도 | 주입 기준 |
| --- | --- | --- |
| `CONFIG_USER`, `CONFIG_PASSWORD` | Config 설정 조회 인증 | Config와 클라이언트에 같은 환경 값 |
| `ENCRYPT_KEY` | 중앙 설정 암복호화 | Config Server에만 주입 |
| `CONFIG_GIT_URI`, `CONFIG_GIT_LABEL` | 설정 Git 위치·label | 로컬 준비 스크립트 사용, 기본 label `main` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | User·AI DB 연결 | 각 프로세스에 자기 서비스 DB 값 주입 |
| `BLOG_DB_URL`, `BLOG_DB_USER`, `BLOG_DB_PASSWORD` | Blog DB 연결 | Blog 전용 DB·계정 사용 |
| `INTERNAL_API_TOKEN` | 서비스 간 내부 API 인증 | 같은 환경의 User·AI·Blog가 공유 |
| `GEMINI_API_KEY` | AI 가이드 생성 | 생성된 AI 실행 설정에 본인 키 입력 |
| `BLOG_COLLECT_API_KEY` | 외부 수집 API 인증 | Blog와 외부 수집기에 동일 값 주입 |
| `FRONTEND_ORIGIN` | Gateway CORS 허용 출처 | 로컬 프론트 기본 `http://localhost:5173` |
| `BUS_ENABLED`, `KAFKA_BOOTSTRAP_SERVERS`, `BUS_DESTINATION` | 설정 갱신 이벤트 연결 | 준비된 Kafka와 환경별 Bus 토픽 사용 |

실제 비밀번호·토큰은 커밋하지 않습니다. 공유 IntelliJ 실행 구성이 로컬 환경변수 파일을 읽으며, 일반 `bootRun`·`java -jar`에는 필요한 환경변수를 별도로 전달해야 합니다.

### 실행

1. IntelliJ에서 backend를 Gradle 프로젝트로 열고 새로고침합니다.
2. 공유 실행 구성 `1. Platform (Eureka + Config)`를 실행합니다.
3. Config 기동을 확인하고 `2. Services (Gateway + User + AI + Blog)`를 실행합니다.
4. [frontend](https://github.com/LGCNS-MiniProject2-team6/frontend)에서 `yarn dev`로 화면을 실행합니다.

| 모듈 | 역할 | 기본 포트 |
| --- | --- | ---: |
| `platform/discovery-server` | Eureka 서비스 등록·검색 | 8761 |
| `platform/config-server` | 중앙 설정·암복호화·Bus 갱신 | 8888 |
| `platform/api-gateway` | 외부 API 진입·JWT 검증·라우팅·CORS | 8080 |
| `services/user-service` | 인증·프로필·북마크·알림 | 8082 |
| `services/ai-service` | AI 읽기 가이드 | 8083 |
| `services/blog-service` | 기업·글·태그·수집 파이프라인 | 8084 |

Eureka는 `http://localhost:8761`, 각 앱 상태는 `/actuator/health`에서 확인합니다. 기동 직후에는 Eureka 갱신 전까지 Gateway 요청이 일시적으로 실패할 수 있습니다. 독립 Auth 서비스는 사용하지 않으며 인증은 User에 포함됩니다.

종료 시 IDE의 앱을 중지하고 `bash scripts/local-setup.sh down`으로 컨테이너를 내립니다. DB 볼륨은 유지됩니다. `reset`은 로컬 DB 데이터를 삭제하므로 일반 종료에 사용하지 않습니다.

## 사용 예시

1. Google 로그인 후 경력·직무·관심 기술을 저장합니다.
2. 추천 기업에서 글을 선택하고 원문·목차·AI 읽기 가이드를 확인합니다.
3. 시연용 외부 수집 서버에서 관심 조건에 맞는 새 글을 전송합니다.
4. Blog가 Kafka 발행을 확인한 뒤 `202 Accepted`를 반환하고, Consumer가 글을 저장합니다.
5. 저장 완료 이벤트를 받은 User가 관심 사용자에게 알림을 저장합니다. 프론트 알림함을 열어 글을 확인하고 읽음 처리합니다.

```text
외부 수집 서버 → Gateway → Blog 수집 API
  → blog-post.requested.v1 → Blog 저장·커밋
  → blog-post.created.v1 → User 관심 조건 판별 → 알림 저장
```

`202`는 요청 접수 결과입니다. 글 저장과 알림 처리는 이후 진행됩니다. Blog는 URL 중복 저장을 막고 재처리에 같은 완료 이벤트 ID를 사용하며, User는 중복 이벤트의 알림 재생성을 방지합니다. 재시도·DLT 기반 처리이며 전체 구간의 Exactly-once 보장은 구현 범위에 포함하지 않습니다.

## 기술 구성

| 영역 | 기술 | 사용하는 목적 |
| --- | --- | --- |
| 언어·서버 | Java 17, Spring Boot 3.4.5, Spring Cloud 2024.0.2 | Gradle 멀티모듈 MSA |
| 플랫폼 | Eureka, Config, Gateway, OpenFeign, Spring Cloud Bus | 탐색·중앙 설정·라우팅·내부 통신·갱신 |
| 데이터 | MariaDB 11.4, Spring Data JPA | 환경별 인스턴스 안에서 서비스별 DB·계정 분리 |
| 이벤트 | Apache Kafka, Spring Kafka | 수집 요청·저장 완료·알림, 재시도·DLT |
| 인증·AI | Google 로그인, JWT, Gemini | 사용자 인증·개인화 가이드 |
| 배포·관측 | GitHub Actions, Docker, K3s, Argo CD, Prometheus, Grafana | 이미지·GitOps·배포·지표 수집 |
| 테스트 | JUnit 5, Spring Boot Test, H2, Embedded Kafka | 서비스 회귀·이벤트 파이프라인 검증 |

브라우저 요청은 frontend nginx → Gateway → 업무 서비스로 흐릅니다. Gateway가 JWT를 검증하고 외부 입력의 `userId`를 제거한 뒤 검증된 사용자 ID를 전달합니다. 서비스 내부 API에는 `X-Internal-Token`, 수집 API에는 `X-Collect-Key`를 사용합니다.

AI는 User 프로필과 Blog 원문·구간을 내부 API로 조합합니다. 가이드는 사용자·게시글·프로필 버전·프롬프트 버전을 기준으로 재사용합니다. Blog의 목록 조합과 User 북마크의 게시글 조회는 내부 조회 계약에 맞춰 최대 200개씩 배치합니다.

| 경로 | 책임 |
| --- | --- |
| `platform/` | Discovery·Config·Gateway, 공통 `bus-support` 라이브러리 |
| `services/` | User·AI·Blog 업무 서비스 |
| `database/`, `kafka/`, `monitoring/` | DB 이미지·스키마·토픽 도구·관측 구성 |
| `scripts/`, `.run/` | 로컬 준비·격리 검증·IDE 실행 구성 |
| `.github/workflows/` | 앱·MariaDB CI와 이미지 업로드 |
| `deploy/k8s/` | 데이터·관측 등 인프라 및 기존 배포 구성 |
| `config-repository-template/` | 설정 예제·검증 fixture. 실제 중앙 설정은 recu-config |
| `src/` | 기존 모놀리스. MSA 실행·검증은 모듈 경로를 명시 |

## 테스트 및 빌드

backend 루트에서 실행합니다.

```bash
bash ./gradlew :platform:discovery-server:test :platform:config-server:test :platform:api-gateway:test :platform:bus-support:test :services:user-service:test :services:ai-service:test :services:blog-service:test
bash ./gradlew :platform:discovery-server:bootJar :platform:config-server:bootJar :platform:api-gateway:bootJar :services:user-service:bootJar :services:ai-service:bootJar :services:blog-service:bootJar
python scripts/test_update_gitops_image.py
```

성공 기준은 명령 종료 코드 0과 각 모듈 `build/libs/<서비스>.jar` 생성입니다. 서비스 테스트는 H2·테스트 더블·임베디드 Kafka 등의 격리 구성을 사용하며, 실제 MariaDB·Gemini·클러스터 연동과는 검증 범위가 다릅니다.

이미지 빌드 예시:

```bash
docker build --platform linux/amd64 -t recu-userservice:local services/user-service
```

앱 Dockerfile은 빌드된 JAR을 복사하므로 `bootJar`가 먼저 필요합니다. MariaDB 이미지는 [별도 절차](database/README.md)를 사용합니다. 플랫폼 통합·암호화·Bus 검증은 [로컬 실행 안내](docs/MSA_LOCAL_GUIDE.md)와 [Config 안내](docs/CONFIG_SERVER_GUIDE.md)의 실행 조건을 확인합니다. 진단용 프로필의 health 성공은 업무 기능 검증을 대신하지 않습니다.

제공된 Blog 작업 기록에는 2026-11-03 테스트 31개와 별도의 User 북마크 배치 테스트 6개 통과, 실제 로컬 MariaDB·Kafka 연동 확인이 기록되어 있습니다. 이 수치는 해당 기록 기준이며 이번 README 작성에서 테스트를 새로 실행한 결과가 아닙니다.

## 배포 및 운영

```text
develop 대상 PR → 변경 앱 테스트·JAR·Docker 빌드
  → develop 병합 → Docker Hub 업로드 → GitOps dev 이미지 PR
  → 검토·main 병합 → Argo CD dev 자동 Sync → 상태·기능 확인
  → 검증한 동일 이미지의 prod 승격 PR → 병합 → prod 수동 Sync
```

- [Apps CI](.github/workflows/apps-ci.yml)는 실행 앱 6종 중 변경 영향이 있는 앱만 빌드합니다. `bus-support` 변경은 Discovery를 제외한 5종, 공통 Gradle 설정 변경은 6종에 영향을 줍니다.
- PR에서는 이미지 빌드까지만 수행합니다. `develop` push에서 전체 소스 커밋 SHA 태그의 `linux/amd64` 이미지를 업로드하고 서비스별 GitOps PR을 생성합니다.
- 이미지 주소는 `docker.io/junwooqkr5302/recu-<서비스명에서 하이픈 제거>:<전체 SHA>`이며, PR에 digest·소스 커밋·CI 링크를 남깁니다. 자동 병합은 하지 않습니다.
- Actions에는 `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `GITOPS_TOKEN` Secret과 `GITOPS_REPOSITORY`, `GITOPS_BASE_BRANCH` Variable을 사용합니다. GitOps 쓰기 토큰은 클러스터 배포 권한과 별개입니다.
- [MariaDB CI](.github/workflows/mariadb-image.yml)는 별도 변경 경로로 이미지 검증·업로드하며 앱 이미지와 같은 GitOps PR 자동 생성 단계는 없습니다.
- 앱 배포 선언은 [recu-gitops](https://github.com/LGCNS-MiniProject2-team6/recu-gitops), 중앙 설정은 [recu-config](https://github.com/LGCNS-MiniProject2-team6/recu-config)에서 관리합니다.
- 앱 이미지 롤백은 GitOps 이미지 선언을 이전 정상 조합으로 되돌리는 PR을 사용합니다. DB 스키마·데이터 복구는 별도입니다.

제공된 배포 보고는 dev/prod 앱 배포, prod `Synced / Healthy`, HTTPS·Google 로그인·기업 목록/상세·게시글 조회 성공을 기록합니다. 별도 팀 초안에는 dev AI·수집→알림 통합 확인도 기록되어 있습니다. 현재 운영 상태와 prod 전체 기능은 해당 환경에서 다시 확인해야 하며, 이번 문서 작업에서 클러스터·인증 세션을 재검증하지 않았습니다.

## 문제 해결

| 증상 | 확인할 내용 | 조치 |
| --- | --- | --- |
| 플랫폼만 뜨고 업무 서비스 기동 실패 | DB 설정, Config 읽기 인증, AI 키, Blog 스키마 | 공유 실행 구성과 서비스별 환경변수를 맞추고 MariaDB 준비 확인 |
| 로그인 후 기업 목록 503 | Eureka 등록, Blog→User 내부 계약, 이미지 조합·내부 토큰 | 개별 서비스 최신화만 하지 말고 dev에서 검증한 호환 이미지 조합 반영 |
| 내부 호출 401 | `INTERNAL_API_TOKEN`, 호출 경로별 인증 필터 | 같은 환경 토큰과 배포된 코드 버전 확인 |
| Pod 재시작·간헐적 500 | `OOMKilled`, 메모리 사용량·limit | 로그·지표로 원인 확인. 팀 기록에는 Blog limit 512Mi→768Mi 조치가 있음 |
| 수집 요청 거부 | Blog와 수집기의 `BLOG_COLLECT_API_KEY` | 동일 키를 실행 환경에 주입. 사용자 JWT·내부 토큰과 구분 |
| 설정 병합 후 실행값 유지 | Config label, Bus 연결, 갱신 가능한 속성인지 | Bus 갱신 또는 필요한 서비스 재시작 후 기능 확인 |

문의는 팀 `backend-issue` 포럼에 재현 절차·환경·비밀값을 제거한 로그를 남깁니다.

## 문서 및 협업

| 문서·저장소 | 내용 |
| --- | --- |
| [frontend](https://github.com/LGCNS-MiniProject2-team6/frontend) | 사용자 화면·로그인·알림 UI |
| [recu-config](https://github.com/LGCNS-MiniProject2-team6/recu-config) | 환경별 중앙 설정 |
| [recu-gitops](https://github.com/LGCNS-MiniProject2-team6/recu-gitops) | 배포 선언·이미지·Argo CD |
| [API 원천 안내](docs/api.md) | 팀 Notion API 명세서 위치. 실제 요청·응답 규격은 Notion 원본 참조 |
| [DB](database/README.md) · [Kafka](kafka/README.md) · [관측](monitoring/README.md) | 서비스별 데이터·토픽·지표 구성 |
| [Blog 이벤트](services/blog-service/BLOG_EVENT_PIPELINE.md) | 이벤트 계약·중복·재시도·검증 |
| [Gateway](docs/GATEWAY_GUIDE.md) · [Config](docs/CONFIG_SERVER_GUIDE.md) · [Bus](docs/SPRING_CLOUD_BUS_GUIDE.md) | 공통 플랫폼 연결·운영 |
| [GitOps 이미지 자동화](docs/GITOPS_DEV_IMAGE_AUTOMATION.md) | 이미지 PR 생성·Actions 설정 |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 브랜치·커밋·PR 규칙 |

코드 변경은 `develop` 대상 PR로 검토합니다. 커밋·PR 제목은 `type(scope): 설명`, 본문은 `수정사항`·`스크린샷`·`메모`를 따릅니다. API 규격 변경은 팀 이슈 포럼에서 먼저 논의하고 원본 명세와 맞춥니다. 기존 인계 문서에는 전환 중간 시점의 상태도 남아 있으므로 현재 코드·설정과 함께 확인합니다.

## 팀 및 문의

| 작업 영역 | 담당자 | 이번 MSA 전환 기여 |
| --- | --- | --- |
| Backend — User 서비스 | [@woomoo3](https://github.com/woomoo3) | 인증·프로필·북마크 분리, Kafka 관심 알림 Consumer·중복 처리·DLT |
| Backend — 알림 API | [@yhi9839](https://github.com/yhi9839) | 사용자 알림 목록 조회·읽음 처리 API |
| Backend — AI 서비스 | [@pdpdpdjjj23](https://github.com/pdpdpdjjj23) | 프로필·본문 내부 API 조합, Gemini 가이드·캐시·검증, 내부 API 보안 |
| Backend — Blog 서비스 | [@Kimoojin](https://github.com/Kimoojin) | Blog 분리·데이터 소유권, 내부 조회 연동, 수집→저장→완료 이벤트, 북마크 배치 개선·테스트 |
| Gateway 인증 연동 | [@pdpdpdjjj23](https://github.com/pdpdpdjjj23) | JWT 검증·사용자 ID 전달 필터·서비스 인증 연결 |
| DevOps 역할1 | [@yhi9839](https://github.com/yhi9839) | Discovery·Config·Gateway 라우팅·CORS·Bus 플랫폼 및 연결 검증 |
| DevOps 역할2 | [@Unpart](https://github.com/Unpart) | K3s 환경·노드 배치·Secret·앱 배포, Argo CD·GitOps 동기화·롤백, 외부 HTTPS 공개 |
| DevOps 역할3 | [@HYH0804](https://github.com/HYH0804) | MariaDB DB·계정 분리와 초기화 이미지, Kafka·토픽·DLT 도구, Prometheus·Grafana 구성 |
| DevOps 역할4 | [@junwoo-qkr](https://github.com/junwoo-qkr) | 앱·DB 이미지 빌드/업로드 CI, 변경 영향 감지, GitOps 이미지 PR 자동화·배포 인계 |
| Frontend 화면·알림 | [@yhi9839](https://github.com/yhi9839) | 기존 화면의 MSA 연동·반응형 개선, 알림함·읽음 처리·게시글 이동 |
| Frontend 이미지·배포 연결 | [@HYH0804](https://github.com/HYH0804) | 정적 이미지·nginx·GHCR CI, dev frontend·시연 수집기 배포 연결 |

담당 계정은 이번 MSA 전환의 병합 PR·커밋 이력 기준입니다. 역할은 작업 영역 기준이며 팀원 한 명이 여러 영역에 참여할 수 있습니다. 원래 모놀리스의 기능 개발과 이번 분리·연동 기여를 구분합니다.

## 향후 계획

- [ ] 실제 클러스터의 전체 기능·장애 복구·Git 기반 롤백 검증 기록 보강
- [ ] 단일 Kafka·MariaDB 구성의 백업·복구 및 확장 방안 정리
- [ ] 관측 알림 규칙·DLT 운영 절차 보강

## 라이선스

저장소에 라이선스 파일이 확인되지 않았습니다. 사용·재배포 조건은 팀에 문의하고 공개 배포 전에 확정합니다.
