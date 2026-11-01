package com.recu.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/** Binder가 미해결 placeholder를 키 문자열로 사용하는 것을 방지한다. */
@Configuration(proxyBeanMethods = false)
public class ConfigEncryptionKeyValidation {

  public ConfigEncryptionKeyValidation(@Value("${encrypt.key}") String key) {
    if (key.isBlank() || key.contains("${")) {
      throw new IllegalStateException("ENCRYPT_KEY must be provided and non-blank.");
    }
  }
}
