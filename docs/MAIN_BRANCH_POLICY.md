# main 반영 정책

[문서 목차](README.md) · [배포 전 검토](REVIEW_CHECKLIST.md)

정책은 **같은 저장소의 develop → main PR만 허용**하는 것이다. 코드 원천은 [.github/workflows/check-source-branch.yml](../.github/workflows/check-source-branch.yml)이다. API/클라우드 배포 Workflow와 구분한다.

## 구현된 검사

- main 대상 pull_request_target 이벤트에서 실행하며 작업 이름은 check-main-source다.
- head 브랜치가 develop이고 head 저장소 ID가 대상 저장소 ID와 같아야 성공한다. fork의 develop도 거부한다.
- PR 생성·커밋 갱신·재오픈·대상 수정·리뷰 준비 전환에 재검사한다. develop 대상 PR에는 이 검사가 실행되지 않는다.
- permissions={}이며 PR 코드를 checkout/실행하지 않는다. 이 Workflow에 PR 코드 실행이나 Secret 접근을 추가하지 않는다.

이 검사는 허용되지 않은 PR의 검사를 실패시키며 PR 생성 자체나 직접 push를 단독으로 막지는 않는다. 실제 차단에는 GitHub branch protection/ruleset이 필요하다.

## 관리자가 확인할 설정

1. main에도 신뢰할 수 있는 Workflow 정의가 반영되어 실제 check-main-source 실행 이력이 있는지 확인한다.
2. main에 PR 필수·check-main-source 필수 상태 검사·직접 push/force push/삭제 제한을 구성한다. 승인 인원은 팀 합의에 따른다.
3. 관리자/앱/팀 bypass와 조직 ruleset 중복을 검토한다. Restrict updates 등 다른 규칙이 정상 develop PR까지 막지 않는지 확인한다.
4. 기존 규칙을 무작정 삭제하거나 중복 생성하지 말고 저장소 관리자가 원격 설정을 확인한다. 과거 특정 ruleset 조회 결과를 현재 상태로 가정하지 않는다.

| 대상/소스 | 기대 |
|---|---|
| main ← 같은 저장소 develop | 검사 성공, 나머지 보호 조건 충족 시 merge 가능 |
| main ← 다른 작업 브랜치·fork develop | 검사 실패, 보호 적용 시 merge 차단 |
| develop ← 작업 브랜치 | 이 Workflow 대상 아님 |
| main 직접 push | 보호 설정으로 차단 |

직접 push를 시도해 main을 바꾸는 방식으로 시험하지 않는다. 보호 규칙과 정상/거부 PR 시나리오를 확인한다. Workflow의 검사 성공과 실제 merge 차단은 별도 검증 대상이다. 이번 문서 정리에서는 원격 보호 설정을 변경하거나 적용 완료를 주장하지 않는다.
