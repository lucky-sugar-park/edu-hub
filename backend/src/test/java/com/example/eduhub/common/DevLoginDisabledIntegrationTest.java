package com.example.eduhub.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** dev 로그인이 꺼진 환경(운영 상당)에서는 로그인 경로가 404다(A7). test 프로필의 true를 덮어 쓴다. */
@SpringBootTest(properties = "eduhub.auth.dev-login-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DevLoginDisabledIntegrationTest {

  @DynamicPropertySource
  static void randomSecret(DynamicPropertyRegistry registry) {
    registry.add(
        "eduhub.auth.jwt-secret",
        () -> {
          byte[] raw = new byte[32];
          new SecureRandom().nextBytes(raw);
          return Base64.getEncoder().encodeToString(raw);
        });
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ApplicationContext context;

  @Test
  void devLoginIsNotFoundNotUnauthorized() throws Exception {
    assertThat(context.getBeanNamesForType(DevAuthController.class)).isEmpty();
    mockMvc
        .perform(
            post("/api/v1/dev/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":\"E1001\"}"))
        .andExpect(status().isNotFound());
  }
}
