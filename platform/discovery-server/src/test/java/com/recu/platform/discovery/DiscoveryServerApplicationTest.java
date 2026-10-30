package com.recu.platform.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryServerApplicationTest {

  @Autowired private TestRestTemplate rest;

  @Test
  void servesDashboardAndRegistryWithoutExternalDependencies() {
    var dashboard = rest.getForEntity("/", String.class);
    assertThat(dashboard.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(dashboard.getBody()).contains("Instances currently registered with Eureka");

    var headers = new HttpHeaders();
    headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
    var registry = rest.exchange("/eureka/apps", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    assertThat(registry.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(registry.getBody()).contains("applications");
  }

  @Test
  void exposesHealthProbesWithoutExposingEnvironment() {
    for (String path : new String[] {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"}) {
      var health = rest.getForEntity(path, String.class);
      assertThat(health.getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
      assertThat(health.getBody()).contains("\"status\":\"UP\"");
    }
    assertThat(rest.getForEntity("/actuator/env", String.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
