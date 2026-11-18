package com.example.eduhub.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "서버 상태 확인")
@RestController
public class HealthController {

  @Operation(summary = "서버 상태를 확인한다", description = "서버가 요청을 받을 수 있으면 status=UP을 돌려준다")
  @ApiResponse(responseCode = "200", description = "서버 동작 중(status=UP)")
  @GetMapping("/api/health")
  public HealthResponse health() {
    return new HealthResponse("UP");
  }
}
