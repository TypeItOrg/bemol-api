package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.SHA_256_UNAVAILABLE;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.JwtProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.AccessTokenParseResult;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.AccountType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private static final String CLAIM_ACCOUNT_TYPE = "accountType";
  private static final String CLAIM_DOCUMENT_NUMBER = "documentNumber";
  private static final String CLAIM_EMAIL = "email";
  private static final String CLAIM_INSTITUTION_ID = "institutionId";
  private static final String CLAIM_PERSON_ID = "personId";
  private static final String CLAIM_SESSION_ID = "sessionId";

  private final JwtProperties jwtProperties;
  private final SecretKey secretKey;
  private final Clock clock;

  public JwtService(final JwtProperties jwtProperties, final Clock clock) {
    this.clock = clock;
    this.jwtProperties = jwtProperties;
    this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
  }

  public String generateAccessToken(InstitutionalAccessTokenInput input) {
    UUID tokenId = UUID.randomUUID();
    return signedBuilder(clock.instant(), tokenId, input.userId().toString())
        .claim(CLAIM_ACCOUNT_TYPE, AccountType.INSTITUTION.name())
        .claim(CLAIM_DOCUMENT_NUMBER, input.documentNumber())
        .claim(CLAIM_INSTITUTION_ID, input.institutionId().toString())
        .claim(CLAIM_PERSON_ID, input.personId().toString())
        .claim(CLAIM_SESSION_ID, input.sessionId().toString())
        .compact();
  }

  public String generatePlatformAccessToken(PlatformAccessTokenInput input) {
    UUID tokenId = UUID.randomUUID();
    return signedBuilder(clock.instant(), tokenId, input.platformAccountId().toString())
        .claim(CLAIM_ACCOUNT_TYPE, AccountType.PLATFORM.name())
        .claim(CLAIM_EMAIL, input.email())
        .claim(CLAIM_SESSION_ID, input.sessionId().toString())
        .compact();
  }

  public AccessTokenParseResult parseAccessToken(String token) {
    try {
      Claims claims =
          Jwts.parser()
              .clock(() -> Date.from(clock.instant()))
              .verifyWith(secretKey)
              .build()
              .parseSignedClaims(token)
              .getPayload();
      return new AccessTokenParseResult.Ok(claims);
    } catch (ExpiredJwtException e) {
      return new AccessTokenParseResult.Expired();
    } catch (JwtException | IllegalArgumentException e) {
      return new AccessTokenParseResult.Invalid();
    }
  }

  public static String hashToken(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(SHA_256_UNAVAILABLE, e);
    }
  }

  @NonNull
  public String extractTokenId(Claims claims) {
    return claims.getId();
  }

  @NonNull
  public UUID extractUserId(Claims claims) {
    String subject = claims.getSubject();
    return UUID.fromString(subject);
  }

  @NonNull
  public AccountType extractAccountType(Claims claims) {
    return AccountType.valueOf(claims.get(CLAIM_ACCOUNT_TYPE, String.class));
  }

  @NonNull
  public UUID extractPlatformAccountId(Claims claims) {
    return extractUserId(claims);
  }

  @NonNull
  public String extractEmail(Claims claims) {
    return claims.get(CLAIM_EMAIL, String.class);
  }

  @NonNull
  public String extractDocumentNumber(Claims claims) {
    return claims.get(CLAIM_DOCUMENT_NUMBER, String.class);
  }

  @NonNull
  public UUID extractInstitutionId(Claims claims) {
    return UUID.fromString(claims.get(CLAIM_INSTITUTION_ID, String.class));
  }

  @NonNull
  public UUID extractPersonId(Claims claims) {
    return UUID.fromString(claims.get(CLAIM_PERSON_ID, String.class));
  }

  @NonNull
  public UUID extractSessionId(Claims claims) {
    return UUID.fromString(claims.get(CLAIM_SESSION_ID, String.class));
  }

  private JwtBuilder signedBuilder(Instant now, UUID tokenId, String subject) {
    Instant expiration = now.plus(jwtProperties.accessTokenExpiration());
    return Jwts.builder()
        .id(tokenId.toString())
        .subject(subject)
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiration))
        .signWith(secretKey);
  }
}
