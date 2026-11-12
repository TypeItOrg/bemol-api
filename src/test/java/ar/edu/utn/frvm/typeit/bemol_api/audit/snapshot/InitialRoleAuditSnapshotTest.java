package ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InitialRoleAuditSnapshotTest {
  @Test
  void personDeclaresTheInitialRoleCodeAndLabelWithoutLeakingPrivateFields() {
    final var person =
        Person.builder()
            .firstName("Ana")
            .lastName("Pérez")
            .documentNumber("12345678")
            .email("private@example.test")
            .build();
    final var snapshot = person.auditSnapshot(SystemRoleCode.APPLICANT);
    final var details = AuditChangeDetails.change("Persona", AuditSnapshot.empty(), snapshot);

    assertThat(snapshot.values()).containsEntry("initialRole", "APPLICANT");
    assertThat(snapshot.visibleValues())
        .containsEntry(
            "initialRole",
            Map.of("code", "APPLICANT", "label", SystemRoleCode.APPLICANT.getDisplayName()));
    assertThat(snapshot.labels()).containsEntry("initialRole", "Rol inicial");
    assertThat(details.toString()).doesNotContain("12345678", "private@example.test");
    assertThat(person.auditSnapshot().values()).doesNotContainKey("initialRole");
  }

  @Test
  void platformAccountOwnsItsCreationFieldsWithoutCapturingCredentials() {
    final var account =
        PlatformAccount.builder()
            .name("Ana")
            .lastName("Pérez")
            .email("private@example.test")
            .password("secret-hash")
            .build();
    final var snapshot = account.auditSnapshot(PlatformRoleCode.PLATFORM_ADMIN);
    final var details = AuditChangeDetails.change("Cuenta", AuditSnapshot.empty(), snapshot);

    assertThat(snapshot.visibleValues())
        .containsEntry(
            "initialRole",
            Map.of(
                "code",
                "PLATFORM_ADMIN",
                "label",
                PlatformRoleCode.PLATFORM_ADMIN.getDisplayName()));
    assertThat(snapshot.values()).doesNotContainKey("password");
    assertThat(details.toString()).doesNotContain("private@example.test", "secret-hash");
  }
}
