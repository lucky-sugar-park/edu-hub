package com.example.eduhub.common;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** {@link CurrentAdmin} 파라미터에 관리자 사번을 채운다. 목록에 정확히 일치하지 않으면 403이다(D-29). */
public class CurrentAdminArgumentResolver implements HandlerMethodArgumentResolver {

  private final AdminProperties adminProperties;

  public CurrentAdminArgumentResolver(AdminProperties adminProperties) {
    this.adminProperties = adminProperties;
  }

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(CurrentAdmin.class)
        && String.class.equals(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    String employeeId = CurrentEmployeeArgumentResolver.readEmployeeId(webRequest);
    if (!adminProperties.isAdmin(employeeId)) {
      throw new ForbiddenException("관리자만 사용할 수 있습니다");
    }
    return employeeId;
  }
}
