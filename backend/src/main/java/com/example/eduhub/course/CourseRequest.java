package com.example.eduhub.course;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 과정 등록·수정 요청. */
public record CourseRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200)
        @NotBlank(message = "필수 값입니다")
        @Size(max = 200, message = "200자 이하여야 합니다")
        String title,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100)
        @NotBlank(message = "필수 값입니다")
        @Size(max = 100, message = "100자 이하여야 합니다")
        String category,
    @Schema(maxLength = 2000) @Size(max = 2000, message = "2000자 이하여야 합니다") String description) {}
