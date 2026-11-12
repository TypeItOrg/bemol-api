package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSubject;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "enrollment_pdf_settings")
@Getter
@NoArgsConstructor
public class EnrollmentPdfSettings implements AuditSubject {
  @Id
  @Column(name = "institution_id")
  private UUID institutionId;

  @Column(nullable = false, length = 150)
  private String title;

  @Column(name = "header_text", length = 500)
  private @Nullable String headerText;

  @Column(name = "introduction_text", length = 1000)
  private @Nullable String introductionText;

  @Column(name = "footer_text", length = 500)
  private @Nullable String footerText;

  @Column(name = "show_applicant_signature", nullable = false)
  private boolean showApplicantSignature;

  @Column(name = "show_responsible_signature", nullable = false)
  private boolean showResponsibleSignature;

  public static EnrollmentPdfSettings create(final Institution institution) {
    final var settings = new EnrollmentPdfSettings();
    settings.institutionId = institution.getId();
    settings.replace("Solicitud de inscripción", null, null, null, true, true);
    return settings;
  }

  public void replace(
      final String title,
      final @Nullable String headerText,
      final @Nullable String introductionText,
      final @Nullable String footerText,
      final boolean showApplicantSignature,
      final boolean showResponsibleSignature) {
    if (title == null
        || title.isBlank()
        || title.length() > 150
        || headerText != null && headerText.length() > 500
        || introductionText != null && introductionText.length() > 1000
        || footerText != null && footerText.length() > 500) {
      throw new EnrollmentValidationException(EnrollmentMessages.PDF_SETTINGS_INVALID);
    }

    this.title = title.strip();
    this.headerText = normalize(headerText);
    this.introductionText = normalize(introductionText);
    this.footerText = normalize(footerText);
    this.showApplicantSignature = showApplicantSignature;
    this.showResponsibleSignature = showResponsibleSignature;
  }

  private static @Nullable String normalize(final @Nullable String text) {
    return text == null || text.isBlank() ? null : text.strip();
  }

  @Override
  public AuditSnapshot.Snapshot auditSnapshot() {
    return AuditSnapshot.fields()
        .compare("pdfTitle", "Título del PDF", title)
        .compare("pdfHeaderText", "Encabezado del PDF", headerText)
        .compare("pdfIntroductionText", "Introducción del PDF", introductionText)
        .compare("pdfFooterText", "Pie del PDF", footerText)
        .expose("pdfShowApplicantSignature", "Firma de solicitante", showApplicantSignature)
        .expose("pdfShowResponsibleSignature", "Firma de responsable", showResponsibleSignature)
        .build();
  }
}
