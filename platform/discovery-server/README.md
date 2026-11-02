# Eureka Discovery Server

서비스 등록부다. 현재 모듈은 플랫폼 Eureka·Config·Gateway와 User·AI·Blog 구성을 사용한다. 전체 실행·인증 변수는 [로컬 실행 안내](../../docs/MSA_LOCAL_GUIDE.md)를 따른다.

| 항목 | 값 |
|---|---|
| Gradle 모듈 | :platform:discovery-server |
| 실행 클래스 | DiscoveryServerApplication |
| 기본 포트 | 8761 (SERVER_PORT로 변경 가능) |
| JAR | platform/discovery-server/build/libs/discovery-server.jar |
| 대시보드 | http://localhost:8761 |
| 등록 API 기본 경로 | /eureka/ |
| Health / liveness / readiness | /actuator/health, /actuator/health/liveness, /actuator/health/readiness |

Discovery는 자기 자신을 등록/조회하지 않고 Bus·Config Client로 참여하지 않는다. ENCRYPT_KEY·Config/Git/Kafka 인증을 넣지 않는다. Config와 클라이언트가 사용하는 EUREKA_URL은 로컬 http://localhost:8761/eureka/, 클러스터에서는 같은 Namespace의 Service DNS를 쓴다.

backend 루트에서 Eureka만 빌드/실행하려면:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
bash ./gradlew :platform:discovery-server:bootJar --no-daemon
java -jar platform/discovery-server/build/libs/discovery-server.jar
```

기동만으로 업무 API·heartbeat 복구가 모두 검증된 것은 아니다. [통합 검증](../../docs/MSA_LOCAL_GUIDE.md)의 스크립트로 실제 등록 이름·포트·상태를 확인한다. 외부 공개·프로브 수치·이미지는 [인계 문서](../../docs/PLATFORM_K8S_HANDOFF.md)를 따른다.
