package com.recu.blog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.recu.blog.bootstrap")
@EnableDiscoveryClient
public class BlogServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(BlogServiceApplication.class, args);
  }
}
