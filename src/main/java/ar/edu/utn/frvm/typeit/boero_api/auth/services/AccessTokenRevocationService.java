package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.AccessTokenParseResult;
import io.jsonwebtoken.Claims;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccessTokenRevocationService {
  private final Clock clock;

  private final TokenBlacklistService tokenBlacklistService;
  private final JwtService jwtService;

  public void revoke(final String accessToken) {
    final AccessTokenParseResult parseResult = jwtService.parseAccessToken(accessToken);
    if (!(parseResult instanceof AccessTokenParseResult.Ok ok)) {
      return;
    }

    final Claims claims = ok.claims();
    final String tokenId = jwtService.extractTokenId(claims);
    final Instant expiresAt = claims.getExpiration().toInstant();
    tokenBlacklistService.blacklist(
        tokenId, TokenBlacklistTtl.remaining(clock.instant(), expiresAt));
  }
}
