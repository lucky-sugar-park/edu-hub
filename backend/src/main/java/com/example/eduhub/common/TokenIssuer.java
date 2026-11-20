package com.example.eduhub.common;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * 사번으로 HS256 토큰을 발급한다(D-30). 관리자 여부는 {@link AdminProperties}(D-29) 목록으로 정해 {@code roles}에 넣는다.
 *
 * <p>사내 SSO가 붙으면 발급자만 바뀌고 검증 구조는 유지된다.
 */
@Component
public class TokenIssuer {

  static final String ISSUER = "eduhub-dev";

  private final JwtEncoder jwtEncoder;
  private final AdminProperties adminProperties;
  private final AuthProperties authProperties;
  private final Clock clock;

  public TokenIssuer(
      JwtEncoder jwtEncoder,
      AdminProperties adminProperties,
      AuthProperties authProperties,
      Clock clock) {
    this.jwtEncoder = jwtEncoder;
    this.adminProperties = adminProperties;
    this.authProperties = authProperties;
    this.clock = clock;
  }

  public LoginResponse issue(String employeeId) {
    List<String> roles =
        adminProperties.isAdmin(employeeId) ? List.of("EMPLOYEE", "ADMIN") : List.of("EMPLOYEE");
    Instant issuedAt = clock.instant();
    Instant expiresAt = issuedAt.plus(authProperties.tokenTtl());
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(employeeId)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim("roles", roles)
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new LoginResponse(token, "Bearer", expiresAt, employeeId, roles);
  }
}
