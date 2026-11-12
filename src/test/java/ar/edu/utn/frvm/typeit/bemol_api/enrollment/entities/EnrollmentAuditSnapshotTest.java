package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.AcademicEnrollmentStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseEnrollmentStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.DocumentReviewStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationCourseStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrollmentAuditSnapshotTest {
  @Test
  void enrollmentOwnsStatusLabelsAndSeparatesPrivateReasons() {
    final var enrollment = CourseEnrollment.builder().course(course(UUID.randomUUID())).build();
    final var before = enrollment.auditSnapshot();
    final var previousReason = enrollment.auditReasonSnapshot("Motivo privado anterior");
    enrollment.recordAcademicResult(
        AcademicEnrollmentStatus.PASSED, Instant.now(), "Motivo privado nuevo");
    final var after = enrollment.auditSnapshot();

    assertThat(before.values()).containsEntry("status", "ENROLLED");
    assertThat(after.visibleValues())
        .containsEntry("academicStatus", Map.of("code", "PASSED", "label", "Aprobado"));
    assertThat(AuditChangeDetails.change("Cursada", before, after).get("changedFields"))
        .isEqualTo(List.of("status", "academicStatus"));
    final var details =
        AuditChangeDetails.change(
            "Cursada",
            previousReason,
            enrollment.auditReasonSnapshot(enrollment.getActionReason()));
    assertThat(details.get("changedFields")).isEqualTo(List.of("actionReason"));
    assertThat(details.toString())
        .doesNotContain("Motivo privado anterior", "Motivo privado nuevo");
    assertThat(after.values()).doesNotContainKey("actionReason");
  }

  @Test
  void historicalStatusSnapshotDistinguishesNullFromARecordedAcademicState() {
    final var enrollment = CourseEnrollment.builder().course(course(UUID.randomUUID())).build();
    final var before = enrollment.auditStatusSnapshot(CourseEnrollmentStatus.COMPLETED, null);

    assertThat(before.visibleValues()).containsEntry("academicStatus", null);
    assertThat(before.values()).containsEntry("status", "COMPLETED");
    assertThat(enrollment.auditSnapshot().values()).containsEntry("status", "ENROLLED");
  }

  @Test
  void selectionsCompareCourseIdentityAndKeepResolutionTextOutOfSnapshots() {
    final var selection =
        EnrollmentApplicationCourse.builder()
            .course(course(UUID.randomUUID()))
            .status(EnrollmentApplicationCourseStatus.PENDING)
            .build();
    final var before = selection.auditSnapshot();
    selection.reject("ADMIN_REJECTED", "Rechazo privado", Instant.now(), UUID.randomUUID());
    final var after = selection.auditSnapshot();

    assertThat(AuditChangeDetails.change("Selección", before, after).get("changedFields"))
        .isEqualTo(List.of("status"));
    assertThat(after.visibleValues())
        .containsEntry("status", Map.of("code", "REJECTED", "label", "Rechazado"));
    assertThat(after.values().toString()).doesNotContain("Rechazo privado");
    final var other =
        EnrollmentApplicationCourse.builder()
            .course(course(UUID.randomUUID()))
            .status(EnrollmentApplicationCourseStatus.REJECTED)
            .build();
    assertThat(other.auditSnapshot().values()).isNotEqualTo(after.values());
    assertThat(selection.auditSnapshot(selection.getStatus(), true).visibleValues())
        .containsEntry("deleted", true);
  }

  @Test
  void attachmentIncludesOnlySafeMetadataAndKeepsTheBeforeStateIndependent() {
    final var application = EnrollmentApplication.builder().id(UUID.randomUUID()).build();
    final var attachment =
        EnrollmentAttachment.builder()
            .enrollmentApplication(application)
            .originalFileName("archivo-privado.pdf")
            .storagePath("private/storage/path")
            .contentType("application/pdf")
            .fileSize(128L)
            .build();
    final var before = attachment.auditSnapshot();
    attachment.review(
        DocumentReviewStatus.OBSERVED,
        "Observación privada",
        UUID.randomUUID(),
        "PLATFORM",
        Instant.now());
    final var after = attachment.auditSnapshot();

    assertThat(after.visibleValues())
        .containsOnlyKeys("applicationId", "versionStatus", "reviewStatus", "contentType", "size");
    assertThat(before.values()).containsEntry("reviewStatus", "PENDING_REVIEW");
    assertThat(after.visibleValues())
        .containsEntry("reviewStatus", Map.of("code", "OBSERVED", "label", "Observado"));
    assertThat(AuditChangeDetails.change("Adjunto", before, after).toString())
        .doesNotContain("archivo-privado.pdf", "private/storage/path", "Observación privada");
  }

  @Test
  void admissionHasItsOwnSafeRepresentationWithoutDraftAnswers() {
    final var application =
        EnrollmentApplication.builder().status(EnrollmentApplicationStatus.APPROVED).build();

    assertThat(application.admissionAuditSnapshot().visibleValues())
        .containsOnlyKeys("status")
        .containsEntry("status", Map.of("code", "APPROVED", "label", "Aprobada"));
  }

  @Test
  void formVersionExposesOnlyReferencesAndRevisionNotDefinitionContents() {
    final var institution = Institution.builder().id(UUID.randomUUID()).build();
    final var form = EnrollmentForm.create(institution, "Formulario");
    final var version = EnrollmentFormVersion.draft(form, 2, EnrollmentFormBaseDefinition.create());

    assertThat(form.auditSnapshot().visibleValues()).containsOnlyKeys("formId", "revision");
    assertThat(version.auditSnapshot().visibleValues())
        .containsOnlyKeys("formId", "versionId", "versionNumber", "revision")
        .containsEntry("versionNumber", 2);
    assertThat(version.auditSnapshot().values()).doesNotContainKey("definition");
  }

  @Test
  void documentRequestPublishesTheCountButNotItsPrivateReason() {
    final var institution = Institution.builder().id(UUID.randomUUID()).build();
    final var application = EnrollmentApplication.builder().institution(institution).build();
    final var request =
        EnrollmentDocumentRequest.create(
            application,
            "Motivo privado",
            Instant.now(),
            UUID.randomUUID(),
            "PLATFORM",
            "Operador");
    final var snapshot = request.auditSnapshot(3);

    assertThat(snapshot.visibleValues())
        .containsOnlyKeys("requirementCount")
        .containsEntry("requirementCount", 3);
    assertThat(AuditChangeDetails.change("Pedido", AuditSnapshot.empty(), snapshot).toString())
        .doesNotContain("Motivo privado");
    assertThat(snapshot.labels()).containsEntry("requestReason", "Motivo del pedido");
  }

  private Course course(final UUID id) {
    final var course = mock(Course.class);
    when(course.getId()).thenReturn(id);
    return course;
  }
}
