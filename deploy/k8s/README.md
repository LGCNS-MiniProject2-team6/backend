# Kubernetes 배포 뼈대

이 디렉터리는 역할 2의 K3s 배포 설정이다. 현재 `develop`의 서비스 계약을 기준으로
작성했으며, 실제 이미지 주소·태그와 DB/Kafka 계약이 오기 전에는 배포하지 않는다.

## 구성

```text
base/                 환경 공통 리소스
  platform/           discovery/config/gateway
  services/           user/ai/blog
  data/               Kafka, MariaDB
  ingress/            외부 Gateway 노출 예시(기본 배포 제외)
overlays/dev/         recu-dev + dev 노드
overlays/prod/        recu-prod + prod 노드
overlays/*-kafka/     앱 이미지 없이 Kafka만 먼저 배포하는 overlay
monitoring/           dev 전용 Prometheus, Grafana, exporter 배포 설정
```

각 overlay는 `workload`와 `environment`를 함께 사용한다. 이미지 값은
`registry.invalid` placeholder이므로 역할 4가 실제 레지스트리·태그를 확정한 뒤
`images` 변환으로 바꿔야 한다.

## 배포 전 반드시 확정할 것

- 역할 4: 이미지 레지스트리와 태그, Dockerfile/빌드 방식, CI/CD 실행 주체
- 역할 3: DB/Kafka/모니터링 매니페스트, 연결 Secret 키, PVC 용량
- 역할 2: 외부 도메인·TLS·IngressClass, 실제 측정 후 리소스 값 보정

Secret은 Git에 저장하지 않는다. Namespace별로 아래 Secret을 미리 만든다.

```text
config-server-secrets
  CONFIG_USER, CONFIG_PASSWORD
  CONFIG_GIT_USERNAME, CONFIG_GIT_TOKEN
  ENCRYPT_KEY
  CONFIG_ADMIN_USER, CONFIG_ADMIN_PASSWORD (관리 API 사용 시에만)

config-metrics-credentials
  CONFIG_METRICS_USER, CONFIG_METRICS_PASSWORD

config-client-secrets
  CONFIG_USER, CONFIG_PASSWORD

mariadb-secret
  MARIADB_ROOT_PASSWORD
  USER_DB_PASSWORD
  AI_DB_PASSWORD
  BLOG_DB_PASSWORD
  EXPORTER_DB_PASSWORD (dev 모니터링을 사용할 때만)

grafana-secret (recu-dev만)
  GF_SECURITY_ADMIN_PASSWORD

ai-secrets
  GEMINI_API_KEY (ai-service 필수. 없으면 기동 실패)
```

`ai-secrets`는 ai-service에만 주입한다. 키 값이 명령 기록에 남지 않도록 아래처럼 만든다.

```bash
read -rs GEMINI_KEY   # 입력 내용이 화면에 표시되지 않음
printf '%s' "$GEMINI_KEY" | sudo kubectl -n <namespace> create secret generic ai-secrets --from-file=GEMINI_API_KEY=/dev/stdin
unset GEMINI_KEY
```

`ENCRYPT_KEY`, Config Git 토큰, 관리자 인증은 Config Server에만 주입한다.
Gateway와 업무 서비스에는 `config-client-secrets`만 주입한다. Spring Cloud Bus 값은
`bus-runtime` ConfigMap으로 Config/Gateway/User/AI/Blog에만 주입하며, Eureka에는
주입하지 않는다.

## Kafka

`data/kafka.yaml`은 역할 3의 Kafka 계약을 반영한다. 환경별 `kafka-broker`
ConfigMap은 `kafka/config/broker-<env>.env`에서 생성되며, `kafka` StatefulSet은
각 환경의 `workload=data` 노드에서만 실행된다. `enableServiceLinks: false`는
`apache/kafka` 이미지에 필수다.

Kustomize는 overlay 바깥의 env 파일을 읽지 못하므로, 각 overlay에 같은 환경의
`kafka-broker.env` 사본을 둔다. Kafka 원본 설정을 바꿀 때는 해당 사본도 같이 갱신한다.

Kafka를 먼저 배포하고 Ready가 되면 역할 3이 Bus 토픽을 생성한다. 토픽 생성 전에는
앱을 배포하거나 `BUS_ENABLED=true`로 바꾸지 않는다.

```bash
# 앱 manifest는 포함하지 않는다. 먼저 dev Kafka만 배포한다.
kubectl apply -k deploy/k8s/overlays/dev-kafka
kubectl -n recu-dev rollout status statefulset/kafka
```

## MariaDB와 dev 모니터링

`data/mariadb/`는 `junwooqkr5302/recu-mariadb:cd63175`를 사용한다. 한 MariaDB
인스턴스 안에 `user_service`, `ai_service`, `blog_service` 논리 DB를 생성한다. 빈 PVC로
처음 기동할 때만 초기화 스크립트가 실행되므로, `mariadb-secret`은 반드시 먼저 생성한다.

Prometheus, Grafana, Kafka Exporter, MariaDB Exporter는 dev에만 배포한다. 설정 원본과
배포 매니페스트를 같은 `monitoring/` Kustomize 루트에 두어 ConfigMap 파일을 복사하지
않는다.

```bash
# MariaDB는 dev/prod 공통 base에 포함된다. 앱 이미지가 준비되기 전에는 아래처럼
# 전체 overlay를 apply하지 말고, MariaDB 전용 overlay만 적용한다.
kubectl apply -k deploy/k8s/overlays/dev-mariadb
kubectl -n recu-dev rollout status statefulset/mariadb --timeout=5m

# MariaDB가 Ready가 된 뒤 dev 모니터링을 배포한다.
kubectl apply -k monitoring
```

Grafana와 Prometheus는 외부 공개하지 않는다. 확인이 필요할 때만 port-forward를 쓴다.

```bash
kubectl -n recu-dev port-forward svc/grafana 3000:3000
kubectl -n recu-dev port-forward svc/prometheus 9090:9090
```

## 검토 명령

```bash
kubectl kustomize deploy/k8s/overlays/dev
kubectl kustomize deploy/k8s/overlays/prod
kubectl kustomize monitoring
```

검토가 끝나고 실제 이미지/Secret이 준비된 뒤에만 apply 한다.
