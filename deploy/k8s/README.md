# Kubernetes 배포 뼈대

이 디렉터리는 역할 2의 K3s 배포 설정이다. 현재 `develop`의 서비스 계약을 기준으로
작성했으며, 실제 이미지 주소·태그와 DB/Kafka 계약이 오기 전에는 배포하지 않는다.

## 구성

```text
base/                 환경 공통 리소스
  platform/           discovery/config/gateway
  services/           user/ai/blog
  data/               Kafka (MariaDB·모니터링은 역할 3 추가 인계 후)
  ingress/            외부 Gateway 노출 예시(기본 배포 제외)
overlays/dev/         recu-dev + dev 노드
overlays/prod/        recu-prod + prod 노드
overlays/*-kafka/     앱 이미지 없이 Kafka만 먼저 배포하는 overlay
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

config-client-secrets
  CONFIG_USER, CONFIG_PASSWORD
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

## 검토 명령

```bash
kubectl kustomize deploy/k8s/overlays/dev
kubectl kustomize deploy/k8s/overlays/prod
```

검토가 끝나고 실제 이미지/Secret이 준비된 뒤에만 apply 한다.
