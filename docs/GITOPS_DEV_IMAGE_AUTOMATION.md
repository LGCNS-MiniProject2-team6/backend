# dev 이미지 PR 자동화

`apps-ci.yml`은 develop push에서 변경 앱의 이미지 업로드 성공 후 서비스별 GitOps PR을 생성합니다. PR 이벤트에서는 이미지 업로드와 GitOps 쓰기를 수행하지 않습니다.

## 실행 전 준비

- GitOps PR #2의 `overlays/dev-apps/images/<서비스>.json` Deployment patch를 main에 반영합니다.
- backend Actions secret `GITOPS_TOKEN`: recu-gitops 전용 fine-grained PAT. Contents / Pull requests 읽기·쓰기 권한과 필요한 조직 승인을 설정합니다.
- 선택 변수: `GITOPS_REPOSITORY` (기본 `LGCNS-MiniProject2-team6/recu-gitops`), `GITOPS_BASE_BRANCH` (기본 `main`).

## 동작과 확인

- 해당 서비스의 `app` 컨테이너 이미지 값만 전체 소스 SHA 태그로 갱신합니다. prod 경로와 다른 서비스는 변경하지 않습니다.
- 서비스·소스 SHA별 고정 브랜치를 사용하므로 같은 커밋 재실행은 같은 PR을 갱신합니다. 서로 다른 소스 커밋은 별도 PR로 남습니다.
- PR 본문에 이미지, registry digest, 소스 커밋과 CI 링크를 남깁니다. 자동 병합은 수행하지 않습니다.
- 역할 2는 동일 서비스의 오래된 미병합 PR을 닫고 최신 PR을 검토·병합합니다. 오래된 PR을 나중에 병합하면 구버전이 적용될 수 있습니다.
- GitOps 파일이나 토큰이 없으면 이미지 업로드 후 GitOps 단계가 실패합니다. 준비 후 동일 Actions 실행을 재시도할 수 있습니다.

로컬 확인: `python scripts/test_update_gitops_image.py`, `git diff --check`.
실제 확인: develop 병합 후 Push image / GitOps checkout / PR 생성 성공, dev patch와 업로드 SHA·digest 일치 확인. 병합 후 Argo CD·앱 상태 확인은 역할 2와 진행합니다.
