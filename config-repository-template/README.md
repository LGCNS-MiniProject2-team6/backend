# 중앙 설정 검증 fixture

운영 원천은 별도 Private [recu-config](https://github.com/LGCNS-MiniProject2-team6/recu-config)다. 이 폴더는 초기 예제와 자동 검증용 YAML이며 실행 앱이 직접 읽는 운영 설정이 아니다. 중첩 .git을 만들거나 실제 키·비밀번호·토큰을 넣지 않는다.

| 파일 | 용도 |
|---|---|
| application.yml | 비밀이 아닌 공통 설정·info.config·Bus demo 값 |
| application-local/dev/prod.yml | 환경 구분·Bus 토픽 기본값 |
| user/ai/blog-service-local/dev/prod.yml | 서비스별 포트·Eureka·관측 이름 |
| api-gateway.yml 및 환경별 파일 | User/AI/Blog 라우팅·CORS |

인증은 User에 포함하므로 auth-service 전용 YAML은 없다. /api/auth/** 목적지는 user-service이며 User 포트는 8082다. demo.config-version은 갱신 검증값이고 info.config.version·Git commit SHA와 다르다. 실제 비밀값을 info에 넣지 않는다.

- 설정 읽기/Git 인증·원격/후보 검증: [Config 안내](../docs/CONFIG_SERVER_GUIDE.md)
- Gateway 경로·CORS: [Gateway 안내](../docs/GATEWAY_GUIDE.md)
- 암호문 형식·키 관리: [암호화 안내](../docs/CONFIG_ENCRYPTION_GUIDE.md)
- Kafka 주소·환경 토픽·갱신: [Bus 안내](../docs/SPRING_CLOUD_BUS_GUIDE.md)

자동 검증은 임시 Git으로 복사해 일부 테스트 값을 변경한다. 실제 운영 변경은 recu-config PR로 반영한다. fixture와 원격은 자동 동기화되지 않으므로 계약 변경 시 함께 검토한다.
