package com.example.eduhub.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/** 비밀번호 로그인 응답. */
public record LoginResponse(
    @Schema(description = "Bearer 토큰(HS256 JWT)", requiredMode = Schema.RequiredMode.REQUIRED)
        String accessToken,
    @Schema(
            description = "토큰 유효 시간(초)",
            example = "3600",
            requiredMode = Schema.RequiredMode.REQUIRED)
        long expiresIn,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) MeResponse user) {

  /** 토큰이 로그에 찍히지 않도록 직접 만든다. */
  @Override
  public String toString() {
    return "LoginResponse[expiresIn=" + expiresIn + ", user=" + user + "]";
  }
}
