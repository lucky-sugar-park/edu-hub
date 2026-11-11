package com.example.eduhub.course;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * 회차 등록·수정 요청.
 *
 * <p>시작 시각이 현재 이후여야 하는지는 시작 여부(D-08)에 따라 달라지므로 서비스에서 검사한다.
 */
public record SessionRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "교육 시작 시각(ISO-8601)")
        @NotNull(message = "필수 값입니다")
        Instant startAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200)
        @NotBlank(message = "필수 값입니다")
        @Size(max = 200, message = "200자 이하여야 합니다")
        String place,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", maximum = "1000")
        @NotNull(message = "필수 값입니다")
        @Min(value = 1, message = "1 이상이어야 합니다")
        @Max(value = 1000, message = "1000 이하여야 합니다")
        Integer capacity) {}
