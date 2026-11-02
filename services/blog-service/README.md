# Blog 서비스

기존 모놀리스의 `features/company`, `features/post`, `features/tech`를 이관한 모듈이다. 외부 API의 주소·DTO·추천/필터 기준은 유지하고 User/AI 데이터 조회를 OpenFeign으로 교체한다. 기존 루트 `src/`는 비교·점진 이관을 위해 그대로 둔다.

- Java 17 / Spring Boot 3.4.5 / JPA / MariaDB / OpenFeign
- 서비스 이름: `blog-service`, 기본 포트: `8084`
- DB 소유: company, blog_post, blog_post_section, blog_post_category, blog_post_tag, tech_tag
- 공개 조회: `GET /api/tech-tags`, `GET /api/companies/{id}`
- 개인화 조회: `GET /api/companies`, `GET /api/companies/{id}/posts?onlyMySkills=true`, `GET /api/posts/{id}`
- Swagger: `http://localhost:8084/swagger-ui/index.html`

## 실행

Eureka와 Config를 먼저 실행한다. `.env` 자동 로딩은 하지 않는다. 실행 환경/IDE의 환경변수로 전달한다.

| 환경변수 | 내용 |
|---|---|
| `BLOG_DB_URL` | Blog 전용 MariaDB JDBC URL |
| `BLOG_DB_USER`, `BLOG_DB_PASSWORD` | Blog DB 계정 |
| `CONFIG_USER`, `CONFIG_PASSWORD` | Config 읽기 계정 |
| `CONFIG_SERVER_URL` | 기본 `http://localhost:8888` |
| `CONFIG_GIT_LABEL` | 기본 `main` |
| `EUREKA_URL` | 기본 `http://localhost:8761/eureka/` |
| `INTERNAL_API_TOKEN` | User/AI/Blog가 함께 주입받는 내부 API 인증값. Git에 기록하지 않음 |
| `BUS_ENABLED` | 기본 `false` |

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
bash ./gradlew :services:blog-service:bootJar
java -jar services/blog-service/build/libs/blog-service.jar
```

Blog DB에는 기존 콘텐츠 덤프의 6개 테이블을 기존 PK 그대로 준비한다. 기본 `ddl-auto=validate`로 데이터를 생성/초기화하지 않는다. 제공된 덤프에는 DROP TABLE이 있으므로 새 로컬 DB에만 적재하며 기존 공유 DB에서 실행하지 않는다.

User/AI의 Blog용 내부 조회는 해당 서비스에서 `RECU_INTERNAL_BLOG_ENABLED=true`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, 동일한 `INTERNAL_API_TOKEN`을 주입하면 활성화된다. User에는 app_user/user_job_field/user_skill/bookmark, AI에는 ai_guide가 필요하다. 내부 조회는 다른 서비스 DB에 접근하지 않는다.

Config를 제외한 기능 개발은 `SPRING_PROFILES_ACTIVE=standalone`으로 실행할 수 있다. 이 경우에도 Blog DB와 내부 호출 대상은 필요하다. Eureka 없이 직접 연결할 때는 `CLIENTS_USER_URL`, `CLIENTS_AI_URL`을 지정하고 `EUREKA_CLIENT_ENABLED=false`를 준다.

## 검증

```bash
bash ./gradlew :services:blog-service:test :services:user-service:test :services:ai-service:test
```

H2 격리 테스트에서 추천 순서/기술 필터/본문·목차/북마크·가이드 조합/오류·배치 처리/DB 소유 경계를 검증한다. 실제 MariaDB 및 Eureka 연결 검증과는 구분한다.

인프라 담당의 DB 없는 등록/설정/Bus 검증은 **진단 전용** `diagnostic` 프로필을 함께 사용한다. 이 프로필은 업무 API·DB·해당 보안 구성을 로딩하지 않으므로 기능 검증 또는 배포에 사용하지 않는다. 기존 `scripts/verify-msa.py`, `scripts/verify-config.py`는 Blog에만 이 프로필을 추가하도록 수정했다.

내부 API 계약·수정 파일·팀 연동 사항은 [인계 문서](../../docs/BLOG_SERVICE_HANDOFF.md)를 참고한다.

## 공통 모델 기준

AI 담당자 브랜치 e010322의 `features/internal/domain/entity` 및 `features/internal/repository`를 공통 모델로 사용한다. 기존 `database` 패키지는 제거했고 외부 조회와 내부 조회가 같은 Repository를 주입받는다. 상대 코드의 `/internal/blog/{postId}`, `/internal/blog/{postId}/sections`, `/internal/company/{companyId}`, `/internal/tech-tags?ids=...` 계약도 제공하며, 기존 내부 인증 토큰 헤더가 필요하다. AI 쪽 BlogReadFeignConfig가 blog-service 호출에 토큰을 전달하므로 동일한 INTERNAL_API_TOKEN을 주입한다. 자세한 후속 변경/검증 및 User 담당자 인계는 인계 문서의 "Blog 모델 통일 후속 반영"을 참고한다.
