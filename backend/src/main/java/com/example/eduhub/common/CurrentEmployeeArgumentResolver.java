package com.example.eduhub.common;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** {@link CurrentEmployee} 파라미터에 사번을 채운다. */
public class CurrentEmployeeArgumentResolver implements HandlerMethodArgumentResolver {

  // TODO(NFR-03): 인증 도입 후 인증 정보에서 사번을 꺼낸다
  public static final String EMPLOYEE_ID_HEADER = "X-Employee-Id";

  private static final int MAX_LENGTH = 20;

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(CurrentEmployee.class)
        && String.class.equals(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    String value = webRequest.getHeader(EMPLOYEE_ID_HEADER);
    if (value == null || value.trim().isEmpty()) {
      throw new InvalidRequestException(EMPLOYEE_ID_HEADER + ": 사번이 필요합니다");
    }
    if (value.length() > MAX_LENGTH) {
      throw new InvalidRequestException(EMPLOYEE_ID_HEADER + ": 20자 이하여야 합니다");
    }
    return value;
  }
}
