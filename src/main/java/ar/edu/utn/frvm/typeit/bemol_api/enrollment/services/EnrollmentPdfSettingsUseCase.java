package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPdfSettings;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentPdfSettingsRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfSettingsRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfSettingsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.audit.InstitutionalAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentPdfSettingsUseCase {
  private final EnrollmentPdfSettingsRepository settings;
  private final InstitutionRepository institutions;
  private final AuditEventRecorder audit;

  @Transactional(readOnly = true)
  public EnrollmentPdfSettingsResponse get(final UUID institutionId) {
    if (!institutions.existsById(institutionId)) {
      throw new InstitutionNotFoundException();
    }
    return settings
        .findById(institutionId)
        .map(EnrollmentPdfSettingsResponse::from)
        .orElseGet(EnrollmentPdfSettingsResponse::defaults);
  }

  @Transactional
  public EnrollmentPdfSettingsResponse replace(
      final UUID institutionId, final EnrollmentPdfSettingsRequest input) {
    final var institution =
        institutions
            .findByIdForUpdate(institutionId)
            .orElseThrow(InstitutionNotFoundException::new);
    final var configuration =
        settings.findById(institutionId).orElseGet(() -> EnrollmentPdfSettings.create(institution));
    final var before = configuration.auditSnapshot();
    configuration.replace(
        input.title(),
        input.headerText(),
        input.introductionText(),
        input.footerText(),
        input.showApplicantSignature(),
        input.showResponsibleSignature());
    final var after = configuration.auditSnapshot();
    if (!before.values().equals(after.values())) {
      settings.save(configuration);
      audit.recordCurrentActor(
          institution,
          null,
          InstitutionalAuditAction.INSTITUTION_UPDATED,
          Institution.AUDIT_TYPE,
          institutionId,
          AuditOrigin.administration(),
          AuditChangeDetails.change(institution.getName(), before, after));
    }
    return EnrollmentPdfSettingsResponse.from(configuration);
  }
}
