# Spring Cloud Bus·Kafka 연결·설정 갱신

[문서 목차](README.md) · [Config](CONFIG_SERVER_GUIDE.md) · [암호화](CONFIG_ENCRYPTION_GUIDE.md)

## 왜 사용하는가

Git 설정 변경을 앱마다 재시작/개별 refresh하지 않고 여러 인스턴스에 알리기 위해 사용한다.

```text
recu-config 변경·push
→ 관리자 Postman POST /actuator/busrefresh (Config 8888)
→ Kafka 토픽으로 갱신 이벤트
→ User·AI·Blog·Gateway가 각자 Config에서 자기 설정 재조회
→ 갱신 가능한 객체가 새 설정 사용
```

Kafka는 갱신 알림을 운반하며 설정값의 원천은 Config/Git이다. 한 서비스의 설정을 다른 서비스에 복사하지 않는다. 공통 application.yml을 바꾸면 이를 읽는 전체 앱이, user-service-local.yml만 바꾸면 해당 설정을 사용하는 User의 값이 바뀐다. 이벤트 수신 대상과 실제 값이 변경되는 대상은 구분한다.

참여 앱은 Config·Gateway·User·AI·Blog **5개**, 관측 대상 클라이언트는 Gateway·User·AI·Blog **4개**다. Eureka는 Bus에 참여하지 않는다. Config는 자기 Git 설정을 자기 Config Client로 읽지 않는다.

## 기본 false와 연결 변수

우리 코드의 BUS_ENABLED 기본값은 false다. Kafka가 준비되지 않아도 기존 Config/Eureka/Gateway 개발을 진행할 수 있도록 정한 값이다. true만 입력한다고 Kafka 서버/토픽이 생성되지는 않는다.

| 변수 | local | dev | prod |
|---|---|---|---|
| BUS_ENABLED | 준비 전 false, 검증 시 true | 준비 후 true | 준비 후 true |
| BUS_DESTINATION | recu-config-bus-local | recu-config-bus-dev | recu-config-bus-prod |
| KAFKA_BOOTSTRAP_SERVERS | 실제 로컬 브로커 주소 | 역할 3 확정 | 역할 3 확정 |
| KAFKA_SECURITY_PROTOCOL | 테스트는 PLAINTEXT | 역할 3 확정 | 역할 3 확정 |
| KAFKA_SASL_MECHANISM / KAFKA_SASL_JAAS_CONFIG | SASL 사용 시 설정 | 역할 3 확정 | 역할 3 확정 |

다섯 앱 모두 같은 환경의 주소·토픽을 사용한다. 특히 Config는 중앙 profile 파일을 읽지 않으므로 BUS_DESTINATION을 직접 주입한다. BUS_INSTANCE_ID를 생략하면 앱이름:HOSTNAME:서버포트이며 Pod마다 고유해야 한다. 전체 앱에 같은 ID나 고정 공통 consumer group을 넣지 않는다.

역할 3은 토픽을 미리 만든다. autoCreateTopics/autoAddPartitions=false다. broker의 advertised.listeners 주소도 클라이언트에서 도달 가능해야 한다. Kafka 인증·ACL·partition/replication은 [검토 목록](REVIEW_CHECKLIST.md)에서 합의한다. 로컬 PLAINTEXT 통과가 운영 TLS/SASL 검증을 뜻하지 않는다.

## 자동 검증

IntelliJ Terminal에서 [공통 빌드](MSA_LOCAL_GUIDE.md)를 완료하고 실행한다.

```bash
python3 scripts/verify-config.py --verify-bus
# 암호문 갱신도 함께 확인
python3 scripts/verify-config.py --verify-encryption --verify-bus --profile dev
```

GitHub·Docker·AWS 없이 실제 KRaft Kafka, 임시 Git과 계정, 실제 JAR 6개를 실행한다. Config 전용 임시 키도 자동 주입한다. 각 클라이언트가 v1인 상태에서 임시 Git에 v2를 commit하고 **호출 전에는 v1 유지**, 관리자 호출 한 번 뒤 **v2와 동일 PID/startTime**인지 확인한다. 결합 모드는 새 암호문의 복호화 값도 검사한다. 관리 401/403·Gateway 경로 차단·잘못된 Config 인증 fail-fast도 검사한다.

Logs 경로와 PASS를 확인하고, 종료 직후 echo $?가 0인지 확인한다. 임시 빈 포트라 기존 localhost:8761 화면과 별개이고 종료 후 임시 앱은 정리된다. 실제 설정 Git은 바뀌지 않는다. 실패 때도 정리 메시지는 출력되므로 그것만 보고 성공으로 판단하지 않는다.

## IntelliJ·Postman 수동 검증

1. [공통 빌드](MSA_LOCAL_GUIDE.md)를 완료한다. 별도 Terminal에서 테스트 브로커를 실행하고 켜 둔다.

```bash
java -Xms128m -Xmx512m -cp "$(cat platform/config-server/build/bus-test-classpath.txt)" \
  com.recu.platform.config.support.LocalKafkaBroker recu-config-bus-local
```

2. 로그의 KAFKA_READY=localhost:숫자 주소를 복사한다. 이 도구는 local 토픽도 생성하며 배포용 Kafka를 대체하지 않는다. 종료는 Ctrl+C다.
3. Config·Gateway·User·AI·Blog의 기존 실행 변수에 아래를 추가한다. 실제포트를 로그 숫자로 바꾼다.

```text
BUS_ENABLED=true;BUS_DESTINATION=recu-config-bus-local;KAFKA_BOOTSTRAP_SERVERS=localhost:실제포트
```

4. Config에는 기존 읽기/Git 인증과 ENCRYPT_KEY를 유지하고 관리자 두 변수를 설정한다. 읽기 계정과 다른 아이디를 쓴다. Kafka JAAS에는 세미콜론이 들어갈 수 있으므로 IntelliJ 변수 표에 따로 입력한다.
5. Kafka → Eureka → Config 준비 후 네 클라이언트를 실행한다. 다섯 참여 앱의 partitions assigned 로그를 확인한다. Eureka UP만으로 Kafka 성공을 판단하지 않는다.
6. 8082/8083/8084/8080의 /actuator/info에서 refresh.version=v1, pid/startTime을 기록한다. 포트 변경 시 실제 포트를 사용한다.
7. Config가 읽는 recu-config label에 테스트 demo.config-version=v2를 commit·반영한다. 앱은 재시작하지 않는다. 다른 팀원의 실험에 영향을 주지 않도록 반영할 label·테스트 범위를 조율한다. 실제 비밀은 info에 넣지 않는다.
8. Postman에서 다음 요청을 한 번 보낸다.

| 항목 | 값 |
|---|---|
| Method / URL | POST http://localhost:8888/actuator/busrefresh |
| Authorization | Basic Auth, Config 관리자 |
| Content-Type | application/json |
| Body | none |
| 정상 응답 | 204 No Content |

9. 네 info의 refresh.version은 v2, pid/startTime은 동일한지 확인한다. Config 자체 info 버전은 대상이 아니다. Config는 중앙 설정의 클라이언트가 아니다.

## 적용 범위·실패 확인

@RefreshScope 관측 Bean이 demo.config-version을 읽는다. info.config.version은 별도의 설정 수신 확인값이다. v1/v2는 테스트일 뿐이며 실제 운영에서는 변경할 기능 설정을 수정한다. 일반 Bean·DB 커넥션·포트·모든 라우팅이 자동 갱신되는 것은 아니다. 실제 업무 Bean의 갱신 지원을 확인하고 Java 코드 변경은 빌드·배포한다. ENCRYPT_KEY 교체는 별도 재암호화·재시작 절차다.

- 401: 관리자 인증·변수 반영 후 Config 재시작 확인.
- 403: 읽기 계정 사용 여부·관리자 권한 확인.
- 404: BUS_ENABLED·endpoint 노출·요청 주소가 Config인지 확인.
- 415: Content-Type=application/json 확인.
- 204인데 미갱신: partition 할당·같은 환경 토픽·고유 ID·공통 group 유무·Git label·상위 우선순위 키 확인. 204는 모든 클라이언트의 반영 완료 보장이 아니다.

Kafka 준비 전에는 false를 유지한다. 배포 Kafka가 준비되면 다섯 앱의 환경변수를 맞춰 재배포하고 실제 클라우드에서 같은 절차를 검증한다.
