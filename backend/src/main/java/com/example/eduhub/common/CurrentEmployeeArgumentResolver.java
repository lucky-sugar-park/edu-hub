package com.example.eduhub.common;

import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link CurrentEmployee} 파라미터에 로그인한 사원의 사번(토큰의 {@code sub})을 채운다(D-30).
 *
 * <p>보안 필터 체인이 인증 없는 요청을 먼저 막으므로, 인증이 없을 때의 예외는 방어용이다. 예외는 보안 체인의 401 처리({@code
 * ExceptionTranslationFilter})가 받는다.
 */
public class CurrentEmployeeArgumentResolver implements HandlerMethodArgumentResolver {

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
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken token) || !token.isAuthenticated()) {
      throw new AuthenticationCredentialsNotFoundException("인증 정보가 없습니다");
    }
    String employeeId = token.getName();
    if (employeeId == null || employeeId.isBlank()) {
      throw new AuthenticationCredentialsNotFoundException("토큰에 사번이 없습니다");
    }
    return employeeId;
  }
}
