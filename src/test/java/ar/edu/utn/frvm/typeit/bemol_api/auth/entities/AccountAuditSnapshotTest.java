package ar.edu.utn.frvm.typeit.bemol_api.auth.entities;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.auth.enums.AccessInvitationPurpose;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountAuditSnapshotTest {
  private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

  @Test
  void platformProfileKeepsEmailPrivateAndDoesNotCaptureCredentials() {
    final var account =
        PlatformAccount.builder()
            .name("Ana")
            .lastName("Pérez")
            .email("before@example.test")
            .password("old-secret-hash")
            .build();
    final var before = account.auditSnapshot();
    account.updateProfile("Ana", "Pérez", "after@example.test");
    account.changePassword("new-secret-hash");
    final var details = AuditChangeDetails.change("Cuenta", before, account.auditSnapshot());

    assertThat(details.get("changedFields")).isEqualTo(List.of("email"));
    assertThat(before.values()).doesNotContainKey("password");
    assertThat(before.visibleValues()).containsOnlyKeys("firstName", "lastName", "enabled");
    assertThat(details.toString())
        .doesNotContain("before@example.test", "after@example.test", "secret-hash");
    assertThat(before.values()).containsEntry("email", "before@example.test");
  }

  @Test
  void passwordSnapshotsNeverExposeOrPrintHashes() {
    final var platform = PlatformAccount.builder().password("secret-platform-before").build();
    final var institutional = User.builder().password("secret-user-before").build();
    final var beforePlatform = platform.passwordAuditSnapshot();
    final var beforeUser = institutional.passwordAuditSnapshot();
    platform.changePassword("secret-platform-after");
    institutional.changePassword("secret-user-after");

    for (final var details :
        List.of(
            AuditChangeDetails.change("Cuenta", beforePlatform, platform.passwordAuditSnapshot()),
            AuditChangeDetails.change(
                "Cuenta", beforeUser, institutional.passwordAuditSnapshot()))) {
      assertThat(details.get("changedFields")).isEqualTo(List.of("password"));
      assertThat(details.get("before")).isEqualTo(Map.of());
      assertThat(details.get("after")).isEqualTo(Map.of());
      assertThat(details.toString()).doesNotContain("secret-");
    }
    assertThat(beforePlatform.toString()).doesNotContain("secret-");
    assertThat(beforeUser.toString()).doesNotContain("secret-");
  }

  @Test
  void institutionalStateUsesLabeledCodesAndDoesNotConfuseAccessSwitches() {
    final var person = Person.builder().email("private@example.test").build();
    final var user = User.builder().person(person).password("secret-hash").build();
    final var before = user.auditSnapshot();
    user.updateSelfAccess(false);
    final var after = user.auditSnapshot();

    assertThat(AuditChangeDetails.change("Cuenta", before, after).get("changedFields"))
        .isEqualTo(List.of("selfAccessEnabled"));
    assertThat(after.visibleValues())
        .containsEntry("enabled", true)
        .containsEntry("selfAccessEnabled", false)
        .containsEntry(
            "emailVerificationStatus", Map.of("code", "NOT_REQUIRED", "label", "No requerida"));
    assertThat(after.visibleValues().toString())
        .doesNotContain("private@example.test", "secret-hash");
  }

  @Test
  void requestedEmailsAreComparisonOnlyAndTokensAreOmitted() {
    final var platform = PlatformAccount.builder().email("previous@example.test").build();
    final var user =
        User.builder().person(Person.builder().email("previous@example.test").build()).build();
    final var platformToken =
        PlatformEmailChangeToken.issue(
            platform,
            "new@example.test",
            "secret-fingerprint",
            "secret-token",
            NOW,
            NOW.plusSeconds(3600));
    final var accountToken =
        AccountEmailChangeToken.issue(
            user, null, platform, "new@example.test", "secret-token", NOW, NOW.plusSeconds(3600));

    for (final var snapshot :
        List.of(platformToken.auditSnapshot(), accountToken.auditSnapshot())) {
      assertThat(snapshot.values())
          .containsOnlyKeys("email")
          .containsEntry("email", "new@example.test");
      assertThat(snapshot.visibleValues()).isEmpty();
      assertThat(AuditChangeDetails.change("Cuenta", AuditSnapshot.empty(), snapshot).toString())
          .doesNotContain(
              "new@example.test", "previous@example.test", "secret-token", "secret-fingerprint");
    }
  }

  @Test
  void invitationDeclaresItsPurposeAndStateWithoutEmailOrTokenValues() {
    final var institution = Institution.builder().id(UUID.randomUUID()).build();
    final var person =
        Person.builder().institution(institution).email("before@example.test").build();
    final var invitation =
        PersonAccessInvitation.issueAdministratively(
            person,
            null,
            new PlatformAccount(),
            AccessInvitationPurpose.AUTONOMY,
            "private@example.test",
            "secret-token",
            NOW,
            NOW.plusSeconds(3600));
    final var before = invitation.auditSnapshot();
    invitation.consume(NOW.plusSeconds(10));
    final var details = AuditChangeDetails.change("Invitación", before, invitation.auditSnapshot());

    assertThat(details.get("changedFields")).isEqualTo(List.of("consumed"));
    assertThat(before.visibleValues())
        .containsEntry("purpose", Map.of("code", "AUTONOMY", "label", "Autonomía"));
    assertThat(details.toString())
        .doesNotContain("private@example.test", "before@example.test", "secret-token");
  }
}
