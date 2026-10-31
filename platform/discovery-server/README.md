# Eureka Discovery Server

이슈: [#1 infra: Eureka 서버 기본 구성](https://github.com/LGCNS-MiniProject2-team6/backend/issues/1)

Auth/User/AI/Blog가 자신의 주소를 등록할 Eureka 서버다. Java 17, Spring Boot 3.4.5, Spring Cloud 2024.0.2를 사용한다. 기본 포트는 8761이며 서버 자체 등록과 레지스트리 조회는 비활성화한다.

## 빌드 구조

기존 모놀리스의 `build.gradle`, `settings.gradle`, DB 설정은 변경하지 않았다. `platform`은 독립된 Gradle 멀티 프로젝트이며 저장소 루트의 wrapper를 `-p platform`으로 재사용한다. 이후 Config/Gateway 모듈을 여기에 추가할 수 있다. 하위에 별도 Git 저장소나 wrapper를 만들지 않는다.

**모든 명령은 backend 저장소 루트에서 실행한다.** macOS 현재 작업 경로:

```bash
cd /Users/hy/Desktop/miniproject2/backend
# macOS: 설치된 Java 17 선택
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

다른 OS는 설치한 JDK 17 경로로 JAVA_HOME을 설정한다. AWS 계정, DB, Kafka, Config Server, AI 키, `.env` 없이 실행한다. 최초 빌드 시 Gradle/Maven 의존성 다운로드를 위한 인터넷 연결이 필요하다.

## 1. 자동 검증 및 JAR 빌드

```bash
bash ./gradlew -p platform :discovery-server:clean :discovery-server:test :discovery-server:bootJar
```

성공 기준은 `BUILD SUCCESSFUL`이다. 테스트는 임의의 빈 포트에서 실제 서버를 실행해 대시보드/레지스트리/Health/Probe를 확인하고 `/actuator/env`가 공개되지 않는지 검사한다.

- 테스트 보고서: `platform/discovery-server/build/reports/tests/test/index.html`
- 실행 JAR: `platform/discovery-server/build/libs/discovery-server.jar`

## 2. 로컬 실행

터미널 A에서 다음 두 방법 중 하나만 실행한다.

```bash
bash ./gradlew -p platform :discovery-server:bootRun
```

독립 JAR 실행을 검증하려면 위 프로세스를 Ctrl+C로 종료하고 실행한다.

```bash
java -jar platform/discovery-server/build/libs/discovery-server.jar
```

로그에 `Started DiscoveryServerApplication`이 나타나면 터미널을 켜둔 채 다음으로 진행한다. 종료는 Ctrl+C다.

## 3. 브라우저와 curl로 확인

브라우저에서 [Eureka 대시보드](http://localhost:8761)를 연다. `Instances currently registered with Eureka`가 보이면 성공이다. 아직 서비스가 없으므로 `No instances available`은 정상이다. Eureka 서버 자체도 등록하지 않는다.

별도 터미널 B:

```bash
curl -i http://localhost:8761/
curl -i http://localhost:8761/actuator/health
curl -i http://localhost:8761/actuator/health/liveness
curl -i http://localhost:8761/actuator/health/readiness
curl -i -H 'Accept: application/json' http://localhost:8761/eureka/apps
curl -i http://localhost:8761/actuator/env
```

기대 결과:

| 요청 | 결과 |
|---|---|
| `/` | 200, Eureka HTML 대시보드 |
| `/actuator/health` | 200, `{"status":"UP",...}` |
| liveness/readiness | 200, `{"status":"UP"}` |
| `/eureka/apps` | 200, `applications`가 포함된 JSON; 현재 목록은 비어 있음 |
| `/actuator/env` | 404; 환경변수 비공개 |

health는 probes group 정보도 포함할 수 있으므로 문자열 전체가 아닌 `status=UP`을 확인한다. `/actuator/info`는 공개하지만 환경변수/설정 상세값은 노출하지 않는다.

## 4. 이슈에 남길 검증 결과

아래 내용을 실제 실행한 결과로 채운다. 자동으로 이슈 댓글을 작성하거나 이슈를 닫지는 않는다.

```text
- [ ] Java 17에서 test / bootJar 성공
- [ ] java -jar로 독립 실행 성공
- [ ] localhost:8761 대시보드 접근 성공
- [ ] /actuator/health가 HTTP 200 / UP 반환
- [ ] liveness / readiness가 HTTP 200 / UP 반환
- [ ] /eureka/apps가 HTTP 200 반환
- [ ] /actuator/env가 HTTP 404 반환
증빙: 대시보드 캡처, BUILD SUCCESSFUL, health 응답
```

## 다른 담당자에게 전달할 계약

- 서비스 이름: `discovery-server`
- 컨테이너/내부 서비스 포트: `8761`
- 기본 도메인 서비스 이름/포트: `auth-service:8081`, `user-service:8082`, `ai-service:8083`, `blog-service:8084`
- 향후 로컬 클라이언트의 Eureka 주소: `http://localhost:8761/eureka/`
- 동일 Namespace의 K8s 클라이언트 주소: `http://discovery-server:8761/eureka/`
- Probe: `/actuator/health/liveness`, `/actuator/health/readiness`
- 환경변수: `SERVER_PORT`로 리스닝 포트 변경 가능

각 서비스에 Eureka Client 의존성을 추가하고 다음 설정을 적용하는 작업은 백엔드/후속 통합 범위다.

```yaml
spring:
  application:
    name: auth-service
server:
  port: 8081
eureka:
  client:
    serviceUrl:
      defaultZone: http://localhost:8761/eureka/
```

클라이언트별로 이름/포트를 바꾼다. `defaultZone` 대소문자를 유지한다. Pod 내부의 `localhost`는 해당 Pod 자신이므로 K8s에서는 반드시 내부 Service 주소를 사용한다. 실제 도메인 4개 등록·heartbeat·장애 복구 검증은 서비스가 준비된 후 수행한다.

## 실행이 안 될 때

- `Address already in use`: 먼저 실행한 Eureka를 종료한다. macOS에서 `lsof -nP -iTCP:8761 -sTCP:LISTEN`으로 사용 프로세스를 확인한다. 임의의 다른 프로그램을 강제 종료하지 않는다.
- 다른 포트 사용: `SERVER_PORT=18761 java -jar platform/discovery-server/build/libs/discovery-server.jar` 실행 후 확인 URL도 18761로 바꾼다.
- `Task discovery-server not found`: backend 루트인지, `-p platform`을 넣었는지 확인한다.
- Java toolchain 오류: `java -version`과 `JAVA_HOME`에서 JDK 17 설치를 확인한다.
- 의존성 다운로드 오류: Maven Central/Gradle 배포 서버 인터넷 접근과 프록시를 확인한다.
- DB/API 키 오류: 기존 모놀리스의 `bootRun`을 실행한 경우다. `-p platform :discovery-server:bootRun`을 사용한다.

현재는 단일 Eureka 서버 개발 구성이다. self-preservation 기본값을 유지하며, 경고가 보인다고 이를 임의로 끄지 않는다. 클라우드 배포에서 Eureka는 내부 Service로만 노출하고 외부 Ingress 경로를 열지 않는다. Kubernetes/이미지/CI 작업은 역할 2/4와 후속 연계한다.

참고: [Spring Cloud Netflix 4.2 공식 문서](https://docs.spring.io/spring-cloud-netflix/reference/4.2/spring-cloud-netflix.html)

## 구현 시 검증 기록 (2026-10-30 KST)

- Java 17.0.17에서 Gradle test / bootJar 성공: 테스트 2개, 실패 0개
- 독립 JAR를 기본 8761 포트로 실행하여 대시보드 HTTP 200 확인
- health / liveness / readiness HTTP 200 및 UP 확인
- registry HTTP 200, 서비스 목록이 빈 상태임을 확인
- `/actuator/env` HTTP 404 확인
- 검증 프로세스는 종료했으므로 위 실행 명령으로 직접 다시 시작 가능
- 테스트 종료 시 의존 라이브러리의 `Cannot clean connections / Client instance has been closed` 로그가 한 차례 발생했지만 테스트 실패는 없었음. 재현 여부는 후속 관찰하며 로그 레벨 변경으로 숨기지 않음
- 실제 Auth/User/AI/Blog 등록과 AWS 배포는 이번 검증 범위에 포함하지 않음
