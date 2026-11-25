package com.example.eduhub.auth;

import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** 사원 계정으로 HS256 토큰을 발급한다(D-33). {@code sub}는 사원 ID, 역할은 {@code role} 클레임 하나다. */
@Component
public class TokenIssuer {

  static final String ISSUER = "eduhub";

  private final JwtEncoder jwtEncoder;
  private final AuthProperties authProperties;
  private final Clock clock;

  public TokenIssuer(JwtEncoder jwtEncoder, AuthProperties authProperties, Clock clock) {
    this.jwtEncoder = jwtEncoder;
    this.authProperties = authProperties;
    this.clock = clock;
  }

  public IssuedToken issue(Employee employee) {
    Instant issuedAt = clock.instant();
    long expiresIn = authProperties.tokenTtl().toSeconds();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(String.valueOf(employee.getId()))
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(expiresIn))
            .claim("employee_no", employee.getEmployeeNo())
            .claim("role", employee.getRole().name())
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new IssuedToken(token, expiresIn);
  }

  /** 발급 결과. 토큰이 로그에 찍히지 않도록 toString을 직접 만든다. */
  public record IssuedToken(String token, long expiresIn) {

    @Override
    public String toString() {
      return "IssuedToken[expiresIn=" + expiresIn + "]";
    }
  }
}
