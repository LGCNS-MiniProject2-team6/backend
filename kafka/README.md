# Kafka (#29)

Spring Cloud Bus(#15)와 이후 도메인 이벤트가 사용하는 Kafka 브로커 구성이다. 역할 3이 관리하며, Kubernetes 매니페스트는 역할 2가 아래 계약으로 작성한다.

```text
config/broker-dev.env    recu-dev 브로커 설정 (ConfigMap 원본)
config/broker-prod.env   recu-prod 브로커 설정
topics/topics.conf       환경별 토픽 목록
topics/create-topics.sh  토픽 생성 (반복 실행 안전)
scripts/verify-local.sh  로컬 Docker 검증
```

## 구성 결정

| 항목 | 값 | 이유 |
|---|---|---|
| 이미지 | `apache/kafka:4.3.2` | Apache 공식 이미지, KRaft 기본 지원 |
| 모드 | 단일 노드 KRaft (broker+controller 겸용) | data 노드 1대(t3.medium). ZooKeeper 불필요 |
| 환경 분리 | dev/prod 브로커 각 1개 | Namespace·노드 분리 기준과 동일 |
| 인증 | PLAINTEXT, 클러스터 내부 전용 | 외부 노출 없음. SASL은 필요 시 후속 |
| 토픽 생성 | 브로커 자동 생성 끔, 스크립트로만 생성 | 앱 binder도 auto-create=false |

## 역할 2 배포 계약

| 항목 | recu-dev | recu-prod |
|---|---|---|
| 워크로드 | StatefulSet `kafka`, replicas 1 | 동일 |
| nodeSelector | `workload: data`, `environment: dev` | `workload: data`, `environment: prod` |
| 컨테이너 포트 | 9092 (클라이언트), 9093 (controller, 내부 전용) | 동일 |
| Service | `kafka` ClusterIP, 9092만 노출 | 동일 |
| ConfigMap | `kafka-broker` ← `config/broker-dev.env` | `kafka-broker` ← `config/broker-prod.env` |
| Secret | 없음 (PLAINTEXT) | 없음 |
| PVC | `/var/lib/kafka/data`, 5Gi, `local-path` | 동일 |
| 리소스 | requests cpu 250m / mem 768Mi, limits cpu 1 / mem 1Gi | 동일 |
| readiness | exec `/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092`, timeout 10s | 동일 |
| liveness | tcpSocket 9092, initialDelay 30s | 동일 |

```bash
kubectl -n recu-dev create configmap kafka-broker --from-env-file=kafka/config/broker-dev.env
```
env 파일을 그대로 ConfigMap으로 만들고 Pod에는 `envFrom.configMapRef`로 주입한다.

주의 사항

- **`enableServiceLinks: false`를 지정한다.** Service 이름이 `kafka`면 K8s가 `KAFKA_PORT=tcp://...` 같은 환경변수를 자동 주입하고, 이미지가 이를 브로커 설정으로 해석한다.
- `KAFKA_ADVERTISED_LISTENERS`는 `kafka.<namespace>.svc.cluster.local:9092`다. Service 이름이나 Namespace를 바꾸면 env 파일도 같이 바꾼다. 앱은 bootstrap 이후 이 주소로 재접속하므로 틀리면 연결은 되는데 송수신이 실패한다.
- 컨테이너는 uid 1000(appuser)으로 실행된다. PVC 쓰기 권한이 없으면 `securityContext.fsGroup: 1000`을 준다.
- `CLUSTER_ID`는 데이터 디렉터리 최초 포맷에 기록된다. PVC를 유지한 채 바꾸면 기동 실패한다.
- `local-path` PVC는 해당 노드 디스크에 붙는다. data 노드 밖으로 옮기면 데이터가 따라가지 않는다.
- PVC 5Gi는 data 노드 EBS 용량 확인 전 임시값이다. MariaDB PVC와 합산해 노드 디스크를 넘지 않게 한다.

## 역할 1 연결값

Bus에 참여하는 다섯 앱(Config Server, Gateway, user, ai, blog)에 주입한다. 비밀값이 아니므로 ConfigMap으로 충분하다.

| 환경변수 | recu-dev | recu-prod |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka.recu-dev.svc.cluster.local:9092` | `kafka.recu-prod.svc.cluster.local:9092` |
| `KAFKA_SECURITY_PROTOCOL` | `PLAINTEXT` | `PLAINTEXT` |
| `BUS_ENABLED` | `true` | `true` |
| `BUS_DESTINATION` | `recu-config-bus-dev` | `recu-config-bus-prod` |

`KAFKA_SASL_MECHANISM`, `KAFKA_SASL_JAAS_CONFIG`는 주입하지 않는다.

## 토픽

`topics/topics.conf`가 기준이다. 형식은 `환경 토픽 파티션 복제수 retention.ms`이다.

| 토픽 | 파티션 | 복제 | 보관 |
|---|---:|---:|---|
| `recu-config-bus-dev` / `-prod` / `-local` | 1 | 1 | 1일 |

생성은 브로커 Pod 안에서 실행한다. 스크립트는 없으면 만들고, 있으면 건너뛰고, retention만 목록 값으로 맞춘다. 파티션 수가 다르면 경고만 하고 바꾸지 않는다.

```bash
kubectl -n recu-dev cp kafka/topics kafka-0:/tmp/topics
kubectl -n recu-dev exec kafka-0 -- sh /tmp/topics/create-topics.sh dev
```

**앱 배포 전에 실행한다.** 토픽이 없으면 Bus binder가 자동 생성하지 않아 다섯 앱이 기동에 실패한다.

도메인 이벤트 토픽(Mock 외부 서버 → blog 요청, blog → user 완료, 각 DLT)은 백엔드 토픽명·Message Key 확정 후 `topics.conf`에 줄을 추가한다.

## 로컬 검증

Docker만 있으면 된다. 클러스터에는 접근하지 않는다.

```bash
bash kafka/scripts/verify-local.sh
```

| 단계 | 확인 |
|---|---|
| 1 | `broker-dev.env`로 단일 노드 KRaft 기동, `meta.properties`의 node.id |
| 2 | `auto.create.topics.enable=false` |
| 3 | `create-topics.sh local` 2회 실행 후 토픽 상태 동일, 파티션 1, retention 1일 |
| 4 | advertised 주소(`kafka:9092`)로 produce/consume |

K8s DNS 대신 컨테이너 hostname을 advertised 주소로 써서 bootstrap 이후 재접속 경로를 같은 방식으로 검증한다. 실제 클러스터 네트워크·노드 배치 검증은 역할 2 배포 후 진행한다.
