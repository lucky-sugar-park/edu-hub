package com.example.eduhub.auth;

import static com.example.eduhub.auth.WebMvcSecurityTestConfig.admin;
import static com.example.eduhub.auth.WebMvcSecurityTestConfig.employee;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MeController.class)
@Import(WebMvcSecurityTestConfig.class)
class MeControllerTest {

  @Autowired private MockMvc mockMvc;

  // A6
  @Test
  void returnsUserFromToken() throws Exception {
    mockMvc
        .perform(get("/api/me").with(employee("E1001")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.employeeNo").value("E1001"))
        .andExpect(jsonPath("$.role").value("EMPLOYEE"));
  }

  @Test
  void returnsAdminRole() throws Exception {
    mockMvc
        .perform(get("/api/me").with(admin("A0001")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(2))
        .andExpect(jsonPath("$.employeeNo").value("A0001"))
        .andExpect(jsonPath("$.role").value("ADMIN"));
  }

  @Test
  void withoutTokenIsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }
}
