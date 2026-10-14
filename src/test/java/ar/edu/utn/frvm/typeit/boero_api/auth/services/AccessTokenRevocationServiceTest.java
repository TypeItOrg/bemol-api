package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.AccessTokenParseResult;
import io.jsonwebtoken.Claims;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccessTokenRevocationServiceTest {
  private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

  @Mock private TokenBlacklistService tokenBlacklistService;
  @Mock private JwtService jwtService;
  @Mock private Claims claims;

  private AccessTokenRevocationService service;

  @BeforeEach
  void setUp() {
    service =
        new AccessTokenRevocationService(
            Clock.fixed(NOW, ZoneOffset.UTC), tokenBlacklistService, jwtService);
  }

  @Test
  void validTokenIsBlacklistedForItsRemainingLifetime() {
    final Instant expiresAt = NOW.plusSeconds(300);
    when(jwtService.parseAccessToken("access")).thenReturn(new AccessTokenParseResult.Ok(claims));
    when(jwtService.extractTokenId(claims)).thenReturn("jti");
    when(claims.getExpiration()).thenReturn(Date.from(expiresAt));

    service.revoke("access");

    verify(tokenBlacklistService).blacklist("jti", Duration.ofSeconds(300));
  }

  @Test
  void expiredOrInvalidTokenIsNotBlacklisted() {
    when(jwtService.parseAccessToken("expired")).thenReturn(new AccessTokenParseResult.Expired());
    when(jwtService.parseAccessToken("invalid")).thenReturn(new AccessTokenParseResult.Invalid());

    service.revoke("expired");
    service.revoke("invalid");

    verify(tokenBlacklistService, never()).blacklist(any(), any());
  }
}
