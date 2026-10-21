package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.REFRESH_REPLAY_CACHE_INVALID;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.REFRESH_REPLAY_ENCRYPTION_FAILED;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.REFRESH_REPLAY_VALUE_INVALID_FORMAT;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.REFRESH_REPLAY_VALUE_TOO_SHORT;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.RefreshReplayProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshReplayCache {

  private static final String KEY_PREFIX = "auth:refresh-replay:";
  private static final int GCM_TAG_LENGTH_BITS = 128;
  private static final int NONCE_LENGTH_BYTES = 12;

  private final StringRedisTemplate redisTemplate;
  private final RefreshReplayProperties properties;
  private final SecureRandom secureRandom = new SecureRandom();

  public Optional<RefreshReplay> get(final AuthRealm realm, final String tokenHash) {
    return get(realm.replayScope(), tokenHash);
  }

  public void put(final AuthRealm realm, final String tokenHash, final RefreshReplay replay) {
    put(realm.replayScope(), tokenHash, replay);
  }

  private Optional<RefreshReplay> get(final String scope, final String tokenHash) {
    final String cacheKey = cacheKey(scope, tokenHash);
    final String encryptedValue = redisTemplate.opsForValue().get(cacheKey);

    if (encryptedValue == null) {
      return Optional.empty();
    }

    try {
      return Optional.of(decrypt(cacheKey, encryptedValue));
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      throw new IllegalStateException(REFRESH_REPLAY_CACHE_INVALID, exception);
    }
  }

  private void put(final String scope, final String tokenHash, final RefreshReplay replay) {
    final String cacheKey = cacheKey(scope, tokenHash);
    redisTemplate.opsForValue().set(cacheKey, encrypt(cacheKey, replay), properties.ttl());
  }

  private RefreshReplay decrypt(final String cacheKey, final String encryptedValue)
      throws GeneralSecurityException {
    final byte[] encrypted = Base64.getDecoder().decode(encryptedValue);
    if (encrypted.length <= NONCE_LENGTH_BYTES) {
      throw new IllegalArgumentException(REFRESH_REPLAY_VALUE_TOO_SHORT);
    }

    final ByteBuffer buffer = ByteBuffer.wrap(encrypted);
    final byte[] nonce = new byte[NONCE_LENGTH_BYTES];
    buffer.get(nonce);
    final byte[] ciphertext = new byte[buffer.remaining()];
    buffer.get(ciphertext);

    final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.DECRYPT_MODE,
        properties.secretKey(),
        new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
    cipher.updateAAD(cacheKey.getBytes(StandardCharsets.UTF_8));

    final String[] tokens =
        new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8).split("\\n", -1);
    if (tokens.length != 2 || tokens[0].isBlank() || tokens[1].isBlank()) {
      throw new IllegalArgumentException(REFRESH_REPLAY_VALUE_INVALID_FORMAT);
    }

    return new RefreshReplay(tokens[0], tokens[1]);
  }

  private String encrypt(final String cacheKey, final RefreshReplay replay) {
    final byte[] nonce = new byte[NONCE_LENGTH_BYTES];
    secureRandom.nextBytes(nonce);

    try {
      final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.ENCRYPT_MODE,
          properties.secretKey(),
          new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
      cipher.updateAAD(cacheKey.getBytes(StandardCharsets.UTF_8));

      final byte[] ciphertext =
          cipher.doFinal(
              (replay.accessToken() + "\n" + replay.refreshToken())
                  .getBytes(StandardCharsets.UTF_8));
      final ByteBuffer buffer = ByteBuffer.allocate(nonce.length + ciphertext.length);
      buffer.put(nonce).put(ciphertext);
      return Base64.getEncoder().encodeToString(buffer.array());
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(REFRESH_REPLAY_ENCRYPTION_FAILED, exception);
    }
  }

  private static String cacheKey(final String scope, final String tokenHash) {
    return KEY_PREFIX + scope + ":" + tokenHash;
  }
}
