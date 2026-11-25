package com.example.eduhub.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.eduhub.auth.WebMvcSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SpaForwardController.class)
@Import(WebMvcSecurityTestConfig.class)
class SpaForwardControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void adminScreenForwardsToIndex() throws Exception {
    mockMvc
        .perform(get("/admin"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/index.html"));
  }

  @Test
  void courseDetailScreenForwardsToIndex() throws Exception {
    mockMvc
        .perform(get("/courses/39"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/index.html"));
  }

  @Test
  void loginScreenForwardsToIndex() throws Exception {
    mockMvc
        .perform(get("/login"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/index.html"));
  }

  @Test
  void myEnrollmentsScreenForwardsToIndex() throws Exception {
    mockMvc
        .perform(get("/me/enrollments"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/index.html"));
  }

  @Test
  void rosterScreenForwardsToIndex() throws Exception {
    mockMvc
        .perform(get("/admin/sessions/12/roster"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/index.html"));
  }

  @Test
  void unknownScreenIsNotForwarded() throws Exception {
    mockMvc.perform(get("/no-such-screen")).andExpect(status().isNotFound());
  }
}
