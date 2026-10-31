package com.recu.platform.gateway;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ApiGatewayApplication {

  @Bean
  ApplicationRunner requireGatewayRoutes(GatewayProperties properties) {
    return args -> {
      if (properties.getRoutes().isEmpty()) {
        throw new IllegalStateException(
            "Gateway routes are empty. Check api-gateway.yml in recu-config and CONFIG_GIT_LABEL.");
      }
    };
  }

  public static void main(String[] args) {
    SpringApplication.run(ApiGatewayApplication.class, args);
  }
}
