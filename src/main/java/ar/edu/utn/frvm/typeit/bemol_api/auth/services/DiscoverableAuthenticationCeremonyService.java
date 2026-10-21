package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.WebAuthnProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DiscoverableAuthenticationCeremonyService {
  // Separate format and namespace preserve in-flight identified login ceremonies.
  private static final String PREFIX = "boero:webauthn:discoverable-authentication:";
  private final StringRedisTemplate redisTemplate;
  private final WebAuthnProperties properties;

  public String store(
      final UUID institutionId,
      final String hostOrigin,
      final Set<String> trustedOrigins,
      final String optionsJson) {
    final String id = UUID.randomUUID().toString();
    final String origins = String.join("\n", trustedOrigins.stream().sorted().toList());
    final String value =
        institutionId
            + "|"
            + encode(hostOrigin)
            + "|"
            + encode(origins)
            + "|"
            + Instant.now().toEpochMilli()
            + "\n"
            + optionsJson;
    redisTemplate.opsForValue().set(PREFIX + id, value, properties.challengeTtl());
    return id;
  }

  public Optional<DiscoverableAuthenticationCeremony> consume(final String id) {
    final String value = redisTemplate.opsForValue().getAndDelete(PREFIX + id);
    if (value == null) {
      return Optional.empty();
    }
    final int separator = value.indexOf('\n');
    if (separator < 0) {
      return Optional.empty();
    }
    final String[] parts = value.substring(0, separator).split("\\|", -1);
    if (parts.length != 4) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          new DiscoverableAuthenticationCeremony(
              UUID.fromString(parts[0]),
              decode(parts[1]),
              Set.of(decode(parts[2]).split("\n")),
              value.substring(separator + 1),
              Instant.ofEpochMilli(Long.parseLong(parts[3]))));
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }

  private static String encode(final String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String decode(final String value) {
    return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
  }
}
