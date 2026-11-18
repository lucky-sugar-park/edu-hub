package com.example.eduhub.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class CurrentAdminArgumentResolverTest {

  private final CurrentAdminArgumentResolver resolver =
      new CurrentAdminArgumentResolver(new AdminProperties(List.of("ADMIN-1", "ADMIN-2")));

  @SuppressWarnings("unused")
  void handler(@CurrentAdmin String annotated, String plain, @CurrentAdmin Long wrongType) {}

  private MethodParameter param(int index) throws Exception {
    var method =
        CurrentAdminArgumentResolverTest.class.getDeclaredMethod(
            "handler", String.class, String.class, Long.class);
    return new MethodParameter(method, index);
  }

  private Object resolve(CurrentAdminArgumentResolver target, String header) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    if (header != null) {
      request.addHeader(CurrentEmployeeArgumentResolver.EMPLOYEE_ID_HEADER, header);
    }
    return target.resolveArgument(param(0), null, new ServletWebRequest(request), null);
  }

  private Object resolve(String header) throws Exception {
    return resolve(resolver, header);
  }

  @Test
  void supportsOnlyAnnotatedStringParameters() throws Exception {
    assertThat(resolver.supportsParameter(param(0))).isTrue();
    assertThat(resolver.supportsParameter(param(1))).isFalse();
    assertThat(resolver.supportsParameter(param(2))).isFalse();
  }

  @Test
  void resolvesAdminInList() throws Exception {
    assertThat(resolve("ADMIN-1")).isEqualTo("ADMIN-1");
    assertThat(resolve("ADMIN-2")).isEqualTo("ADMIN-2");
  }

  @Test
  void rejectsEmployeeNotInList() {
    assertThatThrownBy(() -> resolve("E1001")).isInstanceOf(ForbiddenException.class);
  }

  @Test
  void rejectsDifferentCaseAndSurroundingSpaces() {
    assertThatThrownBy(() -> resolve("admin-1")).isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> resolve(" ADMIN-1")).isInstanceOf(ForbiddenException.class);
  }

  @Test
  void rejectsEveryoneWhenListIsEmptyOrNull() {
    var empty = new CurrentAdminArgumentResolver(new AdminProperties(List.of()));
    var nul = new CurrentAdminArgumentResolver(new AdminProperties(null));

    assertThatThrownBy(() -> resolve(empty, "ADMIN-1")).isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> resolve(nul, "ADMIN-1")).isInstanceOf(ForbiddenException.class);
  }

  @Test
  void rejectsBlankEntryEvenIfListHadBlankElement() {
    var blankList = new CurrentAdminArgumentResolver(new AdminProperties(List.of("", " ")));

    assertThatThrownBy(() -> resolve(blankList, "")).isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> resolve(blankList, "x")).isInstanceOf(ForbiddenException.class);
  }

  @Test
  void rejectsMissingHeaderWith400() {
    assertThatThrownBy(() -> resolve(null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("X-Employee-Id: 사번이 필요합니다");
  }

  @Test
  void rejectsBlankHeaderWith400() {
    assertThatThrownBy(() -> resolve("  ")).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void rejectsTooLongHeaderWith400() {
    assertThatThrownBy(() -> resolve("A".repeat(21)))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("X-Employee-Id: 20자 이하여야 합니다");
  }
}
