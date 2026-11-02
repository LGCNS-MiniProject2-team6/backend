# Blog 서비스 분리 인계

## 목적과 변경 범위

기존 서비스의 기업 추천/기업 상세/기업별 글 목록/글 상세/기술 태그 조회를 `services/blog-service`로 분리했다. 새로운 추천·크롤링·Kafka 알림 기능은 추가하지 않았다. 기존 모놀리스 `src/`는 변경하지 않았으며, 모놀리스와 새 모듈의 데이터 모델은 별도 실행 애플리케이션에서 사용한다.

설계 근거: [팀 MSA 설계 문서](https://docs.google.com/document/d/1MXvV0wtLFtR3apnPtBGw3dc3xqWoK9Tr/edit)의 서비스 소유권, Auth/User 통합, `userId` 헤더 및 요청 주체가 상대 서비스 `internal` 코드까지 작성한다는 최종 인계 규칙. 공용 Feign 시트는 확인 시점에 헤더만 있어 기존 계약과 중복되는 행이 없었다. 외부 문서/시트는 수정하지 않았다.

## 소유 데이터와 구조

Blog는 company, blog_post, blog_post_section, blog_post_category, blog_post_tag, tech_tag만 소유한다. Company/Post/Tech 엔티티와 복합 키, 관련 Repository를 새 모듈로 이관했다. Blog 안의 JPA 연관관계는 유지한다. UserEntity/BookmarkEntity/AiGuideEntity나 그 Repository는 Blog에 포함하지 않는다.

```text
blog-service/com.recu.blog
├── bootstrap/             업무 코드 로딩, Feign 활성화
├── features/company/      기존 추천·기업·글 목록
├── features/post/         기존 본문·목차·상대 URL 변환
├── features/tech/         기존 기술 칩
├── features/internal/     AI 브랜치 기준 공통 Entity·Repository 및 내부 조회
├── client/                UserClient, AiClient, 배치 조합
├── internal/              User/AI가 호출하는 Blog 데이터 조회
└── commons/               헤더 인증 연결·접근 제어·기존 오류 형식
```

CompanyService/PostService는 원격 조회와 응답 조합을 담당하고 CompanyReadService/PostReadService는 Blog DB 읽기 트랜잭션을 담당한다. HTTP 호출 대기 중 Blog DB 트랜잭션을 유지하지 않는다. JPA open-in-view는 끈다.

## 기존 동작 유지

- 기업 목록: 관심 기술 ID 일치 개수 → 일치 게시글 수 내림차순, 첫 기업 recommended 표시 유지
- 게시글 목록: is_tech=false 제외, true/null 포함, 희망 직무 또는 General + 관심 기술 매칭 유지
- onlyMySkills=true의 일치 기술 수/최신순 정렬, false의 최신순 정렬 유지
- profile_version=0의 기업 추천 PROFILE_REQUIRED 처리 유지
- 본문 HTML, 목차 앵커, 상대 URL의 절대 URL 변환, 구간이 없을 때 1개 구간 fallback 유지
- 기존 외부 URL/DTO 필드 유지, 오류는 `{code,message,field}`
- 개인화 부분의 장애를 북마크=false/hasGuide=false로 숨기지 않고 UPSTREAM_UNAVAILABLE(503)로 반환

## Blog가 호출하는 내부 API — 이번 작업에서 상대 서비스 코드도 추가

모든 호출은 `X-Internal-Token`과 양수 `userId` 헤더를 사용한다. 토큰은 `INTERNAL_API_TOKEN`으로 동일하게 주입한다. User/AI 내부 조회는 원래 서비스 기능을 모두 이관한 구현이 아니라, Blog가 필요로 하는 좁은 읽기 어댑터다.

| 호출 | Method/경로 | 입력 | 응답 |
|---|---|---|---|
| Blog → User | GET /internal/users/recommendation-profile | userId 헤더 | userId, profileVersion, jobFields, wantSkillIds |
| Blog → User | POST /internal/users/bookmarks/lookup | userId 헤더, `{postIds:[...]}` | `{postIds:[북마크된 ID...]}` |
| Blog → AI | POST /internal/guides/lookup | userId 헤더, `{profileVersion:1,postIds:[...]}` | `{postIds:[가이드 있는 ID...]}` |

관심 기술의 이름은 Blog의 tech_tag에서 확인한다. User가 기술 이름을 얻으려고 다시 Blog를 호출할 필요가 없다. 여러 게시글은 최대 200개씩 묶어 보내며, 200개 초과 시 여러 배치로 나눈다. 연결 2초/읽기 5초 제한, 자동 재시도 없음. Eureka의 user-service/ai-service 이름으로 찾으며 `CLIENTS_USER_URL`, `CLIENTS_AI_URL`로 직접 주소를 지정할 수도 있다.

User의 `internal/BlogSupport*`는 자기 DB의 4개 테이블만, AI의 동일 어댑터는 자기 DB의 ai_guide만 JDBC로 읽는다. 상대 담당자가 이관 중인 JPA 엔티티나 서비스 코드를 복사하지 않기 위해 좁은 SQL 조회로 구현했다. 이후 해당 담당자가 자체 JPA Repository로 교체해도 위 HTTP 계약은 유지하면 된다. User 프로필의 여러 조회는 읽기 전용 반복 가능 읽기 트랜잭션으로 묶는다.

두 어댑터는 기존 골격 실행을 유지하도록 기본 비활성이며, `RECU_INTERNAL_BLOG_ENABLED=true` 및 해당 서비스 DB 설정을 주입해야 한다. 외부 API 로그인/프로필 수정/북마크 저장 또는 AI 가이드 생성을 구현한 것으로 보지 않는다.

## 다른 팀원이 재사용할 Blog 내부 API

Blog 내부 조회는 User/AI로 되돌아가는 호출을 하지 않는다. 내부 데이터 응답에 북마크/가이드 여부를 섞지 않는다.

| Method/경로 | 입력 | 응답/용도 |
|---|---|---|
| GET /internal/posts/{postId} | postId | postId,title,field,level,contentText,sections. AI 프롬프트용 원문 |
| GET /internal/posts/{postId}/exists | postId | `{exists:true/false}`. 북마크 등록 검증 |
| POST /internal/posts/lookup | `{postIds:[...]}` | `{posts:[...]}`. 게시글·기업·태그·날짜·요약·원문 URL, 북마크 목록 조합 |
| POST /internal/tech-tags/lookup | `{techTagIds:[...]}` | `{techTags:[{techTagId,name,field}],missingIds:[...]}`. 프로필 기술 검증/이름 조회 |

X-Internal-Token은 필수다. 두 배치 API는 1~200개의 양수 ID를 받는다. 조회되지 않은 게시글은 posts에서 제외하며 호출 측에서 ID로 매칭한다. content의 sections에는 sectionId, seq, heading, contentText, charCount를 제공한다. 구간이 없는 경우 내부 조회는 빈 sections를 그대로 제공하므로 AI 담당자가 기존 처리 정책을 유지해야 한다.

## Gateway/인프라 담당 연동

1. 서비스 이름 blog-service / 기본 포트 8084, 기존 Gateway 라우팅 유지
2. 기본 모드의 Blog는 실제 전용 DB 필요. BLOG_DB_URL/USER/PASSWORD 주입, 기본 ddl-auto=validate
3. 사용자별 외부 API는 설계 문서대로 `@RequestHeader("userId")` 사용. Blog는 JWT 발급/검증을 새로 구현하지 않음
4. Gateway는 외부 userId 헤더를 제거하고 JWT에서 검증한 값으로 덮어써야 함. 현재 저장소의 Gateway는 라우팅 골격이며 JWT 검증/헤더 주입 완료를 이번 작업이 보증하지 않음. 서비스 직접 접근 제한도 인프라 연동 사항
5. 현재 Gateway fixture는 X-User-Id 제거 설정이므로 실제 계약의 userId와 이름을 맞출 것
6. 내부 API는 공통 X-Internal-Token 인증 적용. Gateway에 /internal 경로 라우팅을 추가하지 않음
7. Config/Git/암호화/Bus 구성은 유지. 비밀값은 Git에 기록하지 않음
8. 운영 DB 변경·원격 배포·외부 Kafka/크롤러 연결은 수행하지 않음

## 검증 기록

- Blog 격리 HTTP/DB 테스트 16개: 실제 6개 테이블만 생성, 기존 추천/필터/정렬/본문/목차/북마크·가이드 조합, 인증·입력·장애·배치 확인
- User 내부 어댑터 5개 / AI 내부 어댑터 4개: 각 소유 DB만 사용, 사용자·프로필 버전 격리, 인증·입력 오류 확인
- 별도 로컬 MariaDB 3개 DB/서비스 계정으로 기존 콘텐츠 덤프를 Blog에 적재하고 실제 User/AI/Blog의 HTTP 및 Feign 조회 확인
- 로컬 연동용 사용자와 가이드 레코드는 테스트 fixture이며 실제 로그인/AI 생성 결과가 아님
- 플랫폼 진단 프로필은 등록/설정/Bus 검사만 수행하며 업무 API 검사 결과와 구분

### 최종 결과 (2026-11-02 KST)

- Java 17 `bash ./gradlew check --no-daemon` 통과: 전체 66개 테스트 실패/오류 0, 기존 모놀리스 36개 포함
- 마지막 User 읽기 트랜잭션 변경 후 User spotless/check/bootJar 재검증 통과
- `scripts/verify-msa.py` 통과: 세 서비스 health/liveness/readiness 및 Eureka 등록/포트 일치
- `scripts/verify-config.py` 통과: 9개 local/dev/prod 설정, 중앙 설정 수신, Eureka 등록, 잘못된 인증 fail-fast
- 로컬 MariaDB에서 Blog 계정의 User DB 직접 SELECT가 거절됨을 확인
- 실제 Blog → User/AI Feign 및 Gateway → Blog → User 조회 통과
- 기존 모놀리스/플랫폼 애플리케이션 코드는 수정하지 않음. 공유 Config 저장소·Google 문서·공용 시트는 변경하지 않음


## GitHub 제출

- 이슈: #26
- 작업 브랜치: `refactor/26/blog-service-separation`
- PR 대상: `develop`
- User 연동 작업 #24의 `X-User-Id`와 설계 문서의 `userId` 차이는 통합 리뷰 대상

## Blog 모델 통일 후속 반영

기준은 `refactor/1/ai-service`의 `e01032226d310919c63c1af27b7fec4833f8823f`이다. AI 담당자가 추가한 아래 위치와 ID/getter 명명을 기준으로 Blog의 기존 기능을 맞췄다.

- 공통 Entity: `com.recu.blog.features.internal.domain.entity`
- 공통 Repository: `com.recu.blog.features.internal.repository`
- 기존 `com.recu.blog.database` Entity/Repository 및 중복 PostRepository/PostSectionRepository 제거
- 기존 기업·글·기술 칩과 양쪽 내부 조회 Service가 같은 공통 모델·Repository 사용
- 기존 게시글 태그/카테고리·복합 키도 공통 패키지에 배치, TechTagEntity에는 기존 응답에 필요한 field/aliases/post_count/selectable 컬럼 보완
- 기술 태그 ID는 상대 코드의 `techTagId` / `getTechTagId()` 유지
- 본문 기술명 조회와 기술명 단건 조회는 공통 Repository에 보완
- DB 테이블명·컬럼명·기존 PK와 외부 API 응답 유지

AI 브랜치의 내부 조회 Controller/DTO/Service도 반영했으므로 아래 원래 호출 주소를 유지한다.

| Method | 경로 | 응답 |
|---|---|---|
| GET | /internal/blog/{postId} | AI가 기대하는 blogPostId·본문·기업 ID 등 |
| GET | /internal/blog/{postId}/sections | seq 순서의 구간 목록 |
| GET | /internal/company/{companyId} | 기업 정보 |
| GET | /internal/tech-tags?ids=1,2 | techTagId·name 목록 |

이 주소들도 Blog의 기존 내부 인증 계약인 `X-Internal-Token`을 사용한다. 이번 후속 변경의 AI `internal/BlogReadFeignConfig`가 `blog-service` Feign 호출에만 해당 헤더를 붙인다. 기존과 같이 동일한 INTERNAL_API_TOKEN을 환경변수로 주입해야 하며 다른 Feign 대상에는 Blog 토큰을 전달하지 않는다. 기존 /internal/posts 등 주소도 유지한다.

상대 브랜치의 임시 `features/internal/temp` Security는 공통 컴포넌트 스캔에서 제외하며 Blog의 보안 체인을 하나만 사용한다. 상대 Service의 ResponseStatusException은 공통 예외 처리기에서 원래 상태 코드로 반환한다. 내부 보안 정책을 permitAll로 완화하지 않았다.

정리 후 회귀 검증에는 공통 Repository별 Bean 1개, Entity별 단일 타입, 보안 체인 1개, AI용 원래 4개 조회 경로, 404와 필수 파라미터 오류를 포함한다. GitHub PR을 병합하지 않았으며 현재 Blog 작업 브랜치의 후속 커밋으로 제출한다.

### 예림 담당 User 쪽 인계

`feat/24/user-service-api`의 `522dd936601fdf36cbda7377ce77df61aec56ec5`와 AI 브랜치를 비교했다. 이번 후속 변경에서는 User 업무 모델을 임의로 바꾸지 않는다. AI 브랜치가 추가한 User 내부 조회 Service가 아래 예림 코드의 모델/Repository를 재사용하면 중복을 정리할 수 있다.

- AppUserEntity/AppUserRepository 대신 `com.recu.user.domain.entity.UserEntity` / `com.recu.user.repository.UserRepository` 사용
- AI 쪽 UserJobFieldEntity/UserSkillEntity/UserSkillId 대신 예림의 UserJobFieldEntity/UserSkillEntity/UserSkillPk 재사용
- AI 쪽 UserJobFieldRepository/UserSkillRepository 중복 선언 제거 후 예림 Repository 사용
- ProfileInternalService의 `findByUserId(userId)`와 `findProfileSkills(userId)`를 각각 예림 Repository의 `findByUser_UserId(userId)`로 연결, 기술 ID 순서가 필요하면 결과 정렬
- /internal/users/{userId} 경로와 ProfileResponseDTO는 유지하면서 import/주입 대상/조회 메서드만 조정

이것은 User 쪽 통합 시 필요한 별도 변경이며 Blog 중복 해결 완료와 구분한다. User 담당자의 PR과 함께 검토해야 한다.

### 후속 검증 결과

- 전체 `bash ./gradlew check --no-daemon` 통과: 72개 테스트, 실패/오류 0
- Blog 20개: 기존 외부 동작, 공통 Bean/Entity 단일성, AI 원래 조회 계약, 404/400 유지, 임시 Security 제외
- AI 6개: 기존 조회 어댑터 4개 + Blog Feign 인증 전달/다른 대상에 미전달 2개
- 실제 로컬 MariaDB에서 상대 AI용 4개 조회, 기존 본문·추천·목록과 User/AI 개인화 조합, 내부 인증 및 404 응답 확인
- 새 Blog와 AI 모듈로 로컬 재시작, Eureka 등록 수렴 후 Blog health 및 Gateway 경유 글 상세 조회 정상 확인
- 실제 Gemini 생성 요청은 수행하지 않았으며, User 서비스 담당자 브랜치의 업무 모델 통합은 별도 인계 사항
