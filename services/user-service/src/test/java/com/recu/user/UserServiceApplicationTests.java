package com.recu.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("standalone")
class UserServiceApplicationTests {

  @Test
  @DisplayName("user-service 애플리케이션 컨텍스트 정상 로드 검증")
  void contextLoads() {}
}
