package ar.edu.utn.frvm.typeit.bemol_api.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    String secret,
    Duration accessTokenExpiration,
    Duration refreshTokenExpiration,
    Duration rememberMeTokenExpiration) {

  public Duration refreshExpiration(boolean rememberMe) {
    return rememberMe ? rememberMeTokenExpiration() : refreshTokenExpiration();
  }
}
