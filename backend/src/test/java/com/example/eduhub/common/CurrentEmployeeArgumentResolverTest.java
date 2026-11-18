package com.example.eduhub.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class CurrentEmployeeArgumentResolverTest {

  private final CurrentEmployeeArgumentResolver resolver = new CurrentEmployeeArgumentResolver();

  @SuppressWarnings("unused")
  void handler(@CurrentEmployee String annotated, String plain, @CurrentEmployee Long wrongType) {}

  private MethodParameter param(int index) throws Exception {
    var method =
        CurrentEmployeeArgumentResolverTest.class.getDeclaredMethod(
            "handler", String.class, String.class, Long.class);
    return new MethodParameter(method, index);
  }

  private Object resolve(String header) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    if (header != null) {
      request.addHeader(CurrentEmployeeArgumentResolver.EMPLOYEE_ID_HEADER, header);
    }
    return resolver.resolveArgument(param(0), null, new ServletWebRequest(request), null);
  }

  @Test
  void resolvesHeaderValueAsIs() throws Exception {
    assertThat(resolve("E1001")).isEqualTo("E1001");
    assertThat(resolve(" E1 ")).isEqualTo(" E1 ");
  }

  @Test
  void rejectsMissingHeader() {
    assertThatThrownBy(() -> resolve(null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("X-Employee-Id: 사번이 필요합니다");
  }

  @Test
  void rejectsBlankHeader() {
    assertThatThrownBy(() -> resolve("   "))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("X-Employee-Id: 사번이 필요합니다");
  }

  @Test
  void rejectsControlCharacterOnlyHeader() {
    assertThatThrownBy(() -> resolve("\u0001"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("X-Employee-Id: 사번이 필요합니다");
  }

  @Test
  void acceptsExactlyTwentyCharacters() throws Exception {
    String value = "A".repeat(20);
    assertThat(resolve(value)).isEqualTo(value);
  }

  @Test
  void rejectsTwentyOneCharacters() {
    assertThatThrownBy(() -> resolve("A".repeat(21)))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("X-Employee-Id: 20자 이하여야 합니다");
  }

  @Test
  void supportsOnlyAnnotatedStringParameters() throws Exception {
    assertThat(resolver.supportsParameter(param(0))).isTrue();
    assertThat(resolver.supportsParameter(param(1))).isFalse();
    assertThat(resolver.supportsParameter(param(2))).isFalse();
  }
}
