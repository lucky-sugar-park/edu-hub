package com.example.eduhub.common;

/** 권한이 없는 요청. 403 FORBIDDEN으로 응답한다. */
public class ForbiddenException extends RuntimeException {

  public ForbiddenException(String message) {
    super(message);
  }
}
