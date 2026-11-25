package com.example.eduhub.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 비밀번호 로그인 요청(D-33). */
public record LoginRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 20, example = "E1001")
        @NotBlank(message = "필수 값입니다")
        @Size(max = 20, message = "20자 이하여야 합니다")
        String employeeNo,
    @Schema(
            description = "UTF-8 기준 72바이트 이하",
            requiredMode = Schema.RequiredMode.REQUIRED,
            format = "password")
        @NotBlank(message = "필수 값입니다")
        @MaxUtf8Bytes(value = 72, message = "72바이트 이하여야 합니다")
        String password) {

  /** 비밀번호가 로그에 찍히지 않도록 직접 만든다. */
  @Override
  public String toString() {
    return "LoginRequest[employeeNo=" + employeeNo + "]";
  }
}
