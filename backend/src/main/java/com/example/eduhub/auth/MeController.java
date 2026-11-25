package com.example.eduhub.auth;

import com.example.eduhub.common.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Me", description = "현재 사용자")
@RestController
@SecurityRequirement(name = "bearerAuth")
public class MeController {

  @Operation(
      summary = "현재 로그인한 사용자",
      description = "토큰의 클레임만으로 응답한다(DB 조회 없음). 계정이 지워져도 토큰 만료까지는 응답한다")
  @ApiResponse(responseCode = "200", description = "현재 사용자")
  @ApiResponse(
      responseCode = "401",
      description = "토큰이 없거나 만료·위조됨",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @GetMapping("/api/me")
  public MeResponse me(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    return new MeResponse(user.id(), user.employeeNo(), user.role());
  }
}
