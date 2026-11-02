# MSA 플랫폼 문서

현재 소스 기준: **User(인증 포함)·AI·Blog** 3개 업무 서비스 + Discovery·Config·Gateway. Java 17 / Spring Boot 3.4.5 / Spring Cloud 2024.0.2를 사용한다.

## 읽는 순서

| 목적 | 문서 |
|---|---|
| IntelliJ 실행·포트·모듈·통합 검증 | [로컬 실행](MSA_LOCAL_GUIDE.md) |
| 중앙 설정·읽기 인증·Private Git 연결 | [Config Server](CONFIG_SERVER_GUIDE.md) |
| Gateway 경로·CORS·헤더 계약 | [Gateway](GATEWAY_GUIDE.md) |
| 설정값 암호화·Postman 검증·키 관리 | [설정 암호화](CONFIG_ENCRYPTION_GUIDE.md) |
| Kafka 연결·Postman 갱신·무재시작 검증 | [Spring Cloud Bus](SPRING_CLOUD_BUS_GUIDE.md) |
| 역할 2·4에게 전달할 배포 계약 | [Kubernetes 인계](PLATFORM_K8S_HANDOFF.md) |
| 팀원 확인이 필요한 항목·담당자·완료 기준 | [검토 목록](REVIEW_CHECKLIST.md) |
| develop → main 소스 검사와 보호 설정 | [main 정책](MAIN_BRANCH_POLICY.md) |

## 현재 구현 범위

- 플랫폼 로컬 검증: Config 읽기 인증, 암복호화·관리자 권한, Eureka 등록, Gateway 라우팅·CORS, 실제 테스트 Kafka의 Bus 갱신을 확인했다.
- Config 직접 실행에는 **ENCRYPT_KEY가 필수**다. 읽기/Git/관리자 계정과 암호화 키는 용도가 다르다.
- Kafka 준비 전 Bus 기본값은 **false**다. 준비 후 Config·Gateway·User·AI·Blog 다섯 앱에 같은 환경 연결을 적용한다.
- 세 업무 모듈은 현재 실행 골격이다. 루트 모놀리스의 로그인/JWT·업무 로직·DB가 모두 이전된 상태가 아니다.
- 이미지 배포·운영 Kafka 인증·Ingress·환경 격리·성능 검증은 로컬 테스트 결과와 구분한다. 확정 전 값은 검토 목록에서 관리한다.

## 문서 관리 기준

실행 명령은 backend 루트를 기준으로 한다. 실제 키·비밀번호·토큰·Secret 실값은 기록하지 않는다. .env를 자동 로딩한다고 가정하지 않는다. 서비스/포트/변수/경로의 원천은 소스와 별도 recu-config이며 변경 시 해당 안내도 같이 갱신한다.

Auth·User 통합 과정과 과거 4개 서비스 검증 기록은 Git 이력에서 확인한다. 현재 문서에는 최신 실행 계약만 유지한다. 기존 원격 Config 링크를 위해 [이동 안내](REMOTE_CONFIG_GUIDE.md)만 남겼다.

[api.md](api.md), [data.md](data.md)는 기존 프로젝트의 규격 원천 안내다. MSA 구현 완료의 증거가 아니며 이 작업에서 원본 내용을 변경하지 않았다. 루트 README의 기존 Railway/Gemini 안내도 모놀리스 기준이다.

## 이번 정리의 파일별 변경

| 파일 | 한 줄 요약 |
|---|---|
| README.md (저장소 루트) | 현재 MSA 목차 연결, 기존 본문은 모놀리스 기록으로 구분 |
| docs/README.md | 문서 읽는 순서·현재 구현 범위·관리 기준 추가 |
| docs/REVIEW_CHECKLIST.md | 역할별 미확정 항목·담당자·완료 기준 추가 |
| docs/PLATFORM_K8S_HANDOFF.md | 암호화 반영한 플랫폼 배포 계약을 역할 2 요청 중심으로 정리 |
| docs/MSA_LOCAL_GUIDE.md | 통합된 3개 서비스·IntelliJ·검증 명령을 단일 안내로 정리 |
| docs/CONFIG_SERVER_GUIDE.md | 원격/로컬 후보 검증을 통합하고 필수 키·인증 종류 갱신 |
| docs/REMOTE_CONFIG_GUIDE.md | 외부 기존 링크 호환용 이동 안내만 유지 |
| docs/GATEWAY_GUIDE.md | 실제 경로·CORS·인증 구현 경계·검증 범위로 정리 |
| docs/CONFIG_ENCRYPTION_GUIDE.md | Postman·Git 암호문·키·Secret 인계 중복 설명 정리 |
| docs/SPRING_CLOUD_BUS_GUIDE.md | 기본 false·Kafka 연결·Postman·자동 검증을 구분 |
| docs/MAIN_BRANCH_POLICY.md | 코드 검사와 GitHub 보호 설정을 구분하고 오래된 원격 상태 단정 제거 |
| docs/AUTH_USER_COMBINE_GUIDE.md | 중복 전환 문서 삭제, 필요한 내용은 로컬 안내에 통합 |
| docs/history/PRE_AUTH_USER_COMBINE_VALIDATION.md | 구버전 검증 기록 삭제, Git 이력으로 보존 |
| config-repository-template/README.md | 운영 원천과 테스트 fixture를 구분하고 현재 문서 링크 갱신 |
| platform/discovery-server/README.md | 중복 전체 실행 설명을 제거하고 Eureka 계약·빌드만 유지 |
| services/user-service/README.md | 삭제된 안내 링크를 현재 로컬 안내로 교체하고 키 주입 대상 명확화 |

검사 범위는 Markdown 내부 링크·코드 블록·CLI 옵션·서비스/포트/키/Bus 기본값 계약이다. 문서 변경에 대해 앱 통합 테스트를 새로 실행하거나 클라우드 적용 완료를 주장하지 않는다.
