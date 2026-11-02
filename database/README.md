# MariaDB (#31)

user·ai·blog 서비스의 DB를 한 MariaDB 인스턴스 안에서 분리(Database per Service)한 커스텀 이미지다. 역할 3이 관리하고, 이미지 빌드·push는 역할 4, Kubernetes 배포는 역할 2가 아래 계약으로 진행한다.

```text
Dockerfile               mariadb:11.4 기반 이미지 (빌드 컨텍스트: 레포 루트)
Dockerfile.dockerignore  빌드에 필요한 파일만 전송
conf/recu.cnf            문자셋 utf8mb4, skip-name-resolve
init/01-databases.sh     DB 3개 + 서비스 계정 3개 + 권한, 지표 수집 계정(선택)
init/10-blog-schema.sql  blog_service 테이블 6개
init/20-ai-schema.sql    ai_service.ai_guide
scripts/verify-local.sh  로컬 Docker 검증
```

`11-blog-seed.sql`은 레포에 없다. 이미지 빌드 시 `mini8-content-dump.sql`에서 데이터 구간만 추출해 만든다.

## 구성 결정

| 항목 | 값 | 이유 |
|---|---|---|
| 이미지 | `mariadb:11.4` (LTS) | 덤프 생성 클라이언트와 같은 11.4, 기존 collation `utf8mb4_uca1400_ai_ci` 지원 |
| 분리 방식 | 인스턴스 1개, DB 3개, 계정 3개 | data 노드 1대(t3.medium). 계정은 자기 DB에만 권한 |
| 비밀번호 | 이미지에 넣지 않음, 실행 시 Secret 주입 | 공개 레포 |
| 스키마 관리 | 서비스별 init 파일 | 스키마가 바뀌면 해당 파일만 수정 |

| DB | 계정 | 테이블 생성 주체 | 서비스 `ddl-auto` |
|---|---|---|---|
| `user_service` | `user_service` | user-service JPA | `update` |
| `ai_service` | `ai_service` | `init/20-ai-schema.sql` | `none` |
| `blog_service` | `blog_service` | `init/10-blog-schema.sql` + 덤프 데이터 | `validate` |

DB 이름은 ai-service 코드의 `${DB_URL}/ai_service`에 맞춰 `*_service`로 통일했다.

## 역할 4 빌드

```bash
docker build -f database/Dockerfile -t <registry>/recu-mariadb:<tag> .
```

- **레포 루트에서** 실행한다. 덤프 파일(`mini8-content-dump.sql`)이 루트에 있다.
- BuildKit 필요 (`COPY --chmod`, `Dockerfile.dockerignore`). Docker 23 이상은 기본값이다.
- 빌드 트리거 경로: `database/**`, `mini8-content-dump.sql`

## 역할 2 배포 계약

| 항목 | recu-dev | recu-prod |
|---|---|---|
| 워크로드 | StatefulSet `mariadb`, replicas 1 | 동일 |
| 이미지 | 역할 4가 push한 `recu-mariadb` | 동일 |
| nodeSelector | `workload: data`, `environment: dev` | `workload: data`, `environment: prod` |
| 포트 | 3306 | 동일 |
| Service | `mariadb` ClusterIP, 3306 | 동일 |
| Secret | `mariadb-secret` (아래 키 4개 + 선택 1개) | 동일 키, 값은 dev와 다르게 |
| PVC | `/var/lib/mysql`, 5Gi, `local-path` | 동일 |
| 리소스 | requests cpu 250m / mem 512Mi, limits cpu 1 / mem 1Gi | 동일 |
| readiness | exec `healthcheck.sh --connect --innodb_initialized` | 동일 |
| liveness | exec `healthcheck.sh --connect`, initialDelay 60s | 동일 |

Secret 키 (Pod에 `envFrom.secretRef`로 주입)

| 키 | 용도 |
|---|---|
| `MARIADB_ROOT_PASSWORD` | root. 운영 작업용, 서비스에 주지 않음 |
| `USER_DB_PASSWORD` | `user_service` 계정 |
| `AI_DB_PASSWORD` | `ai_service` 계정 |
| `BLOG_DB_PASSWORD` | `blog_service` 계정 |
| `EXPORTER_DB_PASSWORD` | (선택) `exporter` 계정. mysqld-exporter용, 서버 상태만 조회 (`monitoring/README.md`) |

```bash
kubectl -n recu-dev create secret generic mariadb-secret \
  --from-literal=MARIADB_ROOT_PASSWORD=... \
  --from-literal=USER_DB_PASSWORD=... \
  --from-literal=AI_DB_PASSWORD=... \
  --from-literal=BLOG_DB_PASSWORD=... \n  --from-literal=EXPORTER_DB_PASSWORD=...
```
값은 레포·채팅에 올리지 않는다. 필수 4개 중 하나라도 비어 있으면 최초 기동이 실패한다. `EXPORTER_DB_PASSWORD`가 없으면 `exporter` 계정만 생략한다.

주의 사항

- **init 스크립트는 PVC가 비어 있을 때 1회만 실행된다.** 이후 Secret 비밀번호를 바꿔도 DB 계정 비밀번호는 바뀌지 않는다. 스키마 변경도 같다.
- dev에서 스키마·시드를 다시 적용하려면 PVC를 지우고 재기동한다. prod는 실데이터가 생긴 뒤 별도 변경 스크립트로 반영한다.
- 최초 기동은 blog 데이터 적재로 1분 안팎 걸린다. readiness 실패 횟수를 넉넉히 둔다.
- `local-path` PVC는 노드에 고정된다. PVC 5Gi는 data 노드 EBS 용량 확인 전 임시값이며 Kafka PVC와 합산해 확정한다.

## 서비스 연결값

DB 환경변수 이름이 서비스마다 다르다. 각 서비스 코드 기준으로 넣는다. `PASSWORD`는 `mariadb-secret`의 해당 키를 `secretKeyRef`로 참조한다.

호스트: `mariadb.recu-dev.svc.cluster.local:3306` (prod는 `recu-prod`)

| 서비스 | 환경변수 | recu-dev 값 |
|---|---|---|
| user | `SPRING_DATASOURCE_URL` | `jdbc:mariadb://mariadb.recu-dev.svc.cluster.local:3306/user_service` |
| | `SPRING_DATASOURCE_DRIVER_CLASS_NAME` | `org.mariadb.jdbc.Driver` (기본값이 H2라 필수) |
| | `SPRING_DATASOURCE_USERNAME` | `user_service` |
| | `SPRING_DATASOURCE_PASSWORD` | Secret `USER_DB_PASSWORD` |
| ai | `DB_URL` | `jdbc:mariadb://mariadb.recu-dev.svc.cluster.local:3306` (**DB 이름 없이**, 코드가 `/ai_service`를 붙임) |
| | `DB_USER` | `ai_service` |
| | `DB_PASSWORD` | Secret `AI_DB_PASSWORD` |
| blog | `BLOG_DB_URL` | `jdbc:mariadb://mariadb.recu-dev.svc.cluster.local:3306/blog_service` |
| | `BLOG_DB_USER` | `blog_service` |
| | `BLOG_DB_PASSWORD` | Secret `BLOG_DB_PASSWORD` |

기준 브랜치: user `feat/24/user-service-api`, ai `refactor/1/ai-service`, blog `refactor/26/blog-service-separation`. 머지 후 변수명이 바뀌면 이 표를 갱신한다.

## 스키마 변경

| 대상 | 수정 파일 |
|---|---|
| blog 테이블 | `init/10-blog-schema.sql` (blog-service 엔티티와 일치해야 `validate` 통과) |
| blog 초기 데이터 | `mini8-content-dump.sql` 또는 Dockerfile 추출 규칙 |
| ai 테이블 | `init/20-ai-schema.sql` |
| user 테이블 | 없음 (JPA 자동 생성) |

수정 후 `verify-local.sh`로 확인하고 이미지를 다시 빌드한다. 초기 데이터 범위(시드 vs Mock 서버 스트리밍 분량)는 확정 전까지 덤프 전체를 넣는다.

## 로컬 검증

Docker만 있으면 된다. 비밀번호는 실행마다 임의 생성하고 끝나면 컨테이너를 지운다.

```bash
bash database/scripts/verify-local.sh
```

| 단계 | 확인 |
|---|---|
| 1~2 | 이미지 빌드, 이미지 ENV에 비밀번호 없음 |
| 3 | 빈 볼륨 최초 기동, healthcheck 통과 |
| 4 | DB 3개, 계정 3개 |
| 5 | 서비스 계정이 자기 DB에서 DDL 가능, 다른 DB 접근 불가 |
| 6 | blog 테이블 6개 데이터 (company 8, tech_tag 53, blog_post 382, section 4706, tag 527, category 517) |
| 7~8 | `ai_guide` 존재, `user_service` 빈 DB |
