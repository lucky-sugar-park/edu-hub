package com.example.eduhub.common;

/** 현재 상태에서 허용되지 않는 변경 요청. 409와 함께 {@link #getCode()}를 에러 코드로 돌려준다. */
public class ConflictException extends RuntimeException {

  private final String code;

  public ConflictException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
