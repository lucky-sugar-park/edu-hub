package com.example.eduhub.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, String> {

  private int max;

  @Override
  public void initialize(MaxUtf8Bytes annotation) {
    this.max = annotation.value();
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return value == null || withinLimit(value, max);
  }

  /** UTF-8 바이트 길이가 {@code max} 이하인지 본다. 검증 어노테이션과 같은 계산을 다른 곳에서도 쓰도록 둔다. */
  static boolean withinLimit(String value, int max) {
    return value.getBytes(StandardCharsets.UTF_8).length <= max;
  }
}
