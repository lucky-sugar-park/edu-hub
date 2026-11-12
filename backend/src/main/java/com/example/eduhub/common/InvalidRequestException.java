package com.example.eduhub.common;

/** Bean Validation으로 표현하기 어려운 입력 오류. 400 BAD_REQUEST로 응답한다. */
public class InvalidRequestException extends RuntimeException {

  public InvalidRequestException(String message) {
    super(message);
  }
}
