# recu-config 초기 템플릿

실행 애플리케이션 소스와 분리할 중앙 설정 Git 저장소의 초기 내용이다.
이 디렉터리의 `*.yml`을 별도 저장소 루트로 복사하고 main 브랜치에 commit한다.
여기에 중첩 `.git`을 만들지 않는다. 이 템플릿 폴더가 자동으로 원격 저장소가 되는 것은 아니다.

- application.yml: 비밀 아닌 공통 설정과 설정 수신 확인용 info.config
- application-{local,dev,prod}.yml: 환경 구분
- {auth,user,ai,blog}-service-{local,dev,prod}.yml: 서비스별 포트와 Eureka URL
- bootstrap 정보인 Config 주소/인증/앱 이름/프로필은 각 앱이 먼저 가지고 있어야 한다.
- DB와 외부 API 설정은 백엔드 계약 후 추가한다. 기존 모놀리스 비밀번호를 복사하지 않는다.
- info에는 비밀값을 넣지 않는다. 서비스 /actuator/info는 이 공개 확인값만 반환한다.
- 파일명이 다르거나 프로필이 잘못되면 공통 파일만 읽히는 경우가 있으므로 info.config.service/profile까지 확인한다.

실행 및 별도 저장소 준비 순서는 docs/CONFIG_SERVER_GUIDE.md를 참고한다.

운영 설정 원천은 별도 https://github.com/LGCNS-MiniProject2-team6/recu-config 저장소다. 이 템플릿은 초기 예제와 임시 Git 회귀 검증 fixture로 유지하며 자동 동기화되지 않는다. 원격 연결은 docs/REMOTE_CONFIG_GUIDE.md를 참고한다.
