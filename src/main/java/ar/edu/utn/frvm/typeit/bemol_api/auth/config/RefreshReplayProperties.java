package ar.edu.utn.frvm.typeit.bemol_api.auth.config;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.REFRESH_REPLAY_ENCRYPTION_KEY_INVALID_BASE64;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.REFRESH_REPLAY_ENCRYPTION_KEY_INVALID_SIZE;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.REFRESH_REPLAY_ENCRYPTION_KEY_REQUIRED;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.REFRESH_REPLAY_TTL_INVALID;

import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.refresh-replay")
public record RefreshReplayProperties(String encryptionKey, Duration ttl) {

  private static final int KEY_SIZE_BYTES = 32;

  public RefreshReplayProperties {
    if (encryptionKey == null || encryptionKey.isBlank()) {
      throw new IllegalArgumentException(REFRESH_REPLAY_ENCRYPTION_KEY_REQUIRED);
    }
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException(REFRESH_REPLAY_TTL_INVALID);
    }
    decodeKey(encryptionKey);
  }

  public SecretKey secretKey() {
    return new SecretKeySpec(decodeKey(encryptionKey), "AES");
  }

  private static byte[] decodeKey(final String encodedKey) {
    final byte[] decodedKey;
    try {
      decodedKey = Base64.getDecoder().decode(encodedKey);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException(REFRESH_REPLAY_ENCRYPTION_KEY_INVALID_BASE64, exception);
    }

    if (decodedKey.length != KEY_SIZE_BYTES) {
      throw new IllegalArgumentException(REFRESH_REPLAY_ENCRYPTION_KEY_INVALID_SIZE);
    }

    return decodedKey;
  }
}
