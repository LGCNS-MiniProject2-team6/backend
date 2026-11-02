# 배포 전 검토·확정 목록

[문서 목차](README.md) · [역할 2 인계](PLATFORM_K8S_HANDOFF.md)

소스·로컬 검증으로 확인한 계약과 실제 서버 확인을 구분한다. 아래는 아직 이 작업에서 확인되지 않은 항목이며 제안값을 확정값으로 복사하지 않는다. 실제 비밀값은 목록에 적지 않는다.

| 우선 시점 | 검토 항목 | 담당 | 확정·반영 위치 | 완료 기준 |
|---|---|---|---|---|
| 기본 배포 전 | 앱 목록: 플랫폼 3 + User·AI·Blog, 독립 Auth/Notification 제외 | 역할 1·2·4·백엔드 | Deployment/Service/CI 이미지 목록 | 6종과 환경별 replica 수 합의, 폐기 앱 미배포 |
| 기본 배포 전 | 플랫폼/업무 Dockerfile·registry 전체 주소·SHA/digest·CPU 아키텍처 | 역할 4 | Docker 빌드·manifest | 6개 이미지 pull·앱 시작, dev→prod 같은 이미지 승격 |
| 기본 배포 전 | Service/Deployment/컨테이너 이름, Namespace·두 노드 라벨 | 역할 2·4 | recu-dev/recu-prod manifest | workload+environment 일치, control-plane 일반 앱 미배치 |
| Config rollout 전 | ENCRYPT_KEY, 읽기/Git/선택적 관리자 Secret 참조 | 역할 2·1 | Namespace별 Secret/env | Config 기동·암호문 복호화, 키/관리자/Git 토큰 클라이언트 미주입 |
| Config rollout 전 | Git 토큰 권한·소유자·만료·조직 승인, dev/prod label 정책 | 역할 1·운영 담당 | Secret·ConfigMap·비밀 보관 절차 | 실제 Config Git clone·예상 commit/profile 일치, 키 교체/복구 절차 |
| 외부 공개 전 | 프론트 dev/prod Origin, Gateway host/port·IngressClass·TLS·timeout | 역할 2·프론트·1 | Ingress·Gateway ConfigMap | 허용/거부 CORS, 실제 요청, 관리 경로 외부 차단 |
| 외부 공개 전 | Gateway JWT 인증과 서비스별 권한 검사·실제 업무 API 이관 | 백엔드·역할 1/2 | Gateway/도메인 코드·네트워크 | 구현 API·권한 테스트, 전달 헤더 위조 방어, stub 검증과 구분 |
| Bus 활성 전 | Kafka bootstrap·advertised 주소·TLS/SASL·ACL·토픽·partition/replication | 역할 3·1·2 | Kafka·Secret·ConfigMap | dev/prod 토픽 사전 생성, 다섯 앱 구독, Bus 1회 무재시작 반영 |
| 업무 DB 연결 전 | DB 방식·AI 저장 필요 여부·DB명/계정·환경변수·초기화·PVC/백업 | 역할 3·백엔드 | 데이터 manifest·도메인 코드 | 서비스-DB 매핑·환경별 데이터/계정 격리·복구 확인 |
| 관측 연동 전 | Prometheus/Grafana/Alertmanager 범위·이미지·저장소·scrape 인증 | 역할 3·백엔드·1 | 데이터 manifest·앱 의존성/노출 | 현재 prometheus registry 미적용: 추가 후 내부 수집·관리 노출 제한 |
| 환경 운영 전 | Namespace RBAC·NetworkPolicy enforcement·DNS/egress·격리 | 역할 2 | Role/RoleBinding·NetworkPolicy | dev→prod 차단 실검증, 허용 앱 경로·Git/DNS 연결 유지 |
| 자동 배포 전 | develop→dev 자동 여부·prod 승인·runner 위치·SA 인증/권한 수명 | 역할 4·2 | Workflow·Environment·Namespace 권한 | 최소 권한 실제 배포, 관리자 kubeconfig/SSH 미사용, prod 승인 경계 |
| 안정성 검증 전 | CPU/메모리·heap·probe 수치·rollout 여유·Pod 복구 | 역할 2·4 | resources/probes·JVM 옵션 | 제한 자원에서 기동·OOM/timeout 확인, readiness 차단·복구 실검증 |
| 데이터 운영 전 | local-path 노드 고정·저장 용량·백업·노드 장애 대응 | 역할 3·2 | PVC/StorageClass·복구 절차 | 노드 변경 시 자동 공유되지 않는 제약 확인과 복구 수행 |
| main 반영 전 | Workflow의 main 반영·check-main-source 필수 검사·ruleset 우회 | 저장소 관리자·역할 4 | GitHub 설정 | develop 허용/다른 소스 거부·직접 push 차단, 조직 규칙 충돌 확인 |

## 지금 바로 필요한 회신

- 역할 2: Service/Deployment/컨테이너·Ingress 최종 이름, 외부 주소, 두 노드 라벨과 초기 자원 제안 검토.
- 역할 3: Kafka 주소·보안·환경 토픽, DB·모니터링·PVC 설계. 서비스 4개라는 이전 가정은 사용하지 않음.
- 역할 4: 이미지 전체 경로·tag/digest·Dockerfile·아키텍처, dev/prod 배포 및 runner·권한 방식.
- 백엔드/프론트: 실제 도메인 기능 이관·인증/권한·환경변수·API 경로·프론트 Origin.

플랫폼 기본 기동은 Kafka 비활성으로 먼저 할 수 있다. DB 개수·이미지 경로·Ingress 주소·운영 보안 방식은 역할 1이 임의 확정하지 않는다. 합의되면 인계 문서와 각 코드/설정의 원천을 함께 갱신한다.

## 문서 검토 범위와 한계

이번 문서 정리는 현재 코드·CLI 옵션·링크·계약 일치 여부를 검사한다. 로컬 통합 검증은 기존 실행 결과가 있고, 사용자 Postman 암복호화·401/403 확인도 완료했다. 문서 변경만으로 서버/배포 검증이나 GitHub 보호 설정이 새로 완료된 것은 아니다. 과거 PR 진행 상태·후보 commit·4개 서비스 검증 로그는 Git 이력에서 확인한다.
