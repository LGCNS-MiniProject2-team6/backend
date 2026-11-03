# 역할 4 → 역할 2: Argo CD GitOps 진행 확정사항

- 작성일: 2026-11-03
- 목적: 전달받은 「Argo CD GitOps 작업 진행 전 확인사항」 6개 항목에 대한 역할 4의 최종 회신
- 상태: 아래 운영 방침으로 진행. 저장소 생성·권한 등록·Argo CD 설치·배포가 이미 수행되었다는 의미는 아님

## 1. GitOps 저장소

별도 `recu-gitops` 저장소를 사용합니다.

| 항목 | 확정값 |
|---|---|
| 소유 조직 | `LGCNS-MiniProject2-team6` — 현재 backend 원격 저장소와 동일 |
| 저장소 이름 | `recu-gitops` |
| 생성 대상 URL | `https://github.com/LGCNS-MiniProject2-team6/recu-gitops` |
| 기본 브랜치 | `main` |
| 생성·보호 규칙 설정 | 조직 저장소 관리자, 역할 4가 생성 요청·자동화 연결 담당 |
| dev 앱 경로 | `overlays/dev-apps` |
| prod 앱 경로 | `overlays/prod-apps` |

위 URL은 생성할 위치로 확정한 값이며, 현재 저장소 존재나 생성 권한을 확인한 결과는 아닙니다. 역할 2는 이 구조로 앱 경로 분리 작업을 시작하고, 원격 Application 연결은 저장소 생성 후 진행합니다.

이미지 선언은 앞선 협의안대로 각 환경의 `images/<서비스>.json` Deployment patch로 분리하고 `kustomization.yaml`의 `patches`에서 연결합니다. 역할 4 자동화는 해당 서비스의 이미지 값만 갱신합니다. 역할 2는 실제 Deployment·컨테이너 이름과 최종 렌더링 이미지를 확인합니다.

## 2. GitOps 변경 인증과 관리

초기 인증은 **fine-grained personal access token**으로 확정합니다.

- 생성·만료일 관리·갱신·폐기 담당: 역할 4. 조직 승인이 필요하면 조직 관리자가 승인합니다.
- 대상 저장소: `recu-gitops` 한 개만 선택합니다.
- 권한: `Contents: Read and write`, `Pull requests: Read and write`.
- 보관: backend GitHub Actions repository secret `GITOPS_TOKEN`.
- backend repository variables: `GITOPS_REPOSITORY=LGCNS-MiniProject2-team6/recu-gitops`, `GITOPS_BASE_BRANCH=main`.
- 봇은 작업 브랜치 push와 PR 생성까지만 담당합니다. 기본 브랜치 직접 push·보호 규칙 우회·자기 PR 승인은 허용하지 않습니다.
- Argo CD Git 읽기 인증은 역할 2가 별도로 생성·보관합니다. Actions 쓰기 토큰을 공유하지 않습니다.
- 토큰 실값은 Markdown·Git·PR·채팅에 기재하지 않습니다.

Actions는 이미지 push와 GitOps PR 생성까지 담당합니다. 클러스터 배포용 SSH 키·kubeconfig·Argo CD 배포 토큰이나 배포용 self-hosted runner는 이번 방식에 필요하지 않습니다.

## 3. dev 배포 정책

다음 흐름으로 확정합니다.

```text
backend PR 테스트·빌드 확인
→ develop 병합
→ 변경 앱 테스트·이미지 빌드·Docker Hub push
→ GitOps dev 이미지 변경 PR 생성
→ 역할 2 검토·승인·병합
→ Argo CD dev 자동 Sync
→ 상태·지표·기능 확인
```

- dev GitOps PR 검토·병합 담당: **역할 2**, 작성자 외 승인 최소 1명.
- 자동 PR 병합은 사용하지 않습니다.
- dev Application: namespace `recu-dev`, 자동 Sync 사용, `prune=false`, `selfHeal=false`.
- 신규 이미지는 backend 소스의 전체 커밋 SHA 태그를 사용하고, 기존 태그를 덮어쓰지 않습니다.
- PR에 대상 서비스·이미지 주소·digest·소스 커밋·CI 실행 링크를 남깁니다.
- 동일 서비스의 오래된 미병합 PR은 정리하여 뒤늦게 구버전이 적용되지 않게 합니다.
- 변경하지 않은 서비스의 이미지 태그는 유지합니다.

## 4. prod 배포 정책

**dev에서 기능 검증을 통과한 동일 이미지 태그·digest만 prod로 승격**합니다. prod용 이미지를 다시 빌드하지 않습니다.

| 항목 | 담당·기준 |
|---|---|
| prod 승격 PR 작성 | 역할 4 |
| 기능 검증·승격 가능 판단 | 역할 1, 대상 서비스 담당자와 확인 |
| prod PR 필수 승인 | 역할 2, 작성자 외 최소 1명 |
| prod PR 병합 | 역할 2, 기능 검증 기록 확인 후 병합 |
| Argo CD prod 수동 Sync | 역할 2 |
| 배포 후 최종 기능 확인 | 역할 1, 대상 서비스 담당자와 확인 |

- prod Application: namespace `recu-prod`, 자동 Sync 비활성화. 수동 Sync에서도 prune은 사용하지 않습니다.
- 승인 후 PR 내용이 변경되면 다시 승인받습니다.
- prod 경로의 필수 리뷰와 Sync 권한을 각각 설정합니다. 실제 GitHub 계정·Argo CD 계정은 역할 2·4가 역할 담당자 계정에 연결합니다.
- dev의 이미지 선언만 승격하고 ConfigMap·Secret·데이터 연결·노드 배치 설정은 prod 값을 유지합니다.
- prod 데이터·Secret·연결 설정 준비와 승인 기록을 확인한 뒤 Sync합니다.
- `main` 소스 병합만으로 prod 자동 배포하지 않습니다. 검증된 이미지의 GitOps prod PR과 수동 Sync를 배포 기준으로 삼습니다.

## 5. Argo CD 운영 범위

초기 관리 대상은 다음 앱 6개와 앱에 필요한 Deployment·Service·비밀값 없는 ConfigMap 등 앱 전용 선언입니다.

- discovery-server
- config-server
- api-gateway
- user-service
- ai-service
- blog-service

Kafka·MariaDB·Prometheus·Grafana·PVC와 Secret 실값은 초기 GitOps 관리에서 제외합니다. 앱은 역할 2·3이 준비한 기존 Secret과 데이터·모니터링 리소스를 참조합니다. 기존 통합 overlay를 그대로 Application에 연결하지 않습니다.

- 앱 공통 선언은 `base/`, 환경별 선언은 `overlays/dev-apps`·`overlays/prod-apps`로 분리합니다.
- Argo CD는 **non-HA**, `prod-platform` 노드에 배치합니다. 배치 조건은 `environment=prod`, `workload=platform`이며 역할 2가 실제 노드 label과 자원 여유를 확인합니다.
- Argo CD UI는 외부 공개하지 않고 역할 2가 안내하는 port-forward 절차로 접속합니다.
- 설치·bootstrap·AppProject·Application·Git 읽기 인증·RBAC는 역할 2가 담당합니다. 앱 배포 범위는 `recu-dev`·`recu-prod`와 필요한 리소스로 제한합니다.
- 기존 dev 리소스를 인수할 때 현재 실행 설정을 기준으로 diff를 확인합니다. 오래된 이미지·placeholder 설정이 포함된 backend overlay를 그대로 재적용하지 않습니다.

## 6. 롤백과 긴급 대응

| 구분 | 확정 절차 |
|---|---|
| dev 롤백 | GitOps dev 이미지 선언을 직전 정상 버전으로 되돌리는 PR → 역할 2 검토·병합 → 자동 Sync |
| prod 롤백 | GitOps prod 이미지 선언을 직전 정상 버전으로 되돌리는 PR → 역할 2 승인·병합 → 역할 2 수동 Sync |
| 긴급 수동 조치 | 역할 2가 수행하고 조치 내역·사용 이미지를 기록. Git 선언도 실제 정상 상태와 일치시키고 재동기화 확인 |
| 이미지·롤백 PR 지원 | 역할 4 |
| 복구 후 최종 기능 검증 | 역할 1, 대상 서비스 담당자와 확인 |
| DB 스키마·데이터 복구 | 역할 3이 별도 절차 담당. 앱 이미지 롤백으로 복구된 것으로 처리하지 않음 |

`kubectl rollout undo`만 실행한 상태로 롤백을 종료하지 않습니다. 정상 버전은 이전 태그라는 이유만으로 고르지 않고 배포·기능 검증 기록으로 확인합니다.

## 7. 진행·인계 순서

1. 역할 2: 앱 전용 경로 분리와 Argo CD 구성 준비 시작.
2. 역할 4·조직 관리자: 저장소 생성·브랜치 보호·토큰 승인·Actions Secret 등록.
3. 역할 2: 실제 실행 설정에 맞춰 앱 선언 작성, 환경별 Kustomize 렌더링과 기존 리소스 diff 확인.
4. 역할 4: 이미지 push 이후 서비스별 GitOps dev PR 생성 자동화 연결.
5. 역할 2·4: dev 이미지 push → PR → 승인·병합 → 자동 Sync 전체 흐름과 Git 기반 롤백 확인.
6. 역할 1: 배포 후 기능 확인. 역할 3: 데이터·모니터링 확인 지원.
7. prod 준비·검증 기록 확보 후 동일 이미지 승격 PR과 수동 Sync 수행.

역할 2는 앱 전용 경로·Deployment/컨테이너 이름·ConfigMap/Secret 참조 키·Argo CD 접속/Sync 절차를 인계해 주세요. 역할 4는 그 계약에 맞춰 이미지 변경 자동화를 연결하겠습니다.

## 확인 범위

이 문서는 전달받은 질문에 대한 정책 결정 회신입니다. 첨부 문서의 요청을 저장소 생성·토큰 발급·배포 실행 지시로 처리하지 않았습니다. 기존 GitOps 협의안과 현재 backend 원격 주소·로컬 CI 파일을 참고했습니다. 로컬 `apps-ci.yml`에는 이미지 push 단계가 있으나 GitOps PR 생성 단계는 없으므로 추가 연결이 필요합니다. 원격 Actions 성공 여부·저장소 생성 여부·클러스터 상태는 이번 문서 작성에서 확인하지 않았습니다.
