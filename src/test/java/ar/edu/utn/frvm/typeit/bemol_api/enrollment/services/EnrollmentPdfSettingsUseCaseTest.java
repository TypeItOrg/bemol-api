package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPdfSettings;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentPdfSettingsRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfSettingsRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfSettingsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import jakarta.validation.Validation;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EnrollmentPdfSettingsUseCaseTest {
  private final UUID id = UUID.randomUUID();
  private final Institution institution = Institution.builder().id(id).name("Institución").build();
  private final EnrollmentPdfSettingsRepository repository =
      mock(EnrollmentPdfSettingsRepository.class);
  private final InstitutionRepository institutions = mock(InstitutionRepository.class);
  private final AuditEventRecorder audit = mock(AuditEventRecorder.class);
  private final EnrollmentPdfSettingsUseCase service =
      new EnrollmentPdfSettingsUseCase(repository, institutions, audit);

  @Test
  void defaultsDoNotCreateConfiguration() {
    when(institutions.existsById(id)).thenReturn(true);
    when(repository.findById(id)).thenReturn(Optional.empty());
    assertThat(service.get(id)).isEqualTo(EnrollmentPdfSettingsResponse.defaults());
    verify(repository, never()).save(any());
    verifyNoInteractions(audit);
    assertThatThrownBy(() -> service.get(UUID.randomUUID()))
        .isInstanceOf(InstitutionNotFoundException.class);
  }

  @Test
  void replacementNormalizesAndAuditsOnlyChangedFieldNames() {
    when(institutions.findByIdForUpdate(id)).thenReturn(Optional.of(institution));
    when(repository.findById(id)).thenReturn(Optional.empty());
    final var input =
        new EnrollmentPdfSettingsRequest(
            "  Mi título  ", " Línea 1\nLínea 2 ", " ", null, false, true);
    final var result = service.replace(id, input);
    assertThat(result)
        .isEqualTo(
            new EnrollmentPdfSettingsResponse(
                "Mi título", "Línea 1\nLínea 2", null, null, false, true));
    verify(repository).save(any());
    final ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.captor();
    verify(audit)
        .recordCurrentActor(
            eq(institution), isNull(), any(), any(), eq(id), any(), details.capture());
    assertThat(details.getValue().toString()).doesNotContain("Mi título", "Línea 1", "Línea 2");
    assertThat(details.getValue()).containsKey("changedFields");
  }

  @Test
  void identicalReplacementDoesNotWriteOrAudit() {
    when(institutions.findByIdForUpdate(id)).thenReturn(Optional.of(institution));
    when(repository.findById(id))
        .thenReturn(Optional.of(EnrollmentPdfSettings.create(institution)));
    service.replace(
        id,
        new EnrollmentPdfSettingsRequest("Solicitud de inscripción", null, null, null, true, true));
    verify(repository, never()).save(any());
    verifyNoInteractions(audit);
  }

  @Test
  void entityRejectsInvalidConfigurationAndRequestRequiresExplicitBooleans() {
    final var settings = EnrollmentPdfSettings.create(institution);
    assertThatThrownBy(() -> settings.replace(" ", null, null, null, true, true))
        .isInstanceOf(EnrollmentValidationException.class);
    assertThatThrownBy(() -> settings.replace("x".repeat(151), null, null, null, true, true))
        .isInstanceOf(EnrollmentValidationException.class);
    assertThatThrownBy(() -> settings.replace("Título", "x".repeat(501), null, null, true, true))
        .isInstanceOf(EnrollmentValidationException.class);
    assertThatThrownBy(() -> settings.replace("Título", null, "x".repeat(1001), null, true, true))
        .isInstanceOf(EnrollmentValidationException.class);
    assertThatThrownBy(() -> settings.replace("Título", null, null, "x".repeat(501), true, true))
        .isInstanceOf(EnrollmentValidationException.class);
    try (final var factory = Validation.buildDefaultValidatorFactory()) {
      final var validator = factory.getValidator();
      for (final var property :
          java.util.List.of("showApplicantSignature", "showResponsibleSignature")) {
        assertThat(validator.validateValue(EnrollmentPdfSettingsRequest.class, property, null))
            .extracting(violation -> violation.getPropertyPath().toString())
            .containsExactly(property);
      }
      assertThat(
              validator.validate(
                  new EnrollmentPdfSettingsRequest("Título", null, null, null, false, false)))
          .isEmpty();
    }
  }
}
