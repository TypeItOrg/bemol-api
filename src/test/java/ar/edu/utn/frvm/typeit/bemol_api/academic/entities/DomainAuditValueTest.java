package ar.edu.utn.frvm.typeit.bemol_api.academic.entities;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.ApprovalMode;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.CourseStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.RequirementType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.DocumentRequirementLevel;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DomainAuditValueTest {
  @ParameterizedTest
  @EnumSource(CourseStatus.class)
  void coursePreservesComparisonCodesAndPublishesDomainLabels(final CourseStatus status) {
    final var snapshot = Course.builder().status(status).build().auditSnapshot();

    assertThat(snapshot.values()).containsEntry("status", status.name());
    assertThat(snapshot.visibleValues())
        .containsEntry("status", Map.of("code", status.name(), "label", status.getLabel()));
  }

  @Test
  void academicSpaceOwnsTypeAndFormatLabels() {
    final var space =
        AcademicSpace.create(
            new Institution(),
            "Piano",
            null,
            AcademicSpaceType.SUBJECT,
            AcademicSpaceFormat.INDIVIDUAL);
    final var snapshot = space.auditSnapshot();

    assertThat(snapshot.values())
        .containsEntry("type", "SUBJECT")
        .containsEntry("format", "INDIVIDUAL");
    assertThat(snapshot.visibleValues())
        .containsEntry("type", Map.of("code", "SUBJECT", "label", "Asignatura"))
        .containsEntry("format", Map.of("code", "INDIVIDUAL", "label", "Individual"));
  }

  @Test
  void documentGroupsIncludeTheirOwnPublicLabelsWithoutPrivateKeys() {
    final var institution = Institution.builder().id(UUID.randomUUID()).name("Escuela").build();
    final var document = DocumentDefinition.create(institution);
    final var assignment =
        TrainingPathDocumentRequirement.create(
            TrainingPath.builder()
                .id(UUID.randomUUID())
                .institution(institution)
                .name("Trayecto")
                .build(),
            document);
    assignment.update(DocumentRequirementLevel.OPTIONAL, 1, true, "Instrucción privada");
    final var snapshot = document.auditSnapshot(List.of(assignment));

    assertThat(assignment.auditSnapshot().values()).containsEntry("level", "OPTIONAL");
    assertThat(assignment.auditSnapshot().visibleValues())
        .containsEntry("level", Map.of("code", "OPTIONAL", "label", "Opcional"));
    assertThat(snapshot.visibleValues().toString())
        .contains("fieldLabels", "Trayecto formativo", "Opcional")
        .doesNotContain("specificInstructions", "Instrucción privada", "trainingPathId");
    assertThat(RequirementType.OPTIONAL.getLabel()).isEqualTo("Optativo");
  }

  @Test
  void planSpaceDoesNotNeedFrontendCatalogsForItsCodes() {
    final var space =
        StudyPlanSpace.builder()
            .academicSpace(AcademicSpace.builder().id(UUID.randomUUID()).name("Piano").build())
            .requirementType(RequirementType.REQUIRED)
            .approvalMode(ApprovalMode.FINAL_EXAM)
            .build();
    final var snapshot = space.auditSnapshot();

    assertThat(snapshot.visibleValues())
        .containsEntry("requirementType", Map.of("code", "REQUIRED", "label", "Obligatorio"))
        .containsEntry("approvalMode", Map.of("code", "FINAL_EXAM", "label", "Examen final"));
  }

  @Test
  void datesHaveAnExplicitTypeWithoutChangingComparisonValues() {
    final var from = LocalDate.of(2026, 1, 1);
    final var until = LocalDate.of(2026, 12, 31);
    final var plan = StudyPlan.create(new Institution(), new TrainingPath(), "Plan", from, until);
    final var snapshot = plan.auditSnapshot();

    assertThat(snapshot.values()).containsEntry("effectiveFrom", "2026-01-01");
    assertThat(snapshot.visibleValues())
        .containsEntry("effectiveFrom", Map.of("type", "DATE", "value", "2026-01-01"));
    assertThat(AuditSnapshot.dateTime(Instant.parse("2026-11-11T15:30:00Z")))
        .isEqualTo(Map.of("type", "DATETIME", "value", "2026-11-11T15:30:00Z"));
    assertThat(AuditSnapshot.date(null)).isNull();
    assertThat(AuditSnapshot.dateTime(null)).isNull();
  }
}
