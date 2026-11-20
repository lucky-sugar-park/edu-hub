package com.example.eduhub.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "서버 상태 확인")
@RestController
public class HealthController {

  /**
   * 서버가 요청을 받아 처리할 수 있는지 확인한다(헬스 체크).
   *
   * <p>배포 후 기동 확인, 로드밸런서·모니터링의 생존 확인, 프런트 개발 서버의 프록시 연결 확인에 쓴다. 요청이 이 메서드까지 도달했다는 것 자체가 "웹 계층이 살아
   * 있다"는 뜻이므로 항상 {@code status=UP}을 돌려준다.
   *
   * <ul>
   *   <li>인증 없이 호출할 수 있다. {@code SecurityConfig}가 {@code GET /api/health}를 공개 경로로 둔다(D-30).
   *   <li>DB 연결이나 외부 시스템 상태는 확인하지 않는다. 이 엔드포인트가 UP이어도 DB가 내려가 있을 수 있다. 의존 시스템까지 보려면 Spring Boot
   *       Actuator의 health 같은 별도 검사를 붙여야 한다.
   *   <li>상태를 바꾸지 않고 부수 효과가 없으므로 자주 호출해도 된다.
   * </ul>
   *
   * @return 항상 {@code status}가 {@code "UP"}인 응답
   */
  @Operation(summary = "서버 상태를 확인한다", description = "서버가 요청을 받을 수 있으면 status=UP을 돌려준다")
  @ApiResponse(responseCode = "200", description = "서버 동작 중(status=UP)")
  @GetMapping("/api/health")
  public HealthResponse health() {
    return new HealthResponse("UP");
  }
}
