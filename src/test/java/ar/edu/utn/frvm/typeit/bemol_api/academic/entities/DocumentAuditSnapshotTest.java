package ar.edu.utn.frvm.typeit.bemol_api.academic.entities;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.DocumentRequirementLevel;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentAuditSnapshotTest {
  @Test
  void comparesPrivateInstructionsWithoutPublishingThemAtEitherLevel() {
    final var institution =
        Institution.builder().id(UUID.randomUUID()).name("Conservatorio").build();
    final var document = DocumentDefinition.create(institution);
    document.update("Documento", "Instrucción privada anterior", List.of("application/pdf"), true);
    final var path =
        TrainingPath.builder()
            .id(UUID.randomUUID())
            .name("Trayecto")
            .institution(institution)
            .build();
    final var requirement = TrainingPathDocumentRequirement.create(path, document);
    requirement.update(DocumentRequirementLevel.AT_SUBMISSION, 1, true, "Detalle privado anterior");
    final var before = document.auditSnapshot(List.of(requirement));

    document.update("Documento", "Instrucción privada nueva", List.of("application/pdf"), true);
    requirement.update(DocumentRequirementLevel.AT_SUBMISSION, 1, true, "Detalle privado nuevo");
    final var after = document.auditSnapshot(List.of(requirement));
    final var details = AuditChangeDetails.change(document.getName(), before, after);

    assertThat(DocumentDefinition.definitionChanged(before, after)).isTrue();
    assertThat(details.get("changedFields")).isEqualTo(List.of("instructions", "requirements"));
    assertThat(before.visibleValues()).isEqualTo(after.visibleValues());
    assertThat(details.toString())
        .doesNotContain(
            "Instrucción privada",
            "Detalle privado",
            "specificInstructions",
            "trainingPathId",
            institution.getId().toString());
    assertThat(requirement.auditSnapshot().visibleValues())
        .containsEntry("trainingPathName", "Trayecto")
        .doesNotContainKeys("trainingPathId", "specificInstructions");
  }

  @Test
  void assignmentOrderDoesNotCreateFalseChanges() {
    final var institution =
        Institution.builder().id(UUID.randomUUID()).name("Conservatorio").build();
    final var document = DocumentDefinition.create(institution);
    document.update("Documento", "Privado", List.of("application/pdf"), true);
    final var first =
        TrainingPathDocumentRequirement.create(
            TrainingPath.builder()
                .id(UUID.randomUUID())
                .name("Uno")
                .institution(institution)
                .build(),
            document);
    final var second =
        TrainingPathDocumentRequirement.create(
            TrainingPath.builder()
                .id(UUID.randomUUID())
                .name("Dos")
                .institution(institution)
                .build(),
            document);
    first.update(DocumentRequirementLevel.AT_SUBMISSION, 1, true, null);
    second.update(DocumentRequirementLevel.OPTIONAL, 2, false, null);

    assertThat(document.auditSnapshot(List.of(first, second)))
        .isEqualTo(document.auditSnapshot(List.of(second, first)));
  }
}
