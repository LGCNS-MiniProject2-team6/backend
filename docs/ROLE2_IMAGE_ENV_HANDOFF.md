# 역할 2 전달용 앱 이미지·배포 정보

작성일: 2026-11-02
대상: EC2 x86_64 기반 K3s, DEV `recu-dev` / PROD `recu-prod`

현재 로컬 저장소에서 확인한 사실과 전달 전에 확정할 항목을 구분했다. 실제 비밀번호·토큰 값은 포함하지 않는다.

## 1. 앱 6개의 전체 이미지 주소

로컬 Docker 이미지와 Docker Hub의 해당 태그 manifest를 직접 확인했다. `recu-mariadb`는 확인 대상에서 제외했다.

| 앱 | 전체 이미지 주소 | 포트 | linux/amd64 |
|---|---|---:|---|
| discovery-server | `docker.io/junwooqkr5302/recu-discoveryserver:000c6c0` | 8761 | 확인 |
| config-server | `docker.io/junwooqkr5302/recu-configserver:000c6c0` | 8888 | 확인 |
| api-gateway | `docker.io/junwooqkr5302/recu-apigateway:000c6c0` | 8080 | 확인 |
| user-service | `docker.io/junwooqkr5302/recu-userservice:000c6c0` | 8082 | 확인 |
| ai-service | `docker.io/junwooqkr5302/recu-aiservice:000c6c0` | 8083 | 확인 |
| blog-service | `docker.io/junwooqkr5302/recu-blogservice:000c6c0` | 8084 | 확인 |

각 이미지에는 동일한 이미지 ID를 가진 `recu-<이름>:local` 로컬 태그도 있다. K3s 배포에는 위 Docker Hub 전체 주소를 사용한다. 이미지 이름은 `recu-discoveryserver`처럼 붙여 쓴다.

## 2. Prometheus 지표 설정 — PR #53 포함 여부

**6개 이미지 모두 PR #53의 Prometheus 설정이 포함된 JAR로 빌드되어 있다.**

확인 근거:

- 이미지 태그 `000c6c0`는 PR #53 병합 커밋이다.
- 이미지 생성 시각은 2026-11-02 15:17~15:18 KST다.
- 각 이미지의 `/app/app.jar`를 추출해 검사했다.
- 6개 모두 이미지 내부 JAR과 현재 모듈의 `build/libs` JAR의 SHA-256이 일치했다.
- 6개 모두 이미지 내부 `application.yml`이 현재 PR #53 반영 소스와 바이트 단위로 일치했다. 해당 설정에는 Prometheus endpoint 노출이 포함된다.
- 6개 모두 JAR 내부에 `micrometer-registry-prometheus` 라이브러리가 포함되어 있다.

위 내용은 이미지 내부 검사 결과다. 실제 K3s에서의 `/actuator/prometheus` 응답 및 Prometheus 수집 성공 여부는 배포 후 확인한다. Config Server는 지표 수집 인증 설정도 맞춰야 한다.

## 3. 레지스트리 공개 / 비공개 여부

**6개 앱 이미지 모두 Docker Hub에서 공개 pull 가능하다.**

Docker 로그인 정보를 사용하지 않고 익명 pull 토큰으로 각 저장소의 `000c6c0` 태그 manifest 조회에 성공했다. 따라서 현재 앱 이미지 접근을 위한 K3s `imagePullSecret`은 필수가 아니다.

확인한 레지스트리는 `docker.io`, 계정은 `junwooqkr5302`다. 향후 저장소를 비공개로 바꾸면 `recu-dev`와 `recu-prod` 각각에 pull 인증 Secret을 준비하고 Pod의 `imagePullSecrets`에 연결해야 한다.

## 4. linux/amd64 지원 여부

**6개 앱 이미지 모두 `linux/amd64`를 지원한다. 현재 EC2/K3s x86_64 노드에 맞는 플랫폼이다.**

로컬 `docker image inspect`에서 6개 모두 `Os=linux`, `Architecture=amd64`를 확인했다. Docker Hub의 각 태그 manifest에도 `linux/amd64` 항목이 있음을 확인했다.

## 5. 실행에 필요한 환경변수 / Secret 키

### 일반 환경변수 — 키 이름

| 대상 | 키 |
|---|---|
| 전체 | `SERVER_PORT` — 기본 포트를 사용하면 생략 가능 |
| Gateway·User·AI·Blog | `SPRING_PROFILES_ACTIVE`, `CONFIG_SERVER_URL`, `CONFIG_GIT_LABEL` |
| Config | `SPRING_PROFILES_ACTIVE`, `CONFIG_GIT_URI`, `CONFIG_GIT_LABEL` |
| Config·Gateway·User·AI·Blog | `EUREKA_URL` |
| Gateway | `FRONTEND_ORIGIN` |
| Bus 사용 시 Config·Gateway·User·AI·Blog | `BUS_ENABLED`, `BUS_DESTINATION`, `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_SECURITY_PROTOCOL` |
| Kafka SASL 인증 사용 시 | `KAFKA_SASL_MECHANISM`, `KAFKA_SASL_JAAS_CONFIG` — JAAS 인증 값은 Secret으로 관리 |

Discovery에는 Config·DB·Bus 설정이 필요하지 않다. DEV/PROD 프로필과 Bus 토픽은 환경별로 분리한다. Kafka 및 토픽 준비 후 Bus를 활성화한다.

### Secret 키 이름

| Secret | 키 | 대상 |
|---|---|---|
| `config-server-secrets` | `CONFIG_USER`, `CONFIG_PASSWORD`, `ENCRYPT_KEY` | Config |
| `config-server-secrets` | `CONFIG_GIT_USERNAME`, `CONFIG_GIT_TOKEN` | Config의 비공개 HTTPS 설정 저장소 접근 |
| `config-server-secrets` | `CONFIG_ADMIN_USER`, `CONFIG_ADMIN_PASSWORD` | Config 관리 기능 사용 시 두 키 모두 필요 |
| `config-client-secrets` | `CONFIG_USER`, `CONFIG_PASSWORD` | Gateway·User·AI·Blog |

Config Server와 클라이언트의 인증정보는 같은 환경 안에서 일치해야 한다. `ENCRYPT_KEY`는 설정 저장소 암호문에 맞는 키를 사용한다. Git 토큰·암호화 키·관리자 인증은 Config Server에만 주입한다.

### DB·JWT·AI 키 적용 범위

현재 앱 모듈은 시작 클래스 중심의 구성이다. `docs/DOCKER_BUILD_GUIDE.md`는 업무 모듈에 DB/JPA 의존성이 없으며, 기존 루트 모놀리스의 DB·JWT·AI 키 요구사항과 구분하도록 명시한다. 따라서 구성도에 DB가 있다는 이유만으로 현재 앱 이미지에 DB·JWT·AI 키가 모두 필수라고 단정하지 않는다.

다만 현재 K3s 배포 파일은 다음 DB 변수를 이미 주입하고 있다.

| 대상 | 현재 배포 파일의 DB 키 |
|---|---|
| 공통 `db-runtime` | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_DRIVER_CLASS_NAME`, `SPRING_DATASOURCE_USERNAME`, `DB_URL`, `DB_USER`, `BLOG_DB_URL`, `BLOG_DB_USER` |
| User | `SPRING_DATASOURCE_PASSWORD` ← `mariadb-secret`의 `USER_DB_PASSWORD` |
| AI | `DB_PASSWORD` ← `mariadb-secret`의 `AI_DB_PASSWORD` |
| Blog | `BLOG_DB_PASSWORD` ← `mariadb-secret`의 `BLOG_DB_PASSWORD` |

현재 manifest를 그대로 사용하면 위 Secret 참조도 준비해야 한다. 공통 설정은 User용 URL·계정과 AI·Blog용 별도 키를 함께 포함한다. 실제 전달 이미지에서 어떤 키를 소비하는지 서비스 담당자와 확인한 뒤 계약을 확정한다.

이전 문서의 AI·Blog 비밀번호를 `SPRING_DATASOURCE_PASSWORD`로 통일하는 내용은 제안이었다. 실제 코드 확인 없이 필수 변경사항으로 적용하지 않는다.

## 6. CI/CD 배포 방식

**현재 로컬 저장소 기준: 앱 6개의 자동 빌드·push·K3s 배포 workflow는 확인되지 않았다. 최종 방식은 미확정이다.**

현재 `.github/workflows`에는 다음 파일이 있다.

- `check-source-branch.yml`: 브랜치 정책 확인
- `mariadb-image.yml`: MariaDB 이미지 빌드·검증 및 조건부 Docker Hub push

MariaDB용 workflow는 앱 6개의 CI/CD 구현을 의미하지 않는다.

앱 배포 방식 확정 시 아래 항목을 역할 2와 합의한다.

- 빌드·배포 대상 브랜치와 실행 조건
- 앱별 빌드·이미지 push 및 태그 규칙
- GitHub Actions에서 K3s에 접근하는 실행 주체·인증·네트워크 경로
- DEV/PROD 배포 승인 및 롤백 방식

## 7. Kustomize 태그 변경 / kubectl set image 중 최종 방식

**현재 확정된 구현은 없다.** 현 배포 구조가 Kustomize이므로 다음 방식을 제안한다.

1. GitHub Actions에서 대상 앱의 실행 JAR을 생성하고 이미지를 빌드·push한다.
2. 빌드 커밋 기반의 고정 이미지 태그를 사용한다.
3. 해당 환경 overlay의 Kustomize `images`에서 이미지 주소·태그를 갱신하고 Git에 기록한다.
4. 합의한 배포 실행 주체가 해당 overlay를 `kubectl apply -k`로 적용한다.
5. 배포된 이미지와 rollout 결과를 확인한다.

제안은 **Kustomize 이미지 태그 갱신 + apply 방식**이다. `kubectl set image`는 현재 구현된 최종 방식이 아니다. 이 제안은 아직 workflow나 manifest에 반영하지 않았다.

## 확인 범위

- 로컬 Docker에서 `recu-mariadb`를 제외한 recu 앱 이미지 6개를 직접 조회했다.
- 이미지별 전체 태그, 생성 시각, OS·아키텍처를 확인했다.
- 각 이미지 내부 JAR의 Prometheus 라이브러리와 설정을 검사하고 로컬 빌드 JAR의 SHA-256과 비교했다.
- Docker Hub에 로그인 없이 각 태그 manifest를 조회하여 공개 접근과 amd64 지원을 확인했다.
- CI/CD 현황은 현재 로컬 `.github/workflows`와 Kustomize 파일 기준이다. 별도 저장소나 원격 미반영 workflow까지 확인한 결과는 아니다.
- 클러스터 배포·컨테이너 기동·지표 수집 동작은 이번 확인 범위에 포함하지 않았다.
- `.env`는 읽거나 수정하지 않았다. 배포 workflow와 manifest도 변경하지 않았다.
