# 역할 1 → 역할 2·4 배포 인계

[문서 목차](README.md) · [확정이 필요한 항목](REVIEW_CHECKLIST.md)

## 배포 대상과 확정 범위

현재 소스는 플랫폼 3종 + User(인증 포함)·AI·Blog 3종이다. 환경마다 replicas=1이면 기본 앱 Pod는 dev 6 + prod 6 = 12개다. DB/Kafka/모니터링·시스템·롤링 업데이트 추가 Pod는 별도다. Content/Guide/Notification 별도 앱 계획은 현재 코드와 다르며 알림은 User에 포함한다.

역할 2가 전달한 노드는 공용 control-plane 1 + dev(platform/service/data) 3 + prod 3이다. 서버에 직접 접속해 확인한 정보는 아니다. 노드 분리만으로 통신 격리가 완성되지는 않으며 Namespace/RBAC/NetworkPolicy·Secret/PVC 분리와 환경 간 차단 검증이 필요하다.

아래 **포트·환경변수·health 경로는 코드 기준**이고 Service/Deployment/컨테이너/Secret 오브젝트 이름·자원 수치는 제안이다. 이미지·Kafka·Ingress의 실제 미확정값은 임의로 채우지 않는다.

| 앱 이름 | 소스 모듈 | 컨테이너 포트 | Service·Deployment·컨테이너 이름 제안 | Service 포트 제안 |
|---|---|---:|---|---:|
| discovery-server | platform/discovery-server | 8761 | discovery-server | 8761 |
| config-server | platform/config-server | 8888 | config-server | 8888 |
| api-gateway | platform/api-gateway | 8080 | api-gateway | 8080 |
| user-service | services/user-service | 8082 | user-service | 8082 |
| ai-service | services/ai-service | 8083 | ai-service | 8083 |
| blog-service | services/blog-service | 8084 | blog-service | 8084 |

플랫폼의 bootJar task는 :platform:{앱이름}:bootJar, JAR 위치는 platform/{앱이름}/build/libs/{앱이름}.jar다. Java 17을 사용한다. 기존 루트 Dockerfile은 모놀리스 8000용이며 플랫폼 3종 Dockerfile·이미지 경로는 역할 4 확정 필요다. 제안 ECR repository 이름은 recu-discovery-server/recu-config-server/recu-api-gateway지만 registry 전체 주소와 생성 여부는 미확정이다.

## Namespace·노드·이미지

Namespace는 recu-dev/recu-prod. 각 환경에서 플랫폼은 workload=platform, 업무 앱은 workload=service, 데이터는 workload=data다. **environment 라벨도 함께** 지정한다. 일반 앱을 control-plane에 배치하는 toleration은 추가하지 않는다.

```yaml
nodeSelector:
  workload: platform
  environment: dev # prod overlay에서는 prod
```

환경별 이미지를 다시 만들지 않고 dev에서 검증한 같은 SHA tag/digest를 prod로 승격한다. Secret·ConfigMap·프로필·배치만 환경별로 달라진다. 배포할 이미지 아키텍처·tag 형식·digest·Deployment 이름·runner 위치는 역할 2·4가 합의한다.

## ConfigMap 환경변수

Service 이름을 위 제안대로 채택하면 같은 Namespace의 짧은 DNS를 사용한다. Pod의 localhost는 다른 앱 주소가 아니다.

| 변수 | 대상 | dev/prod 값 또는 결정자 |
|---|---|---|
| SERVER_PORT | 각 앱 | 위 앱별 포트 |
| SPRING_PROFILES_ACTIVE | 각 앱 | dev / prod |
| EUREKA_URL | Config·Gateway·업무 앱 | http://discovery-server:8761/eureka/ |
| CONFIG_SERVER_URL | Gateway·업무 앱 | http://config-server:8888 |
| CONFIG_GIT_URI | Config만 | https://github.com/LGCNS-MiniProject2-team6/recu-config.git |
| CONFIG_GIT_LABEL | Config·클라이언트 | 현재 main; 운영 고정 SHA/tag는 별도 합의 |
| FRONTEND_ORIGIN | Gateway만 | 환경별 실제 프론트 Origin, 프론트/역할 2 확정 |
| BUS_ENABLED | Config·Gateway·업무 앱 | Kafka 준비 전 false, 이후 모두 true |
| BUS_DESTINATION | 같은 Bus 참여 앱 | dev recu-config-bus-dev / prod recu-config-bus-prod |
| KAFKA_BOOTSTRAP_SERVERS | 같은 Bus 참여 앱 | 역할 3의 실제 주소 |
| KAFKA_SECURITY_PROTOCOL / KAFKA_SASL_MECHANISM | 필요 앱 | 역할 3 확정; 코드 기본값을 운영 확정값으로 보지 않음 |

Discovery는 Bus에 참여하지 않고 Config/Git/Kafka 인증도 필요 없다. Config는 자신의 Git을 Config Client로 읽지 않으므로 CONFIG_SERVER_URL·FRONTEND_ORIGIN이 불필요하며 BUS_DESTINATION은 직접 주입한다. 선택적 BUS_INSTANCE_ID는 기본 앱이름:HOSTNAME:서버포트이며 인스턴스별 고유성을 유지한다.

## Secret 키와 주입 대상

키 이름은 코드 계약이며 Secret 이름은 제안이다. 각 Namespace에 따로 만들고 실제 값은 공개하지 않는다.

| Secret 이름 제안 | 키 | 대상 |
|---|---|---|
| config-reader-credentials | CONFIG_USER, CONFIG_PASSWORD | Config·Gateway·User·AI·Blog |
| config-git-credentials | CONFIG_GIT_USERNAME, CONFIG_GIT_TOKEN | Config만 |
| config-encryption | ENCRYPT_KEY | Config만, 필수 |
| config-bus-admin | CONFIG_ADMIN_USER, CONFIG_ADMIN_PASSWORD | Config만, 암복호화·Bus 관리 API 사용 시 |
| kafka-client-credentials | KAFKA_SASL_JAAS_CONFIG | SASL 사용 시 다섯 Bus 참여 앱 |

- ENCRYPT_KEY 누락·빈 값·미해결 placeholder면 Config 기동을 거부한다. **새 이미지 rollout 전에 Secret을 준비**한다.
- 관리자는 기존 Bus 계정을 재사용하고 두 변수를 함께 지정한다. 읽기 계정과 아이디가 같으면 기동 실패한다. 둘 다 생략해도 키가 있으면 암호문 설정 제공은 가능하지만 관리 API는 호출할 수 없다.
- dev/prod 키·자격증명을 분리하고 각 환경 YAML의 암호문과 대응시킨다. Pod 재시작마다 키를 바꾸지 않는다. 같은 환경의 Config replica는 같은 키를 쓴다.
- Gateway·업무 앱에 Git 토큰·관리자·암호화 키를 전달하지 않는다. Secret 하나에 전부 묶어 모든 앱에 envFrom하지 않는다.
- [암호화 patch](../platform/config-server/k8s/encryption-env.patch.yaml)는 기존 관리자 참조를 유지하며 ENCRYPT_KEY만 추가한다. 실제 Namespace·Deployment·컨테이너 이름을 맞춰 사용한다. 단독 apply할 완성 Deployment가 아니다.
- 이미지 pull 인증은 역할 4·2가 별도로 정하며 CONFIG_GIT_TOKEN과 무관하다.

## Health·Probe·자원

모든 앱은 앱 포트에서 무인증 health를 제공한다. Config도 health 하위 경로는 허용한다. HTTP 200과 준비/생존 상태를 확인한다.

| 용도 | 경로 |
|---|---|
| 수동 종합 진단 | /actuator/health |
| startupProbe 제안·livenessProbe | /actuator/health/liveness |
| readinessProbe | /actuator/health/readiness |

초기 제안은 startup period=5초/timeout=3초/failure=60, readiness 5/3/3, liveness 10/3/3이다. 실제 기동 시간과 노드 자원으로 조정한다. 종합 health에 외부 의존 장애가 반영될 수 있으므로 liveness로 쓰지 않는다. readiness 기본 그룹만으로 Git/Kafka 기능 성공을 보장하지 않는다.

| 앱 | CPU request/limit 초기 제안 | 메모리 request/limit 초기 제안 |
|---|---|---|
| Discovery | 250m / 1000m | 256Mi / 512Mi |
| Config·Gateway 각각 | 250m / 1000m | 512Mi / 768Mi |
| User·AI·Blog 각각 | 250m / 1000m | 512Mi / 768Mi |

이 수치는 실측 최소값·운영 성능 보장이 아니다. 역할 2가 allocatable·시스템 여유·롤아웃 추가 Pod, 역할 4가 JVM heap 외 native/metaspace를 고려한다. 플랫폼 세 앱은 현재 DB/PVC가 필요 없으며 Config Git clone용 쓰기 가능한 임시 공간은 필요하다.

## Gateway·네트워크·기동

- Gateway Service는 ClusterIP + Ingress를 제안한다. 컨테이너 포트 8080과 외부 접속 포트를 구분하며 외부 port/host/IngressClass/TLS는 역할 2 확정 사항이다.
- 업무 경로는 [Gateway 표](GATEWAY_GUIDE.md)를 따른다. 외부 /actuator/**·/encrypt·/decrypt·Eureka/Config 관리는 차단한다. Gateway의 / 전체를 무조건 전달하지 않는다.
- Gateway는 Eureka가 반환한 실제 업무 Pod IP/포트로 접근한다. Config의 GitHub HTTPS/DNS egress와 앱들의 내부 Config/Eureka/Kafka 연결을 허용한다.
- Kafka bootstrap뿐 아니라 advertised.listeners의 broker 주소도 Pod에서 도달 가능해야 한다. 토픽 사전 생성·개별 group ACL·보안 방식은 역할 3 계약이 필요하다.
- Deployment 작성 순서가 준비 순서를 보장하지 않는다. Eureka → Config 준비 후 Gateway·업무 앱을 시작한다. 업무 앱과 Gateway 중 누가 먼저여야 하는 필수 조건은 없다. Kafka 활성 시 토픽/브로커도 먼저 준비한다.

## 배포 후 확인

1. Config 키·Git/읽기 인증과 선택적 관리자 Secret 준비 → 이미지 rollout. Kafka가 없으면 false 유지.
2. Config 설정 수신과 profile/service/Git SHA → Eureka 등록 → Gateway 실제 라우팅·CORS 확인.
3. 내부 관리 접근으로 테스트 문자열 암복호화·401/403, 해당 환경 테스트 암호문 수신 확인. 실제 비밀을 info/로그에 넣지 않음.
4. Kafka 준비 후 다섯 앱에 주소·인증·토픽·BUS_ENABLED=true 주입 후 재배포. 관리자 Bus 한 번 호출로 네 클라이언트 값 갱신과 Pod UID/시작 시각 유지 확인.
5. 외부 관리 경로 차단·dev/prod 교차 통신 차단·Pod 복구·readiness 미완료 Pod 트래픽 제외 확인.

로컬에서는 설정/권한·암복호화·Gateway/CORS·실제 테스트 Kafka 갱신을 확인했다. 서버 접속·실제 Secret 생성·이미지 pull·Ingress·배포 Kafka 인증·부하 테스트는 별도다. 미확정 사항은 [검토 목록](REVIEW_CHECKLIST.md)에 담당자와 완료 기준을 정리했다.
