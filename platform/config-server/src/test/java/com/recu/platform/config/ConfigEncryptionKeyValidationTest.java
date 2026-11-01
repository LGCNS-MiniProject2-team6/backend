package com.recu.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ConfigEncryptionKeyValidationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(ConfigEncryptionKeyValidation.class);

  @Test
  void rejectsUnresolvedKeyDuringContextStartup() {
    runner
        .withPropertyValues("encrypt.key=${recu.missing.key.for.validation.test}")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsBlankKeyDuringContextStartup() {
    runner.withPropertyValues("encrypt.key=   ").run(context -> assertThat(context).hasFailed());
  }

  @Test
  void startsWithProvidedKey() {
    runner
        .withPropertyValues("encrypt.key=verification-only-not-a-real-key")
        .run(context -> assertThat(context).hasNotFailed());
  }
}
