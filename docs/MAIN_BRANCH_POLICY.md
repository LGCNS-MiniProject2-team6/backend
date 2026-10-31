# main 반영 정책

관련 이슈: #5. **같은 저장소의 develop → main만 허용**한다. Workflow는 PR 생성 자체를 금지하지 않고 허용되지 않은 PR의 검사를 실패시킨다. 실제 병합 및 직접 push 차단은 GitHub 보호 설정으로 적용한다.

## 코드 변경

`.github/workflows/check-source-branch.yml`은 main 대상 PR에만 실행한다.

- head 브랜치가 정확히 `develop`이고 head 저장소 ID가 현재 저장소 ID와 같을 때만 성공
- 같은 저장소의 다른 브랜치, fork의 develop, 소스 저장소가 없는 경우는 실패
- PR 생성·커밋 갱신·재오픈·대상 브랜치 수정·리뷰 준비 전환 때 재검사
- develop 대상 PR에는 이 검사를 적용하지 않음
- 필수 검사 이름: **`check-main-source`**. 변경 시 GitHub 보호 설정도 같이 변경해야 함

`pull_request` 대신 `pull_request_target`을 사용한다. 일반 PR Workflow는 PR에서 검사 파일을 수정해 검사를 우회할 수 있으므로 대상 브랜치의 신뢰된 정의를 사용한다. PR 코드를 checkout하거나 실행하지 않으며, Secret과 쓰기 토큰 권한도 사용하지 않는다. 향후 이 Workflow에 PR 코드 빌드·실행을 추가하지 않는다. PR 메타데이터는 환경변수로 전달하고 shell 코드에 직접 삽입하지 않는다.

## GitHub 적용 순서

**이 파일을 develop에 머지하는 것만으로 main 보호가 완성되지는 않는다.** 최초 적용은 다음 순서로 진행한다.

1. 이번 작업 PR을 develop에 머지한다.
2. develop → main 초기 반영 PR로 Workflow를 main에도 반영한다. 대상 브랜치에 아직 Workflow가 없으면 최초 PR에서는 검사가 실행되지 않을 수 있으므로 수동으로 소스 저장소·브랜치를 확인한다.
3. main에 Workflow가 존재한 상태에서 develop → main PR의 검사를 실행한다. 필요하면 추가 변경 후 PR 또는 기존 열린 PR의 재오픈 등으로 실행한다.
4. 성공한 `check-main-source` 검사를 확인한 뒤 아래 보호 규칙의 필수 검사로 등록한다. 검사 정의가 main에 없거나 실행 이력이 없는 상태에서 먼저 필수화해 초기 반영을 막지 않는다.
5. 허용/거부 시나리오를 실제 GitHub PR에서 확인한다. 확인 목적의 PR은 정상 기능 반영 없이 merge하지 않는다.

Settings → Branches → Add branch protection rule에서 **정확히 `main`**만 지정한다. Rulesets를 쓴다면 같은 요구를 Active ruleset으로 적용하고 중복 규칙을 확인한다.

| 설정 | 적용 값 |
|---|---|
| Require a pull request before merging | 활성화: 직접 push 차단 |
| Require approvals | 이번 정책에서는 승인 필수 없이 진행 가능. 팀 합의 시 별도 지정 |
| Require status checks to pass before merging | 활성화, `check-main-source` 필수 |
| 검사 제공자 | GitHub Actions로 선택 가능하면 지정 |
| Do not allow bypassing the above settings / 관리자 포함 | 활성화 |
| 우회 허용 사용자·팀·앱 | 두지 않음 |
| Allow force pushes / Allow deletions | 비활성화 |

다른 브랜치에 리뷰 승인을 강제하는 규칙은 이 작업에서 새로 만들지 않는다. 이미 존재하는 조직 ruleset이 있으면 별도로 영향을 확인한다. 저장소 설정 편집 권한이 있는 관리자는 정책 자체를 변경할 수 있다는 운영 경계는 남는다.

## 검증 시나리오

| 대상 | 소스 | 기대 |
|---|---|---|
| main | 같은 저장소 develop | check-main-source 성공 |
| main | 같은 저장소 feature/chore 브랜치 | 검사 실패, 보호 적용 후 merge 차단 |
| main | 다른 저장소 develop | 검사 실패, 보호 적용 후 merge 차단 |
| develop | 작업 브랜치 | 이 Workflow 실행 대상 아님 |
| main 직접 push | 일반 사용자 및 우회 없는 관리자 | 보호 설정으로 거부 |

직접 push 차단은 실제 main 커밋을 밀어 넣는 방식으로 테스트하지 말고 보호 규칙·우회 설정을 먼저 검토한다. Workflow가 실패하는 것과 실제 merge가 차단되는 것은 별도로 확인한다.

## 이번 로컬 검증과 남은 적용

로컬에서는 Workflow YAML 문법, shell 문법, head 브랜치·저장소 ID 조건의 허용/거부 조합을 검사한다. GitHub 이벤트 실행 및 보호 규칙에 의한 차단은 코드가 반영된 뒤 검증해야 한다. 푸시·PR·main 반영·보호 설정 변경은 검토 및 승인 후 순서대로 적용한다.

2026-10-31 확인 결과 classic branch protection은 없지만 **Active ruleset `Protect-Main-Branch` (ID 26269927)**이 `refs/heads/main`에 적용되어 있었다.

- PR 필수, 필수 승인 0명, 삭제·force push 제한 적용
- `Restrict updates`(`update`)와 `Restrict creations`(`creation`)도 활성화
- bypass actor 없음, 현재 사용자도 우회 불가
- 필수 상태 검사 규칙은 아직 없음

**기존 ruleset을 수정할 때는 `Restrict updates`를 해제해야 한다.** 이 규칙은 업데이트를 bypass actor로 제한하므로 현재처럼 허용자가 없으면 정상 PR 병합까지 막을 수 있다. 직접 push 차단은 `Require a pull request before merging`으로 유지한다. `Restrict creations`도 main 재생성 제한이 특별히 필요하지 않다면 해제하고, PR 필수·삭제 금지·force push 금지·bypass 없음은 유지한다.

[기존 main ruleset](https://github.com/LGCNS-MiniProject2-team6/backend/rules/26269927)을 수정하며 새 중복 ruleset을 만들지 않는다. 초기 develop → main 반영 전에 위 업데이트 제한부터 정리하고, Workflow 실행 확인 후 `check-main-source`를 필수 검사로 추가한다. 이 문서 작성 중에는 원격 ruleset을 변경하지 않았다.

참고:

- [GitHub Workflow 이벤트: pull_request_target](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#pull_request_target)
- [GitHub 보호 브랜치](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)
