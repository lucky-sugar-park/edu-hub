package com.example.eduhub.common;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 관리자 사번 목록(D-29). {@code eduhub.admin.employee-ids}로 지정한다.
 *
 * <p>null, 빈 문자열, 공백뿐인 요소는 걸러낸다. 설정이 비어 있으면(예: {@code ${...:}}가 [""]로 바인딩) 관리자가 없는 것으로 본다.
 */
@ConfigurationProperties("eduhub.admin")
public record AdminProperties(List<String> employeeIds) {

  public AdminProperties {
    employeeIds =
        employeeIds == null
            ? List.of()
            : employeeIds.stream().filter(id -> id != null && !id.isBlank()).toList();
  }

  public boolean isAdmin(String employeeId) {
    return employeeIds.contains(employeeId);
  }
}
