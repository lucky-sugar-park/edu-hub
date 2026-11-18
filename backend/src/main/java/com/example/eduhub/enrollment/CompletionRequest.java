package com.example.eduhub.enrollment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 수료 처리 요청. 요청 안의 중복 사번은 첫 번째만 처리한다.
 *
 * <p>빈 목록 위반이 한 번만 나오도록 {@code @NotEmpty} 대신 {@code @NotNull @Size(min = 1)}을 쓴다. springdoc도
 * {@code @Size}의 min을 그대로 OpenAPI의 minItems·minLength로 옮긴다.
 */
public record CompletionRequest(
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "수료 처리할 사번 목록(1~200개). 각 값은 공백 불가, 최대 20자")
        @NotNull(message = "필수 값입니다")
        @Size(min = 1, max = 200, message = "1~200개여야 합니다")
        List<
                @NotBlank(message = "공백일 수 없습니다") @Size(min = 1, max = 20, message = "1~20자여야 합니다")
                String>
            employeeIds) {}
