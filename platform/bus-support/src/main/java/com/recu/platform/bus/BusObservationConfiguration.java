package com.recu.platform.bus;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(name = "spring.cloud.bus.enabled", havingValue = "true")
public class BusObservationConfiguration {

  @Bean
  @RefreshScope
  RefreshVersionInfoContributor refreshVersionInfoContributor(
      @Value("${demo.config-version:v1}") String version,
      @Value("${spring.cloud.bus.id}") String busId) {
    return new RefreshVersionInfoContributor(version, busId);
  }
}
