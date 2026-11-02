# 앱 Prometheus 지표 (#50)

## 적용 범위

Discovery(8761), Config(8888), Gateway(8080), User(8082), AI(8083), Blog(8084)에 공통 registry 의존성과 `/actuator/prometheus`를 추가했다. 기존 health/info 및 Config busrefresh 노출을 유지한다. recu-config 변경이나 Kafka 설정 변경은 필요 없다. 라이브러리 변경이므로 새 이미지 빌드·재배포가 필요하며 Bus로 적용되지 않는다.

## Config Server 전용 수집 계정

Config 지표를 익명으로 공개하지 않는다. `CONFIG_METRICS_USER`, `CONFIG_METRICS_PASSWORD`를 한 쌍으로 Config에 주입한다. 읽기·관리자 계정과 다른 아이디를 사용한다. 둘 다 생략하면 기존 Config 기능은 유지되지만 지표 수집은 불가능하며 한쪽만 설정하면 기동 실패한다.

전용 계정은 GET `/actuator/prometheus`만 접근한다. 설정 조회·암복호화·busrefresh 접근은 403이다. 기존 읽기 계정도 지표 접근 권한은 없다. 일반 health는 기존처럼 공개한다.

### 역할 2 인계

- recu-dev에 Secret `config-metrics-credentials`를 안전하게 생성한다. 키는 CONFIG_METRICS_USER, CONFIG_METRICS_PASSWORD이며 실제 값은 Git/채팅에 넣지 않는다.
- Config Deployment에 두 키를 각각 secretKeyRef로 주입한다. 기존 읽기·관리자·Git·암호화 Secret은 유지한다.
- Prometheus Pod에 같은 Secret을 readOnly 볼륨으로 `/etc/prometheus/secrets/config-metrics`에 마운트한다. Secret 파일 키 이름을 그대로 유지하고 실행 UID가 읽을 수 있도록 권한을 설정한다. subPath 대신 디렉터리 마운트를 사용한다.
- `monitoring/prometheus/prometheus.yml`은 Config를 별도 인증 job `spring-apps-config`로 수집하고 Eureka 동적 job에서는 제외한다. 역할 3은 이 변경과 Secret 마운트를 함께 적용해야 한다. 마운트가 없으면 Config target 수집은 실패한다.
- dev/prod Secret 값은 분리한다. 현재 모니터링은 dev만 배포한다. 운영 키 교체 시 Secret 반영과 Config 재시작을 조율한다.
- 플랫폼·도메인 포트는 ClusterIP/내부 통신으로 유지하고 Prometheus → 앱 포트 통신을 허용한다. 필요 시 NetworkPolicy로 접근 주체를 제한한다.
- 외부 Ingress는 `/api` 및 `/api/**` 업무 경로만 Gateway로 연결한다. Gateway 자체의 `/actuator/**`도 외부에서 차단해야 한다. 업무 라우팅이 하위 서비스 Actuator를 노출하지 않는 것만으로 Gateway 자체 관리 주소가 보호되는 것은 아니다.
- 현재 저장소에는 실행 가능한 Ingress/NetworkPolicy가 확정되지 않았으므로 실제 외부 차단은 배포 후 검증한다. 이 PR은 해당 매니페스트를 대신 생성하지 않는다.

### 역할 4 인계

이 변경이 포함된 커밋으로 앱 6개 이미지를 다시 빌드한다. dev/prod는 같은 검증된 이미지 태그를 사용한다. Config 전용 지표 인증은 배포 Secret으로 주입하며 빌드 인자로 넣지 않는다.

## 로컬 검증

backend 루트에서 Java 17로 실행한다. 실제 앱 6개를 임시 Git/임시 자격 증명/빈 포트로 띄우며 실행 중인 IntelliJ 앱과 공용 설정은 변경하지 않는다.

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) bash ./gradlew test spotlessCheck bootJar --no-daemon
JAVA_HOME=$(/usr/libexec/java_home -v 17) python3 scripts/verify-config.py --verify-prometheus --verify-encryption
```

앱 6개의 Prometheus 텍스트 응답·JVM 메모리·HTTP 요청 지표, Health/Probe, Config 인증과 전용 지표 계정의 권한 제한, 기존 암복호화를 확인한다. Kafka는 사용하지 않는다.

IntelliJ 수동 확인은 재빌드·재시작 후 Postman GET `http://localhost:포트/actuator/prometheus`로 한다. Config만 전용 metrics 계정 Basic Auth를 사용하고 나머지는 현재 익명 수집 방식이다. HTTP 지표는 요청을 보낸 뒤 확인하며 Hikari 지표는 DB 연결 풀이 있어야 생긴다.

역할 3의 Docker 모니터링 검증은 앱 자체를 띄우지 않는다. Config target이 DOWN인 것은 정상이며 이번 앱 검증과 별개다.

## 클러스터 검증 (역할 1·2·3)

1. 앱 배포 후 Prometheus Targets에서 Discovery 고정 대상, Config 인증 대상, Eureka 발견 Gateway/User/AI/Blog가 UP인지 확인한다.
2. Config가 동적 job에도 중복 수집되지 않는지 확인한다. 401/403이면 전용 계정·Secret 마운트와 실제 이미지 버전을 대조한다.
3. Grafana ReCu Spring Apps에서 JVM/HTTP 지표를 확인한다. Target UP은 업무 기능 정상이나 DB 연결 성공을 보장하지 않는다.
4. 외부 주소의 `/actuator/prometheus`, `/actuator/info`, `/actuator/busrefresh` 접근 차단을 확인한다.
