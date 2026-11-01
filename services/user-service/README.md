# User 서비스 (인증 포함)

이슈 #17부터 Auth를 별도 프로세스로 실행하지 않고 User에서 함께 개발한다.

- 앱: UserServiceApplication / spring.application.name=user-service / 기본 포트 8082
- 담당 기능: Google 로그인·JWT 발급/갱신·회원/프로필·북마크·알림
- 기존 외부 인증 경로 /api/auth 및 /api/auth/** 유지. Gateway는 경로를 제거하지 않고 User로 전달한다.
- 권장 코드 위치: com.recu.user.auth 아래 인증 기능, com.recu.user 아래 회원 관련 기능. 별도 com.recu.auth 최상위 패키지를 만들면 기본 컴포넌트 스캔에 포함되지 않는다.
- 현재는 실행 골격이다. Auth에도 이관할 업무 코드가 없었으므로 로그인/JWT 구현을 새로 추가하지 않았다. 기존 루트 모놀리스의 src와 DB 엔티티는 그대로다.
- Config 읽기 계정과 Kafka Bus 연결 변수는 기존 User 설정을 사용한다. Git 토큰·Bus 관리자 비밀번호는 User에 넣지 않는다.
- 전체 실행·검증: ../../docs/AUTH_USER_COMBINE_GUIDE.md
