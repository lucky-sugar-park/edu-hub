package com.example.eduhub.auth;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 문자열의 UTF-8 바이트 길이가 {@link #value()} 이하여야 한다. BCrypt 입력 한계(72바이트) 검증에 쓴다. null은 통과한다. */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

  int value();

  String message() default "{value}바이트 이하여야 합니다";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
