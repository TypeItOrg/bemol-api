package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PasskeyCredential;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformPasskeyCredential;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
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

  @Test
  void snapshotsExposeOnlyLabelAndStateAndIgnoreAuthenticationCounters() {
    final var credential = credentialWith("Equipo");
    final var before = credential.auditSnapshot();
    credential.markUsed(Instant.parse("2026-11-11T12:00:00Z"), 42);
    assertThat(credential.auditSnapshot().values()).isEqualTo(before.values());
    credential.rename("Equipo nuevo");
    credential.revoke(Instant.parse("2026-11-11T13:00:00Z"));
    final var details =
        AuditChangeDetails.change("Equipo nuevo", before, credential.auditSnapshot());
    assertThat(before.visibleValues()).containsOnlyKeys("label", "active");
    assertThat(before.visibleValues())
        .containsEntry("label", "Equipo")
        .containsEntry("active", true);
    assertThat(details.toString())
        .doesNotContain("credential-id", "publicKeyCose", "userHandle", "signatureCount");
    assertThat(details.get("changedFields")).isEqualTo(java.util.List.of("label", "active"));
  }

  @Test
  void platformSnapshotUsesTheSameSafeFieldsWithoutCredentialMaterial() {
    final var credential =
        PlatformPasskeyCredential.builder()
            .label("Equipo")
            .credentialId("private-credential-id")
            .publicKeyCose(new byte[] {1, 2, 3})
            .userHandle(new byte[] {4, 5, 6})
            .build();
    final var before = credential.auditSnapshot();
    credential.markUsed(Instant.parse("2026-11-11T12:00:00Z"), 3);
    assertThat(credential.auditSnapshot().values()).isEqualTo(before.values());
    credential.revoke(Instant.parse("2026-11-11T13:00:00Z"));
    final var details = AuditChangeDetails.change("Equipo", before, credential.auditSnapshot());
    assertThat(before.visibleValues()).containsOnlyKeys("label", "active");
    assertThat(details.toString())
        .doesNotContain("private-credential-id", "publicKeyCose", "userHandle", "signatureCount");
    assertThat(details.get("changedFields")).isEqualTo(java.util.List.of("active"));
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
