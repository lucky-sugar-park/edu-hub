package com.example.eduhub.common;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** OpenAPI 보안 스킴. 인증이 필요한 컨트롤러가 {@code @SecurityRequirement(name = "bearerAuth")}로 가리킨다(D-30). */
@Configuration
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
public class OpenApiConfig {}
