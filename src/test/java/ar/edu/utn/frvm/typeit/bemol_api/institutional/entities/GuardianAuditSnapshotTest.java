package ar.edu.utn.frvm.typeit.bemol_api.institutional.entities;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.GuardianDependentRequestMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GuardianAuditSnapshotTest {
  @Test
  void pendingRequestDoesNotExposeOrCaptureDeclaredPersonalData() {
    final var institution = Institution.builder().id(UUID.randomUUID()).build();
    final var tutor = Person.builder().id(UUID.randomUUID()).build();
    final var link =
        PersonGuardian.requestUnmaterialized(
            institution,
            tutor,
            "12345678",
            GuardianDependentRequestMode.NEW,
            GuardianRelationship.MOTHER,
            true,
            "Nombre privado",
            "Apellido privado",
            LocalDate.of(2010, 1, 1));
    link.declareContact("telefono-privado", Country.builder().id(UUID.randomUUID()).build());
    link.requestOwnAccess(true, "private@example.test");
    final var before = link.auditSnapshot();
    link.reject(null);
    final var after = link.auditSnapshot();

    assertThat(before.values())
        .containsOnlyKeys(
            "status",
            "relationship",
            "tutorPersonId",
            "dependentPersonId",
            "ownAccessRequested",
            "primaryContact");
    assertThat(before.visibleValues()).containsEntry("dependentPersonId", null);
    assertThat(after.visibleValues())
        .containsEntry("status", Map.of("code", "REJECTED", "label", "Rechazada"));
    assertThat(AuditChangeDetails.change("Vinculación", before, after).get("changedFields"))
        .isEqualTo(List.of("status"));
    assertThat(before.values().toString())
        .doesNotContain(
            "12345678",
            "Nombre privado",
            "Apellido privado",
            "telefono-privado",
            "private@example.test",
            "2010");
  }

  @Test
  void materializationRecordsIdentityWithoutSerializingTheRelatedPerson() {
    final var institution = Institution.builder().id(UUID.randomUUID()).build();
    final var link =
        PersonGuardian.requestUnmaterialized(
            institution,
            Person.builder().id(UUID.randomUUID()).build(),
            "12345678",
            GuardianDependentRequestMode.NEW,
            GuardianRelationship.OTHER,
            false,
            null,
            null,
            null);
    final var before = link.auditSnapshot();
    final var person =
        Person.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .documentNumber("12345678")
            .email("private@example.test")
            .build();
    link.materialize(person);
    link.approve(null);
    final var after = link.auditSnapshot();

    assertThat(before.visibleValues()).containsEntry("dependentPersonId", null);
    assertThat(after.visibleValues()).containsEntry("dependentPersonId", person.getId());
    assertThat(after.values().toString()).doesNotContain("12345678", "private@example.test");
  }

  @Test
  void attachmentPublishesOnlySafeMetadata() {
    final var attachment =
        PersonGuardianAttachment.builder()
            .originalFileName("archivo-privado.pdf")
            .storagePath("private/storage/key")
            .contentType("application/pdf")
            .fileSize(128L)
            .build();

    assertThat(attachment.auditSnapshot().visibleValues())
        .isEqualTo(Map.of("contentType", "application/pdf", "size", 128L));
    assertThat(attachment.auditSnapshot().values()).containsOnlyKeys("contentType", "size");
  }
}
