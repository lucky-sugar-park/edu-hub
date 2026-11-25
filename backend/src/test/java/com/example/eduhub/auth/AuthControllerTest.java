package com.example.eduhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(WebMvcSecurityTestConfig.class)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;

  private static final String BODY = "{\"employeeNo\":\"E1001\",\"password\":\"pw\"}";

  @Test
  void returnsTokenAndUserOnSuccessWithoutAuthentication() throws Exception {
    given(authService.login("E1001", "pw"))
        .willReturn(
            new LoginResponse(
                "token-value", 3600, new MeResponse(3L, "E1001", EmployeeRole.EMPLOYEE)));

    mockMvc
        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("token-value"))
        .andExpect(jsonPath("$.expiresIn").value(3600))
        .andExpect(jsonPath("$.user.id").value(3))
        .andExpect(jsonPath("$.user.employeeNo").value("E1001"))
        .andExpect(jsonPath("$.user.role").value("EMPLOYEE"));
  }

  @Test
  void returns401WithFixedMessageOnInvalidCredentials() throws Exception {
    given(authService.login("E1001", "pw")).willThrow(new InvalidCredentialsException());

    mockMvc
        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.message").value("사번 또는 비밀번호가 올바르지 않습니다"));
  }

  @Test
  void returns400WhenFieldsAreBlank() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeNo\":\"\",\"password\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("employeeNo: 필수 값입니다")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("password: 필수 값입니다")));
    verifyNoInteractions(authService);
  }

  private void login(String employeeNo, String password) throws Exception {
    pending =
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"employeeNo\":\"" + employeeNo + "\",\"password\":\"" + password + "\"}"));
  }

  private org.springframework.test.web.servlet.ResultActions pending;

  @Test
  void employeeNoBoundary20Passes21Fails() throws Exception {
    given(authService.login("E".repeat(20), "pw")).willThrow(new InvalidCredentialsException());

    login("E".repeat(20), "pw");
    pending.andExpect(status().isUnauthorized());

    login("E".repeat(21), "pw");
    pending
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("employeeNo: 20자 이하여야 합니다"));
  }

  @Test
  void passwordBoundary72BytesPasses73Fails() throws Exception {
    given(authService.login("E1001", "a".repeat(72))).willThrow(new InvalidCredentialsException());

    login("E1001", "a".repeat(72));
    pending.andExpect(status().isUnauthorized());

    login("E1001", "a".repeat(73));
    pending
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.message").value("password: 72바이트 이하여야 합니다"));
  }

  @Test
  void koreanPasswordOf25CharsIs75BytesAndFails() throws Exception {
    login("E1001", "가".repeat(25));
    pending
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("password: 72바이트 이하여야 합니다"));
    verifyNoInteractions(authService);
  }

  @Test
  void koreanPasswordOf24CharsIs72BytesPasses() throws Exception {
    given(authService.login("E1001", "가".repeat(24))).willThrow(new InvalidCredentialsException());

    login("E1001", "가".repeat(24));
    pending.andExpect(status().isUnauthorized());
  }

  @Test
  void requestToStringHidesPassword() {
    assertThat(new LoginRequest("E1001", "secret-pw").toString())
        .contains("E1001")
        .doesNotContain("secret-pw");
  }
}
