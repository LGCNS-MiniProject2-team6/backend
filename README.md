# mini8-backend

**미니프로젝트2 MSA 전환 안내:** 현재 실행·Config·Gateway·암호화·Bus·배포 인계는 [docs 문서 목차](docs/README.md)를 참고하세요. 아래 내용은 기존 모놀리스 프로젝트의 기능·실행·배포 기록입니다.

경력, 보유 기술, 관심 기술과 희망 직무를 기반으로 기술 블로그를 추천하고, 선택한 글을 사용자 수준에 맞게 읽을 수 있도록 AI 가이드를 제공하는 ReCu 백엔드 서버입니다.

- 서비스: https://mini8-frontend.vercel.app
- Swagger: https://backend-production-fe2f.up.railway.app/swagger-ui/index.html

## 프로젝트 정보

| 구분 | 내용 |
|---|---|
| 제작 기간 | 2026-09-21 ~ 2026-09-30 |
| 참여 인원 | 6명(Frontend 2명, Backend 4명) |

## 주요 기능

- Google OAuth 로그인
- JWT Access Token 발급 및 Refresh Token 재발급·폐기
- 사용자 프로필 최초 등록·조회·수정
- 관심 기술 기반 기업 및 기술 블로그 추천
- 기업 정보와 게시글 원문 조회
- Gemini 기반 맞춤형 읽기 가이드 생성
- 게시글 북마크 등록·조회·삭제
- 기업 기술 블로그 수집 및 관리자용 적재 API

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.4.5 |
| Database | MariaDB |
| ORM | Spring Data JPA · Hibernate |
| Authentication | Google OAuth 2.0 · JWT |
| API 문서 | Springdoc OpenAPI · Swagger UI |
| AI | Google Gemini |
| 수집 | Jsoup |
| Test | JUnit 5 · H2 |
| Deployment | Railway |

## 실행 환경

로컬 실행에는 다음 항목이 필요합니다.

- JDK 17
- MariaDB 11.x
- Google OAuth Client ID
- Gemini API Key

Java 버전은 Gradle toolchain에서 17로 고정되어 있습니다.

## 시작하기

### 1. 저장소 복제

```powershell
git clone https://github.com/LGCNS-AM06-8/mini8-backend.git
cd mini8-backend
git checkout develop
```

### 2. 데이터베이스 생성

```sql
CREATE DATABASE mini8
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
```

### 3. 로컬 데이터 넣기

[`mini8-content-dump.sql`](https://github.com/LGCNS-AM06-8/mini8-backend/blob/develop/mini8-content-dump.sql)을 다운로드 받아 명령 프롬프트에서 실행합니다.

```cmd
mariadb -u root -p mini8 < mini8-content-dump.sql
```

덤프에는 기업 · 게시글 · 구간 · 기술 테이블만 들어 있습니다. 사용자 정보는 없으므로 서버를 실행한 뒤 본인의 Google 계정으로 로그인하면 됩니다.

### 4. 환경변수 설정

```powershell
Copy-Item .env.example .env
```

`.env`에 다음 값을 입력합니다.

| 환경변수 | 설명 | 필수 |
|---|---|---|
| `DB_URL` | MariaDB JDBC 주소 | O |
| `DB_USER` | MariaDB 사용자 | O |
| `DB_PASSWORD` | MariaDB 비밀번호 | O |
| `JWT_SECRET` | JWT 서명 키 | O |
| `GOOGLE_CLIENT_ID` | Google OAuth Client ID | O |
| `GEMINI_API_KEY` | Gemini API Key | O |
| `COLLECT_DATA_DIR` | 수집 데이터 파일 경로 | 수집 기능 사용 시 |

`.env`에는 비밀값이 포함되므로 커밋하지 않습니다.

### 5. 서버 실행

```powershell
.\gradlew.bat bootRun
```

로컬 Swagger:

```text
http://localhost:8000/swagger-ui/index.html
```

## 테스트

PR을 올리기 전에 전체 검사를 실행합니다.

```powershell
.\gradlew.bat check
```

MariaDB 없이 H2를 사용해 전체 테스트를 실행합니다.

```powershell
.\gradlew.bat test
```

외부 기술 블로그에 실제 요청하는 수집 테스트는 별도로 실행합니다.

```powershell
.\gradlew.bat test -Pnetwork
```

코드 포맷을 자동으로 적용하려면 다음 명령을 사용합니다.

```powershell
.\gradlew.bat spotlessApply
```

## 프로젝트 구조

```text
com.mini8.backend
├─ commons/                       공통 기능
│  ├─ config/                     Security · CORS · Swagger 설정
│  ├─ exception/                  오류 코드 · 비즈니스 예외
│  ├─ filter/                     JWT 인증 필터
│  ├─ handler/                    공통 오류 응답 처리
│  └─ token/                      JWT 발급 · 검증
├─ database/
│  ├─ User/domain/entity/          사용자 관련 엔티티
│  ├─ Tech/domain/entity/          기술 태그 엔티티
│  ├─ company/domain/entity/       기업 엔티티
│  ├─ blog/domain/entity/          게시글 관련 엔티티
│  ├─ bookmark/domain/entity/      북마크 엔티티
│  ├─ ai_guide/domain/entity/      AI 가이드 엔티티
│  └─ repository/                 공통 JPA Repository
└─ features/
   ├─ user/                       로그인 · 프로필
   ├─ tech/                       기술 칩
   ├─ company/                    기업 목록 · 상세 · 글 목록
   ├─ post/                       게시글 상세
   ├─ guide/                      AI 읽기 가이드
   ├─ bookmark/                   북마크
   └─ collect/                    게시글 수집 · 관리자 API
```

각 기능은 필요한 범위에서 다음 구조를 사용합니다.

```text
ctrl/          API 요청·응답 처리
service/       업무 로직
repository/    도메인별 데이터 조회
domain/dto/    요청·응답 DTO
```

## API 응답

정상 응답은 별도의 공통 봉투 없이 DTO를 반환합니다.

오류 응답은 다음 형식을 사용합니다.

```json
{
  "code": "ERROR_CODE",
  "message": "오류 메시지",
  "field": null
}
```

인증이 필요한 API는 다음 헤더를 사용합니다.

```http
Authorization: Bearer <access-token>
```

로그인 성공 시 `Authorization`과 `Refresh-Token` 응답 헤더로 토큰을 전달합니다.

## 기여 방법

브랜치, 커밋 메시지와 PR 작성 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md)를 따릅니다.

```powershell
git config core.hooksPath .githooks
```

이 설정을 적용하면 커밋 전에 Spotless 검사와 커밋 메시지 형식 검사가 자동으로 실행됩니다.

담당 영역과 저장소 작업 규칙은 [AGENTS.md](AGENTS.md)에서 확인할 수 있습니다.

## 만든 사람

| 이름 | 담당 영역 |
|---|---|
| 신해원 | 조장 · 블로그 게시물 수집 · AI 분류 · 산출물 총괄 |
| 노건우 | 기업 상세 · 북마크 · DB 설계 |
| 박준우 | 프로필 · 기업 추천 · 구글 로그인 및 토큰 |
| 류지범 | 글 읽기 · 기업별 글 목록 · AI 가이드와 지시문 |
| 진성민 | /login · /userInput · /home · 기업 리스트 및 상세보기 |
| 김민서 | 블로그 상세보기 · 북마크 · 전체 화면 디자인 · 공통 시스템 변수 세팅 |


## 참고한 내용

- API 규격: [API 명세서](https://app.notion.com/p/3df36a4d171680cca44ae6693674b7be?v=8f336a4d171683c0b85e08892d2cef2d&source=copy_link)
- ERD: [ERD / 테이블 명세서](https://app.notion.com/p/5-cc636a4d1716822985a6013ee1e50ceb?source=copy_link)
- 화면 설계: [화면 설계](https://www.figma.com/design/PXMMTbObThkRVGTRwM9Gxh/1%EC%B0%A8-%EB%AF%B8%EB%8B%88%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8?node-id=150-405&t=XAXPg3LckIimpOmP-1)
- 브랜치 · 커밋 · PR 규칙: [CONTRIBUTING.md](CONTRIBUTING.md)
