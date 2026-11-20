package com.example.eduhub.common;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** dev 로그인 요청. 비밀번호는 받지 않는다(개발 환경 전용). */
public record LoginRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 20, example = "E1001")
        @NotBlank(message = "필수 값입니다")
        @Size(max = 20, message = "20자 이하여야 합니다")
        String employeeId) {}
