package com.recu.blog.bootstrap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "recu.blog.enabled", havingValue = "true", matchIfMissing = true)
@EnableFeignClients(basePackages = "com.recu.blog.client")
@ComponentScan(
    basePackages = {
      "com.recu.blog.features",
      "com.recu.blog.database",
      "com.recu.blog.client",
      "com.recu.blog.internal",
      "com.recu.blog.commons"
    })
public class BlogFeaturesConfiguration {}
