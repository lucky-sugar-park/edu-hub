package com.example.eduhub.auth;

/** 사번이 없거나 비밀번호가 틀렸다. 둘을 구별하지 않는다(D-33). */
public class InvalidCredentialsException extends RuntimeException {

  public static final String MESSAGE = "사번 또는 비밀번호가 올바르지 않습니다";

  public InvalidCredentialsException() {
    super(MESSAGE);
  }
}
