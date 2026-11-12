package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCapabilitiesService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.*;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentApplicationNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.InvalidEnrollmentApplicationStateException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentPdfApplicationRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentAdmissionHistoryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfSettingsRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfSettingsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.access.AccessDeniedException;

class ExtractEnrollmentPdfDataUseCaseTest {
  private final UUID institutionId = UUID.randomUUID();
  private final UUID applicationId = UUID.randomUUID();
  private final UUID personId = UUID.randomUUID();
  private final AcademicAccessGuard guard = mock(AcademicAccessGuard.class);
  private final InstitutionalCapabilitiesService capabilities =
      mock(InstitutionalCapabilitiesService.class);
  private final EnrollmentPdfApplicationRepository applications =
      mock(EnrollmentPdfApplicationRepository.class);
  private final EnrollmentApplicationCourseAuthorization courses =
      mock(EnrollmentApplicationCourseAuthorization.class);
  private final EnrollmentAdmissionHistory history = mock(EnrollmentAdmissionHistory.class);
  private final EnrollmentPdfSettingsUseCase settings = mock(EnrollmentPdfSettingsUseCase.class);
  private final InstitutionRepository institutions = mock(InstitutionRepository.class);
  private final Instant now = Instant.parse("2026-11-05T12:00:00Z");
  private final ExtractEnrollmentPdfDataUseCase service =
      new ExtractEnrollmentPdfDataUseCase(
          guard,
          capabilities,
          applications,
          courses,
          history,
          settings,
          institutions,
          Clock.fixed(now, ZoneOffset.UTC));
  private Institution institution;

  @BeforeEach
  void setUp() {
    final var country = InstitutionalTestData.country("ARG");
    final var province = InstitutionalTestData.province(country, "1");
    institution =
        Institution.builder()
            .id(institutionId)
            .name("Institución")
            .city(InstitutionalTestData.city(province, "2"))
            .build();
  }

  private EnrollmentApplication application(final EnrollmentApplicationStatus status) {
    final var person =
        Person.builder()
            .id(personId)
            .institution(institution)
            .firstName("María")
            .lastName("Muñoz")
            .documentNumber("12345678")
            .build();
    return EnrollmentApplication.builder()
        .id(applicationId)
        .institution(institution)
        .applicantPerson(person)
        .status(status)
        .preference(
            ApplicantPreference.builder().allowsImageUse(false).isReenrolling(false).build())
        .build();
  }

  @ParameterizedTest
  @EnumSource(
      value = EnrollmentApplicationStatus.class,
      names = {"SUBMITTED", "PROVISIONALLY_APPROVED", "APPROVED", "REJECTED"})
  void exportsAllowedStatesWithCurrentOptionalDataAndNoInventedSubmissionDate(
      final EnrollmentApplicationStatus status) {
    when(applications.findForPdf(institutionId, applicationId))
        .thenReturn(Optional.of(application(status)));
    when(history.list(applicationId)).thenReturn(List.of());
    when(settings.get(institutionId)).thenReturn(EnrollmentPdfSettingsResponse.defaults());
    final var result = service.extract(institutionId, applicationId, null);
    assertThat(result.generatedAt()).isEqualTo(now);
    assertThat(result.submittedAt()).isNull();
    assertThat(result.selections()).isEmpty();
    assertThat(
            result.sections().stream()
                .flatMap(section -> section.fields().stream())
                .map(field -> field.value()))
        .contains("No", "Sin informar", "Muñoz, María");
    verify(guard).require(any(), eq(institutionId), any(), eq(applicationId));
  }

  @ParameterizedTest
  @EnumSource(
      value = EnrollmentApplicationStatus.class,
      names = {"DRAFT", "CANCELLED"})
  void rejectsInvalidStatesBeforeReadingCourseData(final EnrollmentApplicationStatus status) {
    when(applications.findForPdf(institutionId, applicationId))
        .thenReturn(Optional.of(application(status)));
    assertThatThrownBy(() -> service.extract(institutionId, applicationId, null))
        .isInstanceOf(InvalidEnrollmentApplicationStateException.class);
    verifyNoInteractions(courses, history, settings);
  }

  @Test
  void selfServiceRequiresExactSubjectAndCurrentCapability() {
    when(applications.findForPdf(institutionId, applicationId))
        .thenReturn(Optional.of(application(EnrollmentApplicationStatus.SUBMITTED)));
    assertThatThrownBy(() -> service.extract(institutionId, applicationId, UUID.randomUUID()))
        .isInstanceOf(EnrollmentApplicationNotFoundException.class);
    doThrow(new AccessDeniedException("denied"))
        .when(capabilities)
        .requireOwnEnrollmentRead(institutionId, personId);
    assertThatThrownBy(() -> service.extract(institutionId, applicationId, personId))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(courses, history, settings);
  }

  @Test
  void usesSubmissionHistoryRatherThanUpdatedAt() {
    when(applications.findForPdf(institutionId, applicationId))
        .thenReturn(Optional.of(application(EnrollmentApplicationStatus.APPROVED)));
    when(settings.get(institutionId)).thenReturn(EnrollmentPdfSettingsResponse.defaults());
    final var submitted = now.minusSeconds(3600);
    when(history.list(applicationId))
        .thenReturn(
            List.of(
                new EnrollmentAdmissionHistoryResponse(
                    UUID.randomUUID(), "SUBMITTED", submitted, null, "INSTITUTION")));
    assertThat(service.extract(institutionId, applicationId, null).submittedAt())
        .isEqualTo(submitted);
  }

  @Test
  void previewUsesFictitiousDataAndDoesNotReadApplicationsOrSaveConfiguration() {
    when(institutions.findWithLocationById(institutionId)).thenReturn(Optional.of(institution));
    final var result =
        service.example(
            institutionId,
            new EnrollmentPdfSettingsRequest("Ejemplo", null, null, null, true, true));
    assertThat(
            result.sections().stream()
                .flatMap(section -> section.fields().stream())
                .map(field -> field.value()))
        .contains("Persona de ejemplo", "Dato ficticio", "Responsable de ejemplo");
    assertThat(result.settings().title()).isEqualTo("Ejemplo");
    assertThat(result.coursesSelected()).isFalse();
    assertThat(result.selections())
        .extracting(selection -> selection.level())
        .containsExactly("Nivel 1");
    verifyNoInteractions(applications, capabilities, courses, history, settings);
  }

  @Test
  void formPreviewUsesCurrentBrandingAndFictitiousDataWithoutReadingApplications() {
    when(institutions.findWithLocationById(institutionId)).thenReturn(Optional.of(institution));
    when(settings.get(institutionId)).thenReturn(EnrollmentPdfSettingsResponse.defaults());

    final var result = service.formExample(institutionId, EnrollmentFormBaseDefinition.create());

    assertThat(
            result.sections().stream()
                .flatMap(section -> section.fields().stream())
                .map(field -> field.value()))
        .contains("Persona de ejemplo", "Dato ficticio", "Responsable de ejemplo");
    verifyNoInteractions(applications, capabilities, courses, history);
    verify(settings).get(institutionId);
  }

  @Test
  void selectionsRequireTheIndependentCoursePermission() {
    final var application = application(EnrollmentApplicationStatus.SUBMITTED);
    final var space = mock(ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlanSpace.class);
    final var academic =
        mock(ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicSpace.class);
    final var plan = mock(ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan.class);
    when(space.getAcademicSpace()).thenReturn(academic);
    when(academic.getName()).thenReturn("Selección protegida");
    when(space.getStudyPlan()).thenReturn(plan);
    when(plan.getName()).thenReturn("Plan");
    application.addSelectedSpace(
        EnrollmentApplicationSpace.builder().studyPlanSpace(space).build());
    when(applications.findForPdf(institutionId, applicationId))
        .thenReturn(Optional.of(application));
    when(history.list(applicationId)).thenReturn(List.of());
    when(settings.get(institutionId)).thenReturn(EnrollmentPdfSettingsResponse.defaults());

    assertThat(service.extract(institutionId, applicationId, null).selections()).isEmpty();
    when(courses.canRead(application)).thenReturn(true);
    assertThat(service.extract(institutionId, applicationId, null).selections())
        .extracting(selection -> selection.name())
        .containsExactly("Selección protegida");
  }

  @Test
  void schoolingOnlyIncludesApplicableFieldsWithoutLosingRequiredMissingData() {
    final var application = application(EnrollmentApplicationStatus.SUBMITTED);
    application.setEducationBackground(
        ApplicantEducationBackground.builder()
            .currentlyStudying(true)
            .educationLevel(
                ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EducationLevel.PRIMARY)
            .build());
    when(applications.findForPdf(institutionId, applicationId))
        .thenReturn(Optional.of(application));
    when(history.list(applicationId)).thenReturn(List.of());
    when(settings.get(institutionId)).thenReturn(EnrollmentPdfSettingsResponse.defaults());
    final var schooling =
        service.extract(institutionId, applicationId, null).sections().stream()
            .filter(section -> section.title().equals("Escolaridad"))
            .findFirst()
            .orElseThrow();
    assertThat(schooling.fields())
        .extracting(field -> field.label())
        .contains(
            "Asiste actualmente", "Nivel actual", "Institución educativa", "Sala, grado o año")
        .doesNotContain(
            "Secundario completo",
            "Título secundario obtenido",
            "Escuela secundaria",
            "Nivel completado");
    assertThat(schooling.fields())
        .filteredOn(field -> field.label().equals("Sala, grado o año"))
        .extracting(field -> field.value())
        .containsExactly("Sin informar");
  }
}
