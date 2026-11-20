package com.example.eduhub.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개발 환경 전용 로그인(D-30). 비밀번호 없이 사번만으로 토큰을 발급한다.
 *
 * <p>{@code eduhub.auth.dev-login-enabled=true}일 때만 빈이 만들어진다(dev·test 프로필). 그 밖의 환경에서는 이 경로가 404다.
 */
@Tag(name = "DevAuth", description = "개발 환경 전용 로그인")
@RestController
@ConditionalOnProperty(name = "eduhub.auth.dev-login-enabled", havingValue = "true")
public class DevAuthController {

  private final TokenIssuer tokenIssuer;

  public DevAuthController(TokenIssuer tokenIssuer) {
    this.tokenIssuer = tokenIssuer;
  }

  @Operation(
      summary = "개발용 로그인(사번으로 토큰 발급)",
      description =
          "개발 환경 전용, 비밀번호 없음. 관리자 사번 목록(EDUHUB_ADMIN_EMPLOYEE_IDS)에 있으면 roles에 ADMIN이 포함된다."
              + " 이 기능이 꺼진 환경에서는 404다")
  @ApiResponse(responseCode = "200", description = "토큰 발급")
  @ApiResponse(
      responseCode = "400",
      description = "사번이 비어 있거나 20자 초과",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping("/api/v1/dev/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return tokenIssuer.issue(request.employeeId());
  }
}
