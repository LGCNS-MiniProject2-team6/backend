# Blog 이벤트 파이프라인 구현 계약

기준: 2026-11-02 `백엔드 작업 요청 - 블로그 이벤트 파이프라인.md`의 B 및 Blog에 해당하는 D.
작업 기준 브랜치는 최신 `backend-develop`의 `34232a7`이다. User 알림, 외부 messageProducer, Config Bus, Kafka 토픽/배포/CI·CD는 변경하지 않았다.

## 흐름

외부 수집기 → HTTP POST → requested 발행 확인 → 202 → Blog consumer 저장/커밋 → created 발행.
수집 API는 DB 저장 완료를 기다리지 않지만, Kafka의 발행 확인 전에는 202를 반환하지 않는다. DB 또는 created 발행 오류는 소비 단계의 재시도 대상이다.

## 수집 API

`POST /api/admin/collect/posts`

헤더 `Content-Type: application/json`, `X-Collect-Key: <BLOG_COLLECT_API_KEY 값>`.
새 비밀 환경변수 이름은 `BLOG_COLLECT_API_KEY`다. 값은 문서/코드에 두지 않는다. 비어 있으면 앱은 기동하되 수집 요청은 모두 401로 거절한다. 기존 userId 또는 X-Internal-Token으로 이 권한을 대체할 수 없다.

```json
{
  "companyId": 1,
  "externalPostId": "source-post-123",
  "title": "새 기술 글",
  "url": "https://example.com/posts/123",
  "publishedAt": "2026-11-01T00:00:00Z",
  "contentHtml": "<h2>Kafka</h2><p>본문</p>",
  "contentText": "본문",
  "isTech": true,
  "field": "Backend",
  "level": "L1",
  "summary": "글 요약",
  "techTagIds": [10]
}
```

필수값은 companyId(양수이며 기존 회사), title(1~300자), url(HTTP/HTTPS, 최대 500자), publishedAt(오프셋 포함 ISO 날짜), contentText(1~500000자)이다. 선택값 externalPostId는 최대 255자, contentHtml 최대 500000자, field 최대 10자, level 최대 4자, summary 최대 300자다. techTagIds는 최대 100개로, 기존 기술 ID만 받는다. 생략하면 빈 목록이다. company/tech ID는 Blog 소유 DB에서만 확인한다.

본문 HTML을 생략하면 contentText를 HTML 이스케이프한 문단으로 저장한다. DB id, collectedAt, charCount, hasCode는 Blog에서 계산한다. 외부 URL을 서버에서 가져오는 수집/크롤링 기능은 추가하지 않았다. section/category의 새로운 추론이나 AI 분류는 하지 않으며 기존 조회 코드의 본문 fallback을 사용한다.

성공은 `202 {"eventId":"UUID","status":"ACCEPTED"}`다. 인증 실패는 401, 필수값/참조 ID 검증 실패는 400, Kafka 발행 실패는 503이다. 오류는 기존 `{code,message,field}` 형식을 사용한다.

## 이벤트 계약

공통 필드는 eventId(UUID), eventType, eventVersion(1), occurredAt(UTC ISO 문자열, Z), source(blog-service), data다. Java 클래스 타입 헤더를 발행하지 않고 수신 타입 헤더도 신뢰하지 않는다.

| 토픽 | Key | data | 처리 |
| --- | --- | --- | --- |
| blog-post.requested.v1 | url | 수집 API 요청과 동일 | group blog-service, 최초 earliest |
| blog-post.created.v1 | postId의 문자열 | postId, companyId, title, url, techTagIds, field, companyName | User 담당 consumer에서 처리 |

created의 techTagIds, field, companyName은 origin/feat/82/user-kafka(e784661)의 BlogPostCreatedData 계약에 맞춘 필드다. 특히 User의 직무 일치 알림은 field를 사용한다. 기존 Blog DB에 저장된 값을 보내며 누락된 기술/직무를 추론해서 채우지 않는다. User 코드는 가져오거나 수정하지 않았다.

## 저장과 중복

기존 BlogPostEntity/CompanyEntity/TechTagEntity 및 repository를 재사용한다. 새 엔티티, 테이블, 컬럼은 없다. 게시글과 태그 연결은 같은 DB 트랜잭션에서 저장하고, 커밋 후 created를 보낸다. publishedAt과 collectedAt은 UTC로 저장한다.

동일 URL은 새 글/태그를 만들거나 기존 내용을 덮어쓰지 않는다. 경쟁 삽입은 UNIQUE 충돌 트랜잭션이 롤백된 다음 저장된 글을 조회한다. 기존 URL을 다시 받으면 created를 다시 보낸다. 이는 저장 성공 뒤 발행 실패/프로세스 중단을 복구하기 위한 선택이다.

created eventId는 `UUID.nameUUIDFromBytes(UTF8("blog-post.created.v1:" + postId))`로 고정한다. occurredAt도 최초 저장 시각을 사용한다. 재전송 시 기존 저장 데이터/태그를 발행한다. User는 이 고정 eventId와 userId에 대한 중복 처리가 필요하다. exactly-once 전달이나 알림 중복 제거를 Blog 단독으로 보장하지 않는다. Outbox는 요청대로 도입하지 않았다.

## Kafka 설정

bus.yml의 KafkaProperties(접속/보안 설정)를 복사해 Blog 전용 template/factory를 구성한다. 전역 consumer.group-id/serializer 및 Config Bus 설정은 변경하지 않는다. offset 자동 커밋은 끄고 AckMode.BATCH를 유지한다. 애플리케이션 토픽 생성은 없다.

일시 오류는 1초·2초·4초로 3회 재시도한다. 잘못된 JSON, envelope, URL Key, 회사/태그 참조는 바로 requested DLT로 보낸다. DLT는 원본 토픽 뒤 `-dlt`, 원본 파티션으로 명시한다. DLT 발행 자체가 실패하면 예외를 유지하여 원본 offset을 성공 처리하지 않는다. 역직렬화 실패 메시지의 원본 byte[]도 DLT에서 보존한다.

## 로컬 검증

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home bash ./gradlew :services:blog-service:test
```

테스트는 임베디드 KRaft Kafka(3개 파티션, 토픽 자동 생성 off)와 H2 MariaDB 모드를 사용한다. 테스트에서만 네 개 토픽을 미리 만든다. 기존 Blog 조회/인증, HTTP 인증·검증, 요청 ack 대기/실패, 저장 및 created, 중복 URL/경쟁 삽입, created 발행 실패 후 복구, 3회 재시도와 DLT, 잘못된 envelope/Key/JSON을 검증한다.

## 팀이 준비한 IntelliJ 로컬 실행 구성

로컬 준비 파일은 이미 origin/develop(dd3984a)에 있다. 현재 작업 기준 backend-develop(34232a7)에는 아직 반영되지 않았다는 뜻이며, 팀이 실행 구성을 준비하지 않았다는 뜻은 아니다. 새 구성이나 별도의 개발용 인프라를 만들 필요는 없다.

준비된 파일은 .run/의 Application 6개 및 Compound 2개, scripts/local-setup.sh, 해당 스크립트가 호출하는 kafka/scripts 및 kafka/topics다. 팀에서 묶어 전달한 파일 또는 해당 upstream 구성 전체를 기준으로 사용한다. MSA 소스 전체를 다른 브랜치에서 임의 병합하는 작업과는 구분한다. 이번 Kafka Blog 변경에는 다른 담당의 실행 설정/인프라 변경을 섞지 않았다.

backend 옆에 recu-config를 받은 후 로컬 준비 스크립트를 실행하고, IntelliJ에서 Gradle Reload 및 JDK 17을 확인한다. `1. Platform (Eureka + Config)`가 준비된 다음 `2. Services (Gateway + User + AI + Blog)`를 실행한다. 기존 DB 포트가 사용 중이면 안내대로 DB_PORT를 지정한다.

Local - Blog.run.xml은 .env.local 및 .env.local.blog를 읽도록 이미 구성돼 있다. 수집 API를 호출하려면 로컬의 .env.local.blog 또는 해당 실행 구성의 환경변수에 BLOG_COLLECT_API_KEY를 추가한다. 외부 호출자는 같은 값을 X-Collect-Key로 보낸다. 실제 값은 코드/공유 파일에 기록하지 않으며, 이 작업에서는 기존 환경변수 파일을 읽거나 수정하지 않았다.

## k3s 반영과 로컬 검증의 구분

CI/CD 미구성 상태에서는 로컬 코드 변경이나 PR 머지가 k3s에 자동 반영되지 않는다. 수동 이미지 빌드/태그 갱신/배포가 필요하며 이는 다른 담당 범위다. 이번에 확인한 Docker + 6앱 결과는 로컬 통합 검증 결과로만 기록한다. k3s 배포/실행을 확인했다는 의미가 아니며, 이를 위해 Blog 앱 로직을 바꿀 필요도 없다.

## 전체 환경 연동 시 확인 사항

현재 backend-develop에는 안내 파일의 scripts/local-setup.sh 및 .run 구성이 아직 없다. develop에 있는 인프라/도구 변경을 이 작업에 임의 병합하지 않았다. 최초에는 Docker가 실행되지 않아 임베디드 검증만 수행했으나, 2026-11-03 Docker를 실행하고 아래의 별도 포트 구성으로 팀 MariaDB + 실제 Kafka + 전체 6앱 기동 및 Blog 파이프라인까지 추가 검증했다. User의 알림 소비까지 검증한 것은 아니다.

실제 로컬 기동에는 기존 DB/Config 환경에 BLOG_COLLECT_API_KEY를 추가하고, 요청 문서의 네 개 토픽이 생성된 Kafka를 사용한다. Kafka를 띄우지 않는 진단 시에만 `recu.blog.events.listener-auto-startup=false`로 consumer 자동 시작을 끌 수 있다.

배포 전에는 다음을 확인한다. 이 문서는 공유 메시지 전송이나 배포를 수행한 기록이 아니다.

1. 수집 API 경로/본문 및 X-Collect-Key 방식과 새 Secret 이름을 역할 1/2와 합의한다. Gateway JWT 필터가 수집 경로에서 사용자 JWT를 강제하지 않는지도 확인한다.
2. messageProducer 담당자는 요청 payload를 이 계약으로 변환하고, HTTP 2xx에만 다음 글로 진행한다.
3. User 담당자는 created의 techTagIds, 고정 eventId와 사용자별 중복 처리 계약을 확인한다.
4. 역할 3의 토픽 생성/로컬 설정이 통합 브랜치에 반영되고 User consumer가 준비된 뒤 저장 → 알림, DLT 재투입, lag까지 확인한다. Blog 저장/완료 이벤트와 전체 앱 기동은 아래 실제 로컬 환경에서 확인했다.

## 이번 로컬 확인 결과

2026-11-03 실행 기준 Blog 테스트 31개(기존 회귀 20개, 이벤트 통합 10개, 발행 ack 단위 1개)가 모두 통과했다. 변경 Java 파일만 대상으로 한 Spotless 검사와 git diff --check도 통과했다.

일반 :services:blog-service:spotlessCheck는 기준 브랜치에 이미 존재하던 11개 파일의 서식 차이로 실패한다. 범위 밖 파일을 일괄 재포맷하지 않고 유지했다. 변경 파일 검사에는 저장소 바깥 임시 Gradle init script로 target만 제한했으며, 공통 Gradle 설정은 바꾸지 않았다.

## 실제 Docker 및 6앱 추가 검증 (2026-11-03)

원래 실행 중이던 개발 서비스/DB를 종료하지 않고 별도 포트로 검증했다. 팀 MariaDB 이미지 `junwooqkr5302/recu-mariadb:cd63175`(linux/amd64), Kafka `apache/kafka:4.3.2`, 현재 작업 브랜치의 6개 bootJar, 별도 clone한 최신 recu-config를 사용했다. 실제 Gemini 호출은 하지 않았다.

| 구성 | 검증용 포트 | 결과 |
| --- | --- | --- |
| MariaDB | 13307 | health 정상, 회사 8개/글 382개 시드 확인 |
| Kafka | 19092 | 자동 토픽 생성 off, Bus 및 이벤트/DLT 5개 토픽 사전 생성 |
| Eureka | 18761 | health UP |
| Config | 18888 | health UP, 로컬 Git 설정 로드 |
| Gateway | 18080 | health UP, Blog 공개 조회 200, 수집 요청 202 |
| User | 18082 | health UP, Eureka UP |
| AI | 18083 | health UP, Eureka UP |
| Blog | 18084 | health UP, Eureka UP, 실제 DB/Kafka 흐름 통과 |

Eureka에는 Config/Gateway/User/AI/Blog 5개가 모두 UP으로 등록됐다.

같은 URL을 Blog 직접 호출 2회 및 Gateway 경유 1회 전송했다. 모두 202였고 MariaDB에는 1건만 저장됐다. created 메시지는 3건이었으며 postId Key, eventId 및 UTC occurredAt이 동일했고 기술 태그 ID도 보존됐다. created 재발행은 문서화한 중복/실패 복구 정책에 따른 정상 동작이다.

수집 키 없는 요청 401, 잘못된 URL 400, Kafka에 직접 넣은 잘못된 JSON의 requested DLT 이동과 원본 Key/본문 보존도 확인했다. 3회 재시도 및 created 발행 실패 복구는 앞의 자동화 테스트에서 확인한 항목이며, 이번 Docker 실행에서 DB 장애를 주입한 것은 아니다.

User 알림 consumer와 알림 중복 제거의 종단 검증은 현재 담당 범위 밖이며 미검증이다. 인프라/다른 서비스 코드를 수정하거나 배포한 것은 아니다. 검증용 6개 JVM과 DB/Kafka 컨테이너는 확인 후 종료하고 기존 프로세스/컨테이너는 그대로 유지했다. 비밀값은 저장소 밖 권한 제한 디렉터리에 무작위로 생성했으며 코드/로그 요약/문서에 싣지 않았다.


## 후속 계약 확인 (2026-11-03)

로컬 실행 안내에 대한 사용자 설명을 반영했다. User Kafka 최신 원격 브랜치를 읽어 created data에 field 및 companyName을 추가했고, 실제 Kafka를 통과한 JSON에서 Backend/Alpha가 유지되는지 자동화 통합 테스트에 검증을 추가했다. 앞의 Docker 수동 검증 기록은 이 두 필드 추가 전 실행 결과다. User 코드 병합이나 User 알림의 종단 검증은 하지 않았다.
