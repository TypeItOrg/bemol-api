package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.boero_api.auth.entities.PasskeyCredential;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasskeyCredentialTest {

  @Test
  @DisplayName("Should rename with trim and reject blank or oversized labels")
  void rename_validatesLabel() {
    final PasskeyCredential credential = credentialWith("Old");

    credential.rename("  New name  ");

    assertThat(credential.getLabel()).isEqualTo("New name");
    assertThatThrownBy(() -> credential.rename("   ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> credential.rename("x".repeat(101)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Should soft revoke once and stay inactive")
  void revoke_softDeletesOnce() {
    final PasskeyCredential credential = credentialWith("Key");

    assertThat(credential.isActive()).isTrue();
    final var revokedAt = Instant.parse("2026-09-14T15:00:00Z");
    assertThat(credential.revoke(revokedAt)).isTrue();
    assertThat(credential.isActive()).isFalse();
    assertThat(credential.revoke(revokedAt.plusSeconds(60))).isFalse();
    assertThat(credential.getRevokedAt()).isEqualTo(revokedAt);
  }

  private static PasskeyCredential credentialWith(final String label) {
    final Institution institution = Institution.builder().id(UUID.randomUUID()).build();
    final User user =
        User.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .person(
                Person.builder()
                    .id(UUID.randomUUID())
                    .institution(institution)
                    .documentNumber("12345678")
                    .build())
            .password("hash")
            .build();
    return PasskeyCredential.builder()
        .user(user)
        .credentialId("credential-id")
        .publicKeyCose(new byte[] {1, 2, 3})
        .userHandle(new byte[] {4, 5, 6})
        .label(label)
        .build();
  }
}
