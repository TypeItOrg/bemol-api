package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrollmentPdfSettingsAuditTest {
  @Test
  void textIsComparedPrivatelyAndSignaturesRemainVisible() {
    final var settings =
        EnrollmentPdfSettings.create(Institution.builder().id(UUID.randomUUID()).build());
    final var before = settings.auditSnapshot();
    settings.replace(
        "Título privado", "Encabezado privado", "Introducción privada", "Pie privado", false, true);
    final var after = settings.auditSnapshot();
    final var details = AuditChangeDetails.change("Institución", before, after);

    assertThat(details.get("changedFields"))
        .isEqualTo(
            List.of(
                "pdfTitle",
                "pdfHeaderText",
                "pdfIntroductionText",
                "pdfFooterText",
                "pdfShowApplicantSignature"));
    assertThat(details.toString())
        .doesNotContain(
            "Título privado", "Encabezado privado", "Introducción privada", "Pie privado");
    assertThat(after.visibleValues())
        .containsOnlyKeys("pdfShowApplicantSignature", "pdfShowResponsibleSignature")
        .containsEntry("pdfShowApplicantSignature", false);
    assertThat(after.labels()).containsEntry("pdfTitle", "Título del PDF");
    assertThat(before.values()).containsEntry("pdfTitle", "Solicitud de inscripción");
  }
}
