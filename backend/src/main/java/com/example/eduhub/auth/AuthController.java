package com.example.eduhub.auth;

import com.example.eduhub.common.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "사원 비밀번호 로그인")
@RestController
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @Operation(
      summary = "사번과 비밀번호로 로그인",
      description = "성공하면 Bearer 토큰을 발급한다. 사번이 없거나 비밀번호가 틀리면 같은 401로 응답한다")
  @ApiResponse(responseCode = "200", description = "토큰 발급")
  @ApiResponse(
      responseCode = "400",
      description = "사번 또는 비밀번호가 비어 있거나 너무 김",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "401",
      description = "사번 또는 비밀번호가 올바르지 않음",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping("/api/auth/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request.employeeNo(), request.password());
  }
}
