package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.WebAuthnVerificationFailedException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

/** Generates and hashes short single-use temporary codes for platform passkey flows. */
public final class PlatformTemporaryCodes {
  private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final int GROUP_LENGTH = 4;
  private static final int CODE_LENGTH = GROUP_LENGTH * 2;
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Pattern LEGACY_HEX_CODE = Pattern.compile("[a-f0-9]{64}");
  private static final Pattern SEPARATORS = Pattern.compile("[\\s-]");

  private PlatformTemporaryCodes() {}

  public static String generate() {
    final var builder = new StringBuilder(CODE_LENGTH + 1);

    for (int i = 0; i < CODE_LENGTH; i++) {
      if (i == GROUP_LENGTH) {
        builder.append('-');
      }
      builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }

    return builder.toString();
  }

  public static String hashSetupCode(final String raw) {
    final String trimmed = raw.trim();

    if (LEGACY_HEX_CODE.matcher(trimmed).matches()) {
      return JwtService.hashToken(trimmed);
    }

    return hashShortCode(trimmed);
  }

  static String hashShortCode(final String raw) {
    final String normalized = SEPARATORS.matcher(raw).replaceAll("");

    if (normalized.length() != CODE_LENGTH) {
      throw new WebAuthnVerificationFailedException();
    }

    return JwtService.hashToken(normalized.toUpperCase(Locale.ROOT));
  }
}
