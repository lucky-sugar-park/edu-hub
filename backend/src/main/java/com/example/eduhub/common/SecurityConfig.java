package com.example.eduhub.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 보안 필터 체인(D-30). 무상태 Bearer JWT 리소스 서버이며, 401·403도 {@link ErrorResponse} JSON으로 응답한다.
 *
 * <p>규칙은 위에서부터 적용되므로 좁은 규칙을 앞에 둔다.
 */
@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper)
      throws Exception {
    AuthenticationEntryPoint entryPoint = authenticationEntryPoint(objectMapper);
    AccessDeniedHandler deniedHandler = accessDeniedHandler(objectMapper);

    http.csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth ->
            auth.requestMatchers(HttpMethod.POST, "/api/v1/dev/login")
                .permitAll()
                // 주의: 이 경로 아래에 개인정보를 반환하는 GET을 만들면 공개된다. 그때는 규칙을 좁힐 것.
                .requestMatchers(
                    HttpMethod.GET, "/api/health", "/api/v1/courses", "/api/v1/courses/**")
                .permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                .permitAll()
                .requestMatchers("/api/v1/admin/**")
                .hasRole("ADMIN")
                .requestMatchers("/api/**")
                .authenticated()
                .anyRequest()
                .permitAll())
        .exceptionHandling(
            e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
        .oauth2ResourceServer(o ->
            o.jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(deniedHandler));
    return http.build();
  }

  /** {@code roles} 클레임을 {@code ROLE_*} 권한으로 바꾸고, 주체 이름은 {@code sub}(사번)로 한다. */
  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName("roles");
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    converter.setPrincipalClaimName("sub");
    return converter;
  }

  private static AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
    // 예외 메시지에는 토큰 내용이 들어갈 수 있어 응답에 쓰지 않는다.
    return (request, response, exception) -> {
      // 상세 정보 없이 스킴만 알린다.
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
      write(
          objectMapper,
          response,
          HttpStatus.UNAUTHORIZED,
          new ErrorResponse("UNAUTHORIZED", "로그인이 필요합니다"));
    };
  }

  private static AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
    return (request, response, exception) ->
        write(
            objectMapper,
            response,
            HttpStatus.FORBIDDEN,
            new ErrorResponse("FORBIDDEN", "권한이 없습니다"));
  }

  private static void write(
      ObjectMapper objectMapper,
      jakarta.servlet.http.HttpServletResponse response,
      HttpStatus status,
      ErrorResponse body)
      throws java.io.IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(response.getWriter(), body);
  }
}
