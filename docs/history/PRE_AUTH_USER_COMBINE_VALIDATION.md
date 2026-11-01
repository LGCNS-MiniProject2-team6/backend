# Auth·User 통합 이전 검증 이력

이 문서는 #17 이전 구성(Auth/User/AI/Blog 4개)의 당시 검증 기록이다. 현재 실행 명령·등록 개수는 ../AUTH_USER_COMBINE_GUIDE.md를 따른다.


## CONFIG_SERVER_GUIDE

## 구현 검증 기록 (2026-10-31 KST)

- 전체 `test spotlessCheck bootJar` 성공(변경 없는 기존 테스트는 Gradle 캐시 사용)
- 임시 Git 저장소로 12개 서비스/프로필 조합 및 응답 Git commit 검증 성공
- 무인증·잘못된 인증의 설정 요청 401 확인
- 네 서비스의 고유 중앙 version 값 수신 및 Health/Probe UP 확인
- Config Server + 네 서비스의 Eureka UP 및 등록 포트 일치 확인
- 잘못된 Config 인증을 받은 클라이언트의 fail-fast 종료 확인
- 실제 첨부 비밀값은 테스트에 사용하거나 신규 소스/YAML에 복사하지 않음
- 별도 원격 Config 저장소 생성/업로드 및 GitHub HTTPS 인증은 미수행
- 최종 프로필 분리 후 `verify-config.py` 재검증 성공, `verify-msa.py`의 standalone 진단도 성공
- platform 단독 프로젝트에서 Config Server 포맷 검사·독립 JAR 빌드 성공


## REMOTE_CONFIG_GUIDE

## 이번 검증 기록

- 원격 recu-config가 Private이고 main이 기본 브랜치인 것을 확인
- 현재 PC Git 인증으로 private 저장소 ls-remote 및 clone 성공
- 원격 main에는 초기 README만 존재함을 확인
- 원격 설정 업로드 및 main 반영은 검토 승인 전이므로 미수행
- Config Server의 실제 HTTPS/JGit 인증 및 main 기반 네 서비스 실행은 업로드·인증 준비 후 최종 검증 필요

- 전체 Gradle test/spotlessCheck/bootJar 성공
- recu-config 후보 커밋 `46c63d5`의 snapshot으로 12개 profile/service 조회 및 Git commit 일치 확인
- 후보 설정으로 네 서비스의 dev 프로필 실제 기동·중앙 설정 수신, Config 포함 Eureka UP 등록 확인
- 무인증/잘못된 Config 인증 401 및 잘못된 인증의 Client 기동 실패 확인
- 기존 임시 Git/local 프로필 통합 검증 회귀 통과
- 인증정보가 든 URI, HTTP URI, slash 포함 label을 거부하는 입력 검사 통과
- 검증 과정에서 원본 recu-config 작업 브랜치와 worktree가 유지됨을 확인

## 최종 원격 연결 확인 (2026-11-01 KST)

위의 업로드 전 기록 이후 recu-config PR #1이 main에 반영되었고, 사용자가 승인된 Git 토큰으로 IntelliJ에서 실행했다.

- 실행 중인 Config Server의 Git URI가 실제 HTTPS recu-config 주소이고 label이 main임을 확인
- 인증된 `/blog-service/local/main` 조회 성공
- 응답 version `5f117dbbb29557b0f34a5eb3fd181f52f2dce79c`가 GitHub recu-config/main의 실제 commit과 일치
- 네 서비스 `/actuator/info`에서 source=recu-config, version=v1, profile=local 및 해당 service 이름 확인
- 사용자 Eureka 화면에서 CONFIG-SERVER 및 네 도메인 서비스 모두 UP 확인
- Git 토큰은 조회·출력하거나 파일에 기록하지 않음

따라서 로컬 PC에서 실제 Private GitHub/main → Config Server → 네 서비스의 연결이 확인되었다. 이는 AWS 배포 검증과는 별개다. 최초 업로드 전 후보 검증 기록은 이력을 위해 유지한다.


## GATEWAY_GUIDE

## 구현 검증 기록 (2026-11-01 KST)

- 전체 `test spotlessCheck bootJar` 성공(기존 변경 없는 테스트는 Gradle 캐시 사용)
- `-p platform :api-gateway:spotlessCheck :api-gateway:bootJar` 성공
- 별도 recu-config 후보 YAML의 local/prod 라우팅·CORS 검증 성공
- backend fixture의 prod 검증에서 FRONTEND_ORIGIN을 Gateway에만 주입해 설정 수신 확인
- 14개 요청의 대상 서비스·AI 우선순위·경로·쿼리·메서드·본문·토큰 보존 및 X-User-Id 제거 확인
- 허용 Origin preflight/실요청 성공, 거부 Origin 403, 미매칭 OPTIONS 처리 확인
- 응답 토큰 노출 헤더 및 중복 Allow-Origin/Allow-Credentials 제거 확인
- Config/Eureka/암복호화/Bus/내부 관측/미등록 경로 404 및 downstream 미전달 확인
- 중앙 api-gateway.yml을 제거한 별도 실패 시나리오에서 원인을 명시하고 기동 실패 확인
- 테스트용 JVM 및 HTTP 서버 정리 완료, 사용자 IntelliJ 프로세스는 변경하지 않음
- 실제 도메인 비즈니스 API와 프론트 브라우저 JavaScript의 최종 E2E는 백엔드 구현 후 검증 필요
- recu-config/backend 작업 브랜치의 원격 push 및 PR은 검토 승인 전이므로 미수행


## SPRING_CLOUD_BUS_GUIDE

## 5. 로컬 검증 결과 (2026-11-01)

- Java 17에서 `test spotlessCheck bootJar` 통과.
- `verify-config.py --verify-bus`: 실제 KRaft Kafka, 관리자 정상 호출 1회, 다섯 앱 v1→v2와 동일 PID/startTime 확인. 접근 제어와 Gateway 관리 경로 차단도 통과.
- `verify-gateway.py`: 14개 라우팅/CORS/헤더 보존·미매칭 404·라우팅 누락 fail-fast 통과.
- `verify-msa.py`: Config/Kafka 없는 standalone health/probe 및 Eureka 등록 통과.
- AWS·역할 3의 배포 Kafka·TLS/SASL 실접속은 공통 인프라 준비 후 후속 통합 대상이다.
