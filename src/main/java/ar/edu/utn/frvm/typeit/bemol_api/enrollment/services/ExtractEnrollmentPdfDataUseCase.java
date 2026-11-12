package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCapabilitiesService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPdfSettings;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentApplicationNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.InvalidEnrollmentApplicationStateException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentPdfApplicationRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData.Field;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData.Section;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData.Selection;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfSettingsRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfSettingsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.InstitutionPublicAccessResponse;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExtractEnrollmentPdfDataUseCase {
  private final AcademicAccessGuard accessGuard;
  private final InstitutionalCapabilitiesService capabilities;
  private final EnrollmentPdfApplicationRepository applications;
  private final EnrollmentApplicationCourseAuthorization courses;
  private final EnrollmentAdmissionHistory history;
  private final EnrollmentPdfSettingsUseCase settings;
  private final InstitutionRepository institutions;
  private final Clock clock;

  @Transactional(readOnly = true)
  public EnrollmentPdfData extract(
      final UUID institutionId, final UUID applicationId, final @Nullable UUID subjectPersonId) {
    if (subjectPersonId == null) {
      accessGuard.require(
          PermissionCode.ENROLLMENT_APPLICATION_READ,
          institutionId,
          ScopedResource.ENROLLMENT_APPLICATION,
          applicationId);
    }
    final var application =
        applications
            .findForPdf(institutionId, applicationId)
            .filter(
                value ->
                    subjectPersonId == null
                        || value.getApplicantPerson().getId().equals(subjectPersonId))
            .orElseThrow(EnrollmentApplicationNotFoundException::new);
    if (subjectPersonId != null) {
      capabilities.requireOwnEnrollmentRead(institutionId, subjectPersonId);
    }
    if (!Set.of(
            EnrollmentApplicationStatus.SUBMITTED,
            EnrollmentApplicationStatus.PROVISIONALLY_APPROVED,
            EnrollmentApplicationStatus.APPROVED,
            EnrollmentApplicationStatus.REJECTED)
        .contains(application.getStatus())) {
      throw new InvalidEnrollmentApplicationStateException(EnrollmentMessages.PDF_STATE_INVALID);
    }
    final var submittedAt =
        history.list(applicationId).stream()
            .filter(event -> event.status().equals(EnrollmentApplicationStatus.SUBMITTED.name()))
            .map(event -> event.occurredAt())
            .findFirst()
            .orElse(null);

    return EnrollmentPdfDataMapper.from(
            application,
            settings.get(institutionId),
            courses.canRead(application),
            clock.instant(),
            submittedAt)
        .toBuilder()
        .institutionId(application.getInstitution().getId())
        .logoUrl(InstitutionPublicAccessResponse.logoUrl(application.getInstitution()))
        .build();
  }

  @Transactional(readOnly = true)
  public EnrollmentPdfData formExample(
      final UUID institutionId, final EnrollmentFormDefinition definition) {
    definition.validateForPublication();
    final var institution =
        institutions
            .findWithLocationById(institutionId)
            .orElseThrow(InstitutionNotFoundException::new);

    return EnrollmentPdfFormPreview.from(
            definition, institution, settings.get(institutionId), clock.instant())
        .toBuilder()
        .institutionId(institution.getId())
        .logoUrl(InstitutionPublicAccessResponse.logoUrl(institution))
        .build();
  }

  @Transactional(readOnly = true)
  public EnrollmentPdfData example(
      final UUID institutionId, final EnrollmentPdfSettingsRequest input) {
    final var institution =
        institutions
            .findWithLocationById(institutionId)
            .orElseThrow(InstitutionNotFoundException::new);
    final var configuration = EnrollmentPdfSettings.create(institution);
    configuration.replace(
        input.title(),
        input.headerText(),
        input.introductionText(),
        input.footerText(),
        input.showApplicantSignature(),
        input.showResponsibleSignature());

    return EnrollmentPdfData.builder()
        .institution(EnrollmentPdfDataMapper.institution(institution))
        .coursesSelected(false)
        .generatedAt(clock.instant())
        .settings(EnrollmentPdfSettingsResponse.from(configuration))
        .hasResponsible(true)
        .institutionId(institution.getId())
        .logoUrl(InstitutionPublicAccessResponse.logoUrl(institution))
        .sections(
            List.of(
                new Section(
                    "Estudiante",
                    List.of(
                        new Field("Apellido y nombre", "Persona de ejemplo"),
                        new Field("Documento nacional de identidad", "Dato ficticio"),
                        new Field("Nacionalidad", "Sin informar"),
                        new Field("Domicilio", "Domicilio de ejemplo"),
                        new Field("Teléfono", "Sin informar"))),
                new Section(
                    "Inscripción",
                    List.of(
                        new Field("Propuesta académica", "Propuesta académica de ejemplo"),
                        new Field("Ciclo lectivo", "2026"))),
                new Section(
                    "Escolaridad",
                    List.of(
                        new Field("Estudia actualmente", "Sí"),
                        new Field("Escuela o institución", "Institución de ejemplo"))),
                new Section(
                    "Responsable",
                    List.of(
                        new Field("Apellido y nombre", "Responsable de ejemplo"),
                        new Field("Correo electrónico", "Sin informar"))),
                new Section(
                    "Preferencias y autorizaciones",
                    List.of(new Field("Autoriza el uso de imágenes", "No")))))
        .selections(
            List.of(
                new Selection(
                    "Espacio académico de ejemplo",
                    "Nivel 1",
                    "No corresponde",
                    "Plan de ejemplo",
                    "Solicitada")))
        .build();
  }
}
