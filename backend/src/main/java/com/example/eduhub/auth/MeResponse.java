package com.example.eduhub.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/** 로그인한 사원 정보. */
public record MeResponse(
    @Schema(description = "사원 ID", requiredMode = Schema.RequiredMode.REQUIRED) long id,
    @Schema(example = "E1001", requiredMode = Schema.RequiredMode.REQUIRED) String employeeNo,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) EmployeeRole role) {

  public static MeResponse from(Employee employee) {
    return new MeResponse(employee.getId(), employee.getEmployeeNo(), employee.getRole());
  }
}
