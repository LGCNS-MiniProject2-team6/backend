# Gateway Ingress

Ingress는 API Gateway만 backend로 연결한다. 실제 도메인, TLS Secret, IngressClass가
확정되지 않았으므로 기본 kustomization에는 포함하지 않는다.

외부에 노출하면 안 되는 경로:

- `/actuator/**`
- `/encrypt`, `/decrypt`
- Eureka와 Config Server의 관리 경로
