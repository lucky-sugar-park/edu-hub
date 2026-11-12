package com.example.eduhub.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SpaForwardController.class)
@ActiveProfiles("test")
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
  void unknownScreenIsNotForwarded() throws Exception {
    mockMvc.perform(get("/no-such-screen")).andExpect(status().isNotFound());
  }
}
