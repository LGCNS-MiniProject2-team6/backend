# 관측 (#43)

Kafka·MariaDB·Spring 앱 지표를 Prometheus로 수집하고 Grafana로 본다. 역할 3이 관리하고, Kubernetes 배포는 역할 2가 아래 계약으로 진행한다. 앱 지표 노출은 역할 1 작업이 필요하다.

```text
prometheus/prometheus.yml                     수집 대상 (exporter 2개, Eureka 등록 앱)
grafana/provisioning/datasources/prometheus.yml   datasource 자동 등록
grafana/provisioning/dashboards/recu.yml      대시보드 폴더 자동 등록
grafana/dashboards/*.json                     ReCu Kafka / ReCu MariaDB / ReCu Spring Apps
scripts/verify-local.sh                       로컬 Docker 검증
```

## 구성 결정

| 항목 | 값 | 이유 |
|---|---|---|
| 실행 범위 | recu-dev만 | data 노드(t3.medium) 메모리·디스크 한계. prod는 dev 운영 후 판단 |
| 앱 수집 방식 | Prometheus `eureka_sd_configs` | 앱이 이미 Eureka에 Pod IP로 등록. K8s API 조회 권한(RBAC) 불필요 |
| Kafka 지표 | kafka-exporter | 토픽·파티션·consumer group lag. 브로커 JMX 설정 변경 불필요 |
| MariaDB 지표 | mysqld-exporter + 전용 계정 `exporter` | 서버 상태만 조회, 서비스 DB 데이터 조회 불가 |
| 보관 | 7일 또는 2500MB 중 먼저 도달 | PVC 3Gi 안에서 유지 |
| Alertmanager | 후속 | 알림 채널 미정 |

## 이미지

| 컴포넌트 | 이미지 | 포트 |
|---|---|---|
| Prometheus | `prom/prometheus:v3.15.1` | 9090 |
| Grafana | `grafana/grafana:13.2.5` | 3000 |
| kafka-exporter | `danielqsj/kafka-exporter:v1.11.0` | 9308 |
| mysqld-exporter | `prom/mysqld-exporter:v0.21.0` | 9104 |

전부 공개 이미지라 역할 4 빌드가 필요 없다. 설정 파일은 ConfigMap으로 넣는다.

## 역할 2 배포 계약 (recu-dev)

Service 이름은 `prometheus.yml`·datasource 주소와 일치해야 한다.

| 워크로드 | Service (ClusterIP) | 실행 인자 / 환경변수 | 마운트 |
|---|---|---|---|
| Deployment `kafka-exporter` | `kafka-exporter:9308` | `--kafka.server=kafka:9092` | 없음 |
| Deployment `mysqld-exporter` | `mysqld-exporter:9104` | `--mysqld.address=mariadb:3306`<br>`--mysqld.username=exporter`<br>env `MYSQLD_EXPORTER_PASSWORD` ← `mariadb-secret`의 `EXPORTER_DB_PASSWORD` | 없음 |
| StatefulSet `prometheus` | `prometheus:9090` | `--config.file=/etc/prometheus/prometheus.yml`<br>`--storage.tsdb.path=/prometheus`<br>`--storage.tsdb.retention.time=7d`<br>`--storage.tsdb.retention.size=2500MB` | ConfigMap → `/etc/prometheus/prometheus.yml`<br>PVC 3Gi → `/prometheus` |
| StatefulSet `grafana` | `grafana:3000` | env `GF_SECURITY_ADMIN_PASSWORD` ← `grafana-secret` | ConfigMap → `/etc/grafana/provisioning/datasources`<br>ConfigMap → `/etc/grafana/provisioning/dashboards`<br>ConfigMap → `/etc/grafana/dashboards`<br>PVC 1Gi → `/var/lib/grafana` |

ConfigMap은 `configMapGenerator`로 이 디렉터리 파일을 그대로 쓴다. 파일을 복사해 두면 내용이 어긋나므로 경로를 참조한다.

| ConfigMap | 원본 |
|---|---|
| `prometheus-config` | `prometheus/prometheus.yml` |
| `grafana-datasources` | `grafana/provisioning/datasources/prometheus.yml` |
| `grafana-dashboard-provider` | `grafana/provisioning/dashboards/recu.yml` |
| `grafana-dashboards` | `grafana/dashboards/*.json` |

배치·리소스

| 워크로드 | nodeSelector | requests | limits |
|---|---|---|---|
| prometheus | `workload: data`, `environment: dev` (PVC가 노드에 고정) | cpu 100m / mem 256Mi | cpu 500m / mem 512Mi |
| grafana | 동일 | cpu 50m / mem 128Mi | cpu 250m / mem 256Mi |
| kafka-exporter | 제한 없음 | cpu 10m / mem 32Mi | cpu 100m / mem 64Mi |
| mysqld-exporter | 제한 없음 | cpu 10m / mem 32Mi | cpu 100m / mem 64Mi |

PVC 합계 (dev data 노드 여유 16Gi 기준)

| 용도 | 크기 |
|---|---|
| Kafka | 5Gi |
| MariaDB | 5Gi |
| Prometheus | 3Gi |
| Grafana | 1Gi |
| 합계 | 14Gi (여유 약 2Gi, EBS 증설 불필요) |

Secret

| Secret | 키 | 용도 |
|---|---|---|
| `mariadb-secret` | `EXPORTER_DB_PASSWORD` (추가) | MariaDB 최초 기동 시 `exporter` 계정 생성, mysqld-exporter 접속 |
| `grafana-secret` | `GF_SECURITY_ADMIN_PASSWORD` | Grafana `admin` 비밀번호 |

```bash
kubectl -n recu-dev create secret generic grafana-secret \
  --from-literal=GF_SECURITY_ADMIN_PASSWORD="$(openssl rand -hex 16)"
```
값은 레포·채팅에 올리지 않는다.

주의 사항

- `EXPORTER_DB_PASSWORD`는 **MariaDB 최초 기동 전에** `mariadb-secret`에 들어 있어야 한다. init 스크립트는 빈 PVC에서 1회만 실행된다. 키가 없으면 계정 생성을 건너뛰고 MariaDB는 정상 기동한다 (prod는 생략 가능).
- 외부 노출은 하지 않는다. 확인은 `kubectl -n recu-dev port-forward svc/grafana 3000:3000`.
- Prometheus 설정 변경 후에는 Pod 재시작이 필요하다 (ConfigMap 해시가 바뀌면 자동 롤아웃).
- 기동 순서 제약 없음. exporter는 대상이 없으면 `up=0`으로 보이다가 대상이 뜨면 자동 수집된다.

배포 확인

```bash
kubectl -n recu-dev port-forward svc/prometheus 9090:9090
# http://localhost:9090/targets 에서 kafka, mariadb UP
```

## 역할 1 요청 (앱 지표)

현재 앱은 `micrometer-registry-prometheus` 의존성이 없고 actuator 노출이 `health,info`뿐이라 `spring-apps` 대상이 DOWN으로 보인다.

1. `platform/bus-support` (공통 모듈)에 의존성 추가
   ```gradle
   runtimeOnly 'io.micrometer:micrometer-registry-prometheus'
   ```
   bus-support를 쓰지 않는 앱(discovery-server 등)은 각자 추가한다.
2. recu-config 공통 설정에 노출 추가
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus
   ```
3. Gateway 라우트로 `/actuator/**`가 외부에 열리지 않는지 확인한다. Prometheus는 Pod IP로 직접 수집한다.

반영 후 Grafana `ReCu Spring Apps`에 앱별 HTTP·JVM·Hikari 지표가 나타난다.

## 로컬 검증

Docker만 있으면 된다. 비밀번호는 실행마다 임의 생성하고 끝나면 컨테이너를 지운다.

```bash
bash monitoring/scripts/verify-local.sh            # 검증 후 삭제
KEEP=1 bash monitoring/scripts/verify-local.sh     # 검증 후 남겨서 Grafana 화면 확인
bash monitoring/scripts/verify-local.sh stop       # 남긴 컨테이너 삭제
```

| 단계 | 확인 |
|---|---|
| 1 | MariaDB 이미지 빌드, `EXPORTER_DB_PASSWORD`로 기동 |
| 2 | `exporter` 계정이 서비스 DB를 보거나 조회하지 못함 |
| 3 | Kafka 기동, 테스트 토픽에 10건 발행·3건 소비 |
| 4 | exporter 2개, Prometheus, Grafana 기동 (K8s Service 이름과 같은 별칭) |
| 5 | `up{job="kafka"}`, `up{job="mariadb"}` = 1, `kafka_brokers` = 1, `mysql_up` = 1, consumer lag = 7 |
| 6 | Grafana datasource 연결 OK, 대시보드 3개 자동 등록 |
