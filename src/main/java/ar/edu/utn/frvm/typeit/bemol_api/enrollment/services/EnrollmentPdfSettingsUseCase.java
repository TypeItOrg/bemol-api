package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPdfSettings;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentPdfSettingsRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfSettingsRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfSettingsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
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
        .map(EnrollmentPdfSettingsUseCase::response)
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
    final var before = response(configuration);
    configuration.replace(
        input.title(),
        input.headerText(),
        input.introductionText(),
        input.footerText(),
        input.showApplicantSignature(),
        input.showResponsibleSignature());
    final var after = response(configuration);
    if (!before.equals(after)) {
      settings.save(configuration);
      final var beforeValues = snapshot(before);
      final var afterValues = snapshot(after);
      audit.recordCurrentActor(
          institution,
          null,
          AuditAction.INSTITUTION_UPDATED,
          AuditEntityType.INSTITUTION,
          institutionId,
          AuditOrigin.administration(),
          Map.of(
              "entityName",
              institution.getName(),
              "changedFields",
              beforeValues.keySet().stream()
                  .filter(key -> !Objects.equals(beforeValues.get(key), afterValues.get(key)))
                  .toList(),
              "before",
              signatures(before),
              "after",
              signatures(after)));
    }
    return after;
  }

  static EnrollmentPdfSettingsResponse response(final EnrollmentPdfSettings settings) {
    return new EnrollmentPdfSettingsResponse(
        settings.getTitle(),
        settings.getHeaderText(),
        settings.getIntroductionText(),
        settings.getFooterText(),
        settings.isShowApplicantSignature(),
        settings.isShowResponsibleSignature());
  }

  private static Map<String, Object> signatures(final EnrollmentPdfSettingsResponse settings) {
    return Map.of(
        "pdfShowApplicantSignature",
        settings.showApplicantSignature(),
        "pdfShowResponsibleSignature",
        settings.showResponsibleSignature());
  }

  private static Map<String, Object> snapshot(final EnrollmentPdfSettingsResponse settings) {
    final var values = new LinkedHashMap<String, Object>();
    values.put("pdfTitle", settings.title());
    values.put("pdfHeaderText", settings.headerText());
    values.put("pdfIntroductionText", settings.introductionText());
    values.put("pdfFooterText", settings.footerText());
    values.put("pdfShowApplicantSignature", settings.showApplicantSignature());
    values.put("pdfShowResponsibleSignature", settings.showResponsibleSignature());
    return values;
  }
}
