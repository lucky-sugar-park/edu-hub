package com.example.eduhub.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** dev 로그인 응답. */
public record LoginResponse(
    @Schema(description = "Bearer 토큰(HS256 JWT)", requiredMode = Schema.RequiredMode.REQUIRED)
        String accessToken,
    @Schema(example = "Bearer", requiredMode = Schema.RequiredMode.REQUIRED) String tokenType,
    @Schema(description = "토큰 만료 시각", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant expiresAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String employeeId,
    @Schema(
            description = "EMPLOYEE 또는 EMPLOYEE, ADMIN",
            requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> roles) {}
