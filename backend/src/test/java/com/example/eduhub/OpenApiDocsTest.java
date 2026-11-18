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
        .andExpect(jsonPath("$.paths['/api/health'].get.summary").exists())
        .andExpect(jsonPath("$.paths['/api/health'].get.tags[0]").value("Health"))
        .andExpect(jsonPath("$.paths['/api/health'].get.responses['200']").exists());
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
        .andExpect(
            jsonPath("$.paths['/api/v1/admin/sessions/{sessionId}'].put.responses['503']").exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/admin/sessions/{sessionId}/close'].post.responses['503']")
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
  void apiDocsIncludeMyEnrollmentsListWithoutEmployeeHeader() throws Exception {
    String path = "$.paths['/api/v1/me/enrollments'].get";
    String params = path + ".parameters";
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath(path + ".summary").exists())
        .andExpect(jsonPath(path + ".responses['200']").exists())
        .andExpect(jsonPath(path + ".responses['400']").exists())
        .andExpect(jsonPath(path + ".responses['401']").exists())
        .andExpect(jsonPath(path + ".security[0].bearerAuth").exists())
        .andExpect(jsonPath(params + "[?(@.in=='header')]").value(org.hamcrest.Matchers.empty()))
        .andExpect(
            jsonPath(params + "[?(@.name=='employeeId')]").value(org.hamcrest.Matchers.empty()))
        .andExpect(
            jsonPath(params + "[?(@.name=='pageable')]").value(org.hamcrest.Matchers.empty()))
        .andExpect(jsonPath(params + "[?(@.name=='sort')]").value(org.hamcrest.Matchers.empty()))
        .andExpect(
            jsonPath(params + "[?(@.name=='status')]").value(org.hamcrest.Matchers.hasSize(1)));
  }

  @Test
  void apiDocsIncludeEnrollmentAdminEndpointsWithAuthResponses() throws Exception {
    String roster = "$.paths['/api/v1/admin/sessions/{sessionId}/roster'].get";
    String completions = "$.paths['/api/v1/admin/sessions/{sessionId}/completions'].post";
    for (String path : new String[] {roster, completions}) {
      String params = path + ".parameters";
      mockMvc
          .perform(get("/v3/api-docs"))
          .andExpect(status().isOk())
          .andExpect(jsonPath(path + ".summary").exists())
          .andExpect(jsonPath(path + ".tags[0]").value("EnrollmentAdmin"))
          .andExpect(jsonPath(path + ".responses['200']").exists())
          .andExpect(jsonPath(path + ".responses['400']").exists())
          .andExpect(jsonPath(path + ".responses['401']").exists())
          .andExpect(jsonPath(path + ".responses['403']").exists())
          .andExpect(jsonPath(path + ".responses['404']").exists())
          .andExpect(jsonPath(path + ".security[0].bearerAuth").exists())
          .andExpect(jsonPath(params + "[?(@.in=='header')]").value(org.hamcrest.Matchers.empty()))
          .andExpect(
              jsonPath(params + "[?(@.name=='adminId')]").value(org.hamcrest.Matchers.empty()));
    }
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath(completions + ".responses['400']").exists())
        .andExpect(jsonPath(completions + ".responses['409']").exists())
        .andExpect(jsonPath(completions + ".responses['503']").exists())
        .andExpect(
            jsonPath("$.components.schemas.CompletionResponse.properties.failed.items.$ref")
                .exists());
  }

  @Test
  void apiDocsShowCompletionRequestListLimits() throws Exception {
    String employeeIds = "$.components.schemas.CompletionRequest.properties.employeeIds";
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components.schemas.CompletionRequest.required").isArray())
        .andExpect(jsonPath(employeeIds + ".minItems").value(1))
        .andExpect(jsonPath(employeeIds + ".maxItems").value(200))
        .andExpect(jsonPath(employeeIds + ".items.minLength").value(1))
        .andExpect(jsonPath(employeeIds + ".items.maxLength").value(20));
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

  // A10
  @Test
  void apiDocsDeclareBearerAuthScheme() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
  }

  // A10: 임시 사번 헤더는 어떤 경로에도 남지 않는다.
  @Test
  void noPathHasEmployeeIdHeaderParameter() throws Exception {
    String body =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    org.assertj.core.api.Assertions.assertThat(body).doesNotContain("X-Employee");
  }

  // A10: 관리자 API 전체(과정·회차 관리 포함)에 401·403·bearerAuth가 있다.
  @Test
  void adminApisDocument401And403() throws Exception {
    String[] ops = {
      "$.paths['/api/v1/admin/courses'].post",
      "$.paths['/api/v1/admin/courses/{courseId}'].put",
      "$.paths['/api/v1/admin/courses/{courseId}/close'].post",
      "$.paths['/api/v1/admin/courses/{courseId}/sessions'].post",
      "$.paths['/api/v1/admin/sessions/{sessionId}'].put",
      "$.paths['/api/v1/admin/sessions/{sessionId}/close'].post"
    };
    String docs =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    for (String op : ops) {
      org.assertj.core.api.Assertions.assertThat(
              (Object) com.jayway.jsonpath.JsonPath.read(docs, op + ".responses['401']"))
          .isNotNull();
      org.assertj.core.api.Assertions.assertThat(
              (Object) com.jayway.jsonpath.JsonPath.read(docs, op + ".responses['403']"))
          .isNotNull();
      org.assertj.core.api.Assertions.assertThat(
              (Object) com.jayway.jsonpath.JsonPath.read(docs, op + ".security[0].bearerAuth"))
          .isNotNull();
    }
  }

  @Test
  void publicApisHaveNoSecurityRequirement() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/courses'].get.security").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/health'].get.security").doesNotExist());
  }
}
