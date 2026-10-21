package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Instrument;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlanSpaceInstrument;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.TrainingPathNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanSpaceInstrumentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanSpaceRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPeriod;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPeriodOffering;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.AcademicSpaceSelectionDto;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CareerSelectionDto;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentDraftData;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.InstrumentSelectionDto;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnrollmentDraftDataValidatorTest {

  @Mock private TrainingPathRepository trainingPathRepository;
  @Mock private StudyPlanSpaceRepository studyPlanSpaceRepository;
  @Mock private StudyPlanSpaceInstrumentRepository studyPlanSpaceInstrumentRepository;
  @Mock private EnrollmentEffectiveStudyPlanResolver enrollmentEffectiveStudyPlanResolver;

  private EnrollmentDraftDataValidator validator;

  private final UUID institutionId = UUID.randomUUID();
  private final UUID studyPlanId = UUID.randomUUID();

  private EnrollmentApplication application;
  private StudyPlan studyPlan;

  @BeforeEach
  void setUp() {
    validator =
        new EnrollmentDraftDataValidator(
            trainingPathRepository,
            studyPlanSpaceRepository,
            studyPlanSpaceInstrumentRepository,
            enrollmentEffectiveStudyPlanResolver);

    studyPlan = mock(StudyPlan.class);
    Mockito.lenient().when(studyPlan.getId()).thenReturn(studyPlanId);

    Institution institution = mock(Institution.class);
    AcademicYear academicYear = mock(AcademicYear.class);
    application =
        EnrollmentApplication.builder()
            .institution(institution)
            .studyPlan(studyPlan)
            .enrollmentPeriod(EnrollmentPeriod.builder().scopeConfigured(true).build())
            .academicYear(academicYear)
            .build();
  }

  @Test
  @DisplayName(
      "Should return the application's current study plan and skip validation when data is null")
  void validate_nullData_returnsCurrentStudyPlan() {
    StudyPlan result = validator.validate(institutionId, application, null);

    assertThat(result).isEqualTo(studyPlan);
    verifyNoValidationSideEffects();
  }

  @Test
  @DisplayName(
      "Should throw TrainingPathNotFoundException when the selected training path is not active for the institution")
  void validate_inactiveTrainingPath_throws() {
    UUID trainingPathId = UUID.randomUUID();
    when(trainingPathRepository.findByIdAndInstitution_IdAndActiveTrueAndDeletedAtIsNull(
            trainingPathId, institutionId))
        .thenReturn(Optional.empty());

    EnrollmentDraftData data =
        EnrollmentDraftData.builder()
            .careerSelection(new CareerSelectionDto(trainingPathId))
            .build();

    assertThatThrownBy(() -> validator.validate(institutionId, application, data))
        .isInstanceOf(TrainingPathNotFoundException.class);
  }

  @Test
  @DisplayName("Should reject a study plan space selection with duplicate ids")
  void validate_duplicateSpaceIds_throws() {
    UUID spaceId = UUID.randomUUID();
    when(enrollmentEffectiveStudyPlanResolver.resolveForTrainingPath(
            institutionId, application, null))
        .thenReturn(studyPlan);

    EnrollmentDraftData data =
        EnrollmentDraftData.builder()
            .academicSpaceSelection(new AcademicSpaceSelectionDto(List.of(spaceId, spaceId)))
            .build();

    assertThatThrownBy(() -> validator.validate(institutionId, application, data))
        .isInstanceOf(EnrollmentValidationException.class)
        .extracting(
            ex ->
                requireNonNull(((EnrollmentValidationException) ex).fieldErrors())
                    .containsKey("academicSpaceSelection.studyPlanSpaceIds"))
        .isEqualTo(true);
    verify(studyPlanSpaceRepository, never())
        .findEligibleByIdInAndStudyPlanId(any(), any(), anyList());
  }

  @Test
  @DisplayName("Should reject a study plan space that does not belong to the effective study plan")
  void validate_spaceNotEligibleForEffectivePlan_throws() {
    UUID spaceId = UUID.randomUUID();
    when(enrollmentEffectiveStudyPlanResolver.resolveForTrainingPath(
            institutionId, application, null))
        .thenReturn(studyPlan);
    when(studyPlanSpaceRepository.findEligibleByIdInAndStudyPlanId(
            institutionId, studyPlanId, List.of(spaceId)))
        .thenReturn(List.of());

    EnrollmentDraftData data =
        EnrollmentDraftData.builder()
            .academicSpaceSelection(new AcademicSpaceSelectionDto(List.of(spaceId)))
            .build();

    assertThatThrownBy(() -> validator.validate(institutionId, application, data))
        .isInstanceOf(EnrollmentValidationException.class)
        .extracting(
            ex ->
                requireNonNull(((EnrollmentValidationException) ex).fieldErrors())
                    .containsKey("academicSpaceSelection.studyPlanSpaceIds"))
        .isEqualTo(true);
  }

  @Test
  @DisplayName(
      "Should validate spaces against the plan resolved from the candidate training path, not the application's current plan")
  void validate_careerChange_validatesSpacesAgainstNewPlan() {
    UUID trainingPathId = UUID.randomUUID();
    UUID newStudyPlanId = UUID.randomUUID();
    UUID spaceId = UUID.randomUUID();
    StudyPlan newStudyPlan = mock(StudyPlan.class);
    when(newStudyPlan.getId()).thenReturn(newStudyPlanId);

    when(trainingPathRepository.findByIdAndInstitution_IdAndActiveTrueAndDeletedAtIsNull(
            trainingPathId, institutionId))
        .thenReturn(Optional.of(mock(TrainingPath.class)));
    when(enrollmentEffectiveStudyPlanResolver.resolveForTrainingPath(
            institutionId, application, trainingPathId))
        .thenReturn(newStudyPlan);
    when(studyPlanSpaceRepository.findEligibleByIdInAndStudyPlanId(
            institutionId, newStudyPlanId, List.of(spaceId)))
        .thenReturn(List.of(mock(StudyPlanSpace.class)));

    EnrollmentDraftData data =
        EnrollmentDraftData.builder()
            .careerSelection(new CareerSelectionDto(trainingPathId))
            .academicSpaceSelection(new AcademicSpaceSelectionDto(List.of(spaceId)))
            .build();

    configureOffering(newStudyPlan, spaceId);

    StudyPlan result = validator.validate(institutionId, application, data);

    assertThat(result).isEqualTo(newStudyPlan);
    verify(studyPlanSpaceRepository)
        .findEligibleByIdInAndStudyPlanId(institutionId, newStudyPlanId, List.of(spaceId));
  }

  @Test
  @DisplayName("Should reject an instrument selected for a study plan space that was not selected")
  void validate_instrumentForUnselectedSpace_throws() {
    UUID selectedSpaceId = UUID.randomUUID();
    UUID otherSpaceId = UUID.randomUUID();
    UUID instrumentId = UUID.randomUUID();
    when(enrollmentEffectiveStudyPlanResolver.resolveForTrainingPath(
            institutionId, application, null))
        .thenReturn(studyPlan);
    when(studyPlanSpaceRepository.findEligibleByIdInAndStudyPlanId(
            institutionId, studyPlanId, List.of(selectedSpaceId)))
        .thenReturn(List.of(mock(StudyPlanSpace.class)));

    EnrollmentDraftData data =
        EnrollmentDraftData.builder()
            .academicSpaceSelection(new AcademicSpaceSelectionDto(List.of(selectedSpaceId)))
            .instrumentSelection(new InstrumentSelectionDto(Map.of(otherSpaceId, instrumentId)))
            .build();

    assertThatThrownBy(() -> validator.validate(institutionId, application, data))
        .isInstanceOf(EnrollmentValidationException.class)
        .extracting(
            ex ->
                requireNonNull(((EnrollmentValidationException) ex).fieldErrors())
                    .containsKey("instrumentSelection.studyPlanSpaceInstrumentIds"))
        .isEqualTo(true);
  }

  @Test
  @DisplayName("Should reject an instrument that is not enabled for the selected study plan space")
  void validate_instrumentNotAllowedForSpace_throws() {
    UUID spaceId = UUID.randomUUID();
    UUID instrumentId = UUID.randomUUID();
    when(enrollmentEffectiveStudyPlanResolver.resolveForTrainingPath(
            institutionId, application, null))
        .thenReturn(studyPlan);
    when(studyPlanSpaceRepository.findEligibleByIdInAndStudyPlanId(
            institutionId, studyPlanId, List.of(spaceId)))
        .thenReturn(List.of(mock(StudyPlanSpace.class)));
    when(studyPlanSpaceInstrumentRepository.findActiveByStudyPlanSpaceIds(
            eq(institutionId), anyList()))
        .thenReturn(List.of());

    EnrollmentDraftData data =
        EnrollmentDraftData.builder()
            .academicSpaceSelection(new AcademicSpaceSelectionDto(List.of(spaceId)))
            .instrumentSelection(new InstrumentSelectionDto(Map.of(spaceId, instrumentId)))
            .build();

    assertThatThrownBy(() -> validator.validate(institutionId, application, data))
        .isInstanceOf(EnrollmentValidationException.class)
        .extracting(
            ex ->
                requireNonNull(((EnrollmentValidationException) ex).fieldErrors())
                    .containsKey("instrumentSelection.studyPlanSpaceInstrumentIds"))
        .isEqualTo(true);
  }

  @Test
  @DisplayName("Should accept an instrument enabled for the selected study plan space")
  void validate_instrumentAllowedForSpace_succeeds() {
    UUID spaceId = UUID.randomUUID();
    UUID instrumentId = UUID.randomUUID();
    when(enrollmentEffectiveStudyPlanResolver.resolveForTrainingPath(
            institutionId, application, null))
        .thenReturn(studyPlan);
    when(studyPlanSpaceRepository.findEligibleByIdInAndStudyPlanId(
            institutionId, studyPlanId, List.of(spaceId)))
        .thenReturn(List.of(mock(StudyPlanSpace.class)));

    StudyPlanSpace relationSpace = mock(StudyPlanSpace.class);
    when(relationSpace.getId()).thenReturn(spaceId);
    Instrument relationInstrument = mock(Instrument.class);
    when(relationInstrument.getId()).thenReturn(instrumentId);
    StudyPlanSpaceInstrument relation =
        StudyPlanSpaceInstrument.create(mock(Institution.class), relationSpace, relationInstrument);
    when(studyPlanSpaceInstrumentRepository.findActiveByStudyPlanSpaceIds(
            eq(institutionId), anyList()))
        .thenReturn(List.of(relation));

    EnrollmentDraftData data =
        EnrollmentDraftData.builder()
            .academicSpaceSelection(new AcademicSpaceSelectionDto(List.of(spaceId)))
            .instrumentSelection(new InstrumentSelectionDto(Map.of(spaceId, instrumentId)))
            .build();

    configureOffering(studyPlan, spaceId);

    StudyPlan result = validator.validate(institutionId, application, data);

    assertThat(result).isEqualTo(studyPlan);
  }

  private void configureOffering(final StudyPlan plan, final UUID spaceId) {
    final var period = requireNonNull(application.getEnrollmentPeriod());
    final var offering = EnrollmentPeriodOffering.create(period, plan);
    offering.selectLevels(List.of(), true);
    period.getOfferings().add(offering);
    final var space = mock(StudyPlanSpace.class);
    when(space.getStudyPlan()).thenReturn(plan);
    when(studyPlanSpaceRepository.findAllById(java.util.Set.of(spaceId)))
        .thenReturn(List.of(space));
  }

  private void verifyNoValidationSideEffects() {
    verify(trainingPathRepository, never())
        .findByIdAndInstitution_IdAndActiveTrueAndDeletedAtIsNull(any(), any());
    verify(studyPlanSpaceRepository, never())
        .findEligibleByIdInAndStudyPlanId(any(), any(), anyList());
    verify(studyPlanSpaceInstrumentRepository, never())
        .findActiveByStudyPlanSpaceIds(any(), anyList());
  }
}
