package com.example.eduhub;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocsTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void apiDocsIncludeHealthEndpoint() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/health'].get").exists());
  }

  @Test
  void apiDocsExcludeScreenRoutes() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/admin']").doesNotExist())
        .andExpect(jsonPath("$.paths['/courses/{id}']").doesNotExist());
  }

  @Test
  void apiDocsIncludeCourseEndpoints() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/courses'].get.summary").exists())
        .andExpect(jsonPath("$.paths['/api/v1/courses/{id}'].get.summary").exists())
        .andExpect(jsonPath("$.paths['/api/v1/courses/{id}'].get.responses['404']").exists());
  }

  @Test
  void apiDocsIncludeCourseAdminEndpoints() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/admin/courses'].post.tags[0]").value("CourseAdmin"))
        .andExpect(jsonPath("$.paths['/api/v1/admin/courses'].post.responses['400']").exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/admin/courses/{courseId}'].put.responses['409']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/admin/courses/{courseId}/close'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/admin/courses/{courseId}/sessions'].post").exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/admin/sessions/{sessionId}'].put.responses['409']").exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/admin/sessions/{sessionId}/close'].post.responses['409']")
                .exists())
        .andExpect(jsonPath("$.components.schemas.SessionRequest.required").isArray());
  }

  @Test
  void apiDocsIncludeEnrollmentEndpoint() throws Exception {
    String path = "$.paths['/api/v1/sessions/{sessionId}/enrollments'].post";
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath(path + ".tags[0]").value("Enrollment"))
        .andExpect(jsonPath(path + ".responses['201']").exists())
        .andExpect(jsonPath(path + ".responses['404']").exists())
        .andExpect(jsonPath(path + ".responses['409']").exists())
        .andExpect(jsonPath(path + ".responses['503']").exists());
  }

  @Test
  void apiDocsIncludeMyEnrollmentEndpointAndWaitingNumber() throws Exception {
    String path = "$.paths['/api/v1/sessions/{sessionId}/enrollments/me'].get";
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath(path).exists())
        .andExpect(jsonPath(path + ".tags[0]").value("Enrollment"))
        .andExpect(jsonPath(path + ".responses['200']").exists())
        .andExpect(jsonPath(path + ".responses['404']").exists())
        .andExpect(
            jsonPath("$.components.schemas.EnrollmentResponse.properties.waitingNumber").exists());
  }

  @Test
  void apiDocsIncludeCancelEndpointAndCancelledStatus() throws Exception {
    String path = "$.paths['/api/v1/sessions/{sessionId}/enrollments/me/cancel'].post";
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath(path).exists())
        .andExpect(jsonPath(path + ".tags[0]").value("Enrollment"))
        .andExpect(jsonPath(path + ".responses['200']").exists())
        .andExpect(jsonPath(path + ".responses['404']").exists())
        .andExpect(jsonPath(path + ".responses['409']").exists())
        .andExpect(jsonPath(path + ".responses['503']").exists())
        .andExpect(
            jsonPath("$.components.schemas.EnrollmentResponse.properties.status.enum")
                .value(org.hamcrest.Matchers.hasItem("CANCELLED")));
  }
}
