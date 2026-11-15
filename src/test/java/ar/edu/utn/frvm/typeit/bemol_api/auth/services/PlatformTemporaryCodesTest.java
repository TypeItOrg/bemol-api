package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.WebAuthnVerificationFailedException;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PlatformTemporaryCodesTest {

  private static final Pattern CODE_PATTERN =
      Pattern.compile(
          "[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{4}-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{4}");

  @Test
  void generateProducesGroupedUnambiguousCodeAndHashesAcrossEquivalentInputs() {
    final String code = PlatformTemporaryCodes.generate();

    assertThat(code).matches(CODE_PATTERN);
    final String expectedHash = PlatformTemporaryCodes.hashSetupCode(code);
    assertThat(PlatformTemporaryCodes.hashSetupCode(code.replace("-", "").toLowerCase()))
        .isEqualTo(expectedHash);
    assertThat(PlatformTemporaryCodes.hashShortCode("  " + code.toLowerCase() + "  "))
        .isEqualTo(expectedHash);
  }

  @Test
  void hashSetupCodePreservesLegacyHexDigest() {
    final String legacyHex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    assertThat(PlatformTemporaryCodes.hashSetupCode(legacyHex))
        .isEqualTo(JwtService.hashToken(legacyHex));
  }

  @Test
  void hashRejectsInvalidLength() {
    assertThatThrownBy(() -> PlatformTemporaryCodes.hashShortCode("ABC-12"))
        .isInstanceOf(WebAuthnVerificationFailedException.class);
  }
}
