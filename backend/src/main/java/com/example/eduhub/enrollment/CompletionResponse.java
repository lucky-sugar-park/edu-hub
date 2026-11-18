package com.example.eduhub.enrollment;

import java.util.List;

/** 수료 처리 결과. 두 목록 모두 요청 순서를 따른다(중복 사번은 첫 번째만). */
public record CompletionResponse(List<String> completed, List<Failure> failed) {

  public record Failure(String employeeId, CompletionFailureReason reason) {}
}
