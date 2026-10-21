package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.WebAuthnProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WebAuthnCeremonyService {

  private static final String REGISTRATION_PREFIX = "boero:webauthn:registration:";
  private static final String AUTHENTICATION_PREFIX = "boero:webauthn:authentication:";

  private final StringRedisTemplate redisTemplate;
  private final WebAuthnProperties properties;

  public String storeRegistration(
      final UUID userId, final UUID sessionId, final String label, final String optionsJson) {
    final String ceremonyId = UUID.randomUUID().toString();
    final String value =
        userId
            + "|"
            + sessionId
            + "|"
            + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(label.getBytes(StandardCharsets.UTF_8))
            + "|"
            + Instant.now().toEpochMilli()
            + "\n"
            + optionsJson;
    redisTemplate.opsForValue().set(REGISTRATION_PREFIX + ceremonyId, value, challengeTtl());
    return ceremonyId;
  }

  public Optional<RegistrationCeremony> consumeRegistration(final String ceremonyId) {
    if (ceremonyId == null || ceremonyId.isBlank()) {
      return Optional.empty();
    }
    final String value = redisTemplate.opsForValue().getAndDelete(REGISTRATION_PREFIX + ceremonyId);
    if (value == null) {
      return Optional.empty();
    }
    return parseRegistration(ceremonyId, value);
  }

  public String storeAuthentication(
      final String loginAttemptId, final UUID userId, final String optionsJson) {
    final String ceremonyId = UUID.randomUUID().toString();
    final String value =
        loginAttemptId + "|" + userId + "|" + Instant.now().toEpochMilli() + "\n" + optionsJson;
    redisTemplate.opsForValue().set(AUTHENTICATION_PREFIX + ceremonyId, value, challengeTtl());
    return ceremonyId;
  }

  public Optional<AuthenticationCeremony> consumeAuthentication(final String ceremonyId) {
    if (ceremonyId == null || ceremonyId.isBlank()) {
      return Optional.empty();
    }
    final String value =
        redisTemplate.opsForValue().getAndDelete(AUTHENTICATION_PREFIX + ceremonyId);
    if (value == null) {
      return Optional.empty();
    }
    return parseAuthentication(ceremonyId, value);
  }

  private Duration challengeTtl() {
    return properties.challengeTtl();
  }

  private static Optional<RegistrationCeremony> parseRegistration(
      final String ceremonyId, final String value) {
    final int separator = value.indexOf('\n');
    if (separator < 0) {
      return Optional.empty();
    }
    final String[] parts = value.substring(0, separator).split("\\|", -1);
    if (parts.length != 4) {
      return Optional.empty();
    }
    try {
      final String label =
          new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8);
      return Optional.of(
          new RegistrationCeremony(
              ceremonyId,
              UUID.fromString(parts[0]),
              UUID.fromString(parts[1]),
              label,
              value.substring(separator + 1),
              Instant.ofEpochMilli(Long.parseLong(parts[3]))));
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }

  private static Optional<AuthenticationCeremony> parseAuthentication(
      final String ceremonyId, final String value) {
    final int separator = value.indexOf('\n');
    if (separator < 0) {
      return Optional.empty();
    }
    final String[] parts = value.substring(0, separator).split("\\|", -1);
    if (parts.length != 3) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          new AuthenticationCeremony(
              ceremonyId,
              parts[0],
              UUID.fromString(parts[1]),
              value.substring(separator + 1),
              Instant.ofEpochMilli(Long.parseLong(parts[2]))));
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }
}
