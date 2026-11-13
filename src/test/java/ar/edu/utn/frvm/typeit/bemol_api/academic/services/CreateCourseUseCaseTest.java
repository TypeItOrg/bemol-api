package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicYearRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanSpaceRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseClassResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateCourseRequest;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateCourseUseCaseTest {
  @Mock private AuditEventRecorder auditRecorder;
  @org.mockito.Mock private AcademicAccessGuard accessGuard;

  private static final UUID INSTITUTION_ID =
      UUID.fromString("66666666-6666-6666-6666-666666666666");
  private static final UUID PLAN_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
  private static final UUID SPACE_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
  private static final UUID YEAR_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
  private static final UUID STUDY_PLAN_SPACE_ID =
      UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

  @Mock private InstitutionRepository institutionRepository;
  @Mock private AcademicYearRepository academicYearRepository;
  @Mock private StudyPlanSpaceRepository studyPlanSpaceRepository;
  @Mock private CourseRepository courseRepository;
  @Mock private CourseClassAssembler courseClassAssembler;
  @Mock private CourseTreeReader courseTreeReader;
  @InjectMocks private CreateCourseUseCase useCase;

  private final Institution institution = Institution.builder().id(INSTITUTION_ID).build();

  private void stubInstitution() {
    given(institutionRepository.findByIdForUpdate(INSTITUTION_ID))
        .willReturn(Optional.of(institution));
  }

  private void stubYear() {
    final var year = mock(AcademicYear.class);
    given(year.getId()).willReturn(YEAR_ID);
    given(year.getYear()).willReturn(2027);
    given(year.getStatus()).willReturn(AcademicYearStatus.ACTIVE);
    given(academicYearRepository.findByIdAndInstitution_IdForUpdate(YEAR_ID, INSTITUTION_ID))
        .willReturn(Optional.of(year));
  }

  private void stubStudyPlanSpace() {
    stubStudyPlanSpace(StudyPlanStatus.ACTIVE);
  }

  private void stubStudyPlanSpace(final StudyPlanStatus status) {
    final var plan = mock(StudyPlan.class);
    given(plan.getId()).willReturn(PLAN_ID);
    given(plan.getName()).willReturn("Plan");
    given(plan.getStatus()).willReturn(status);
    final var path = mock(TrainingPath.class);
    given(path.getId()).willReturn(UUID.randomUUID());
    given(path.getName()).willReturn("Trayecto");
    given(plan.getTrainingPath()).willReturn(path);
    final var space = mock(AcademicSpace.class);
    given(space.getId()).willReturn(SPACE_ID);
    given(space.getName()).willReturn("Espacio");
    given(space.getType()).willReturn(AcademicSpaceType.SUBJECT);
    given(space.getFormat()).willReturn(AcademicSpaceFormat.INDIVIDUAL);
    final var studyPlanSpace = mock(StudyPlanSpace.class);
    given(studyPlanSpace.getId()).willReturn(STUDY_PLAN_SPACE_ID);
    given(studyPlanSpace.getStudyPlan()).willReturn(plan);
    given(studyPlanSpace.getAcademicSpace()).willReturn(space);
    given(
            studyPlanSpaceRepository.findDetailsByIdAndInstitutionIdForUpdate(
                STUDY_PLAN_SPACE_ID, INSTITUTION_ID))
        .willReturn(Optional.of(studyPlanSpace));
  }

  private CreateCourseRequest request() {
    return new CreateCourseRequest(STUDY_PLAN_SPACE_ID, null, YEAR_ID, List.of());
  }

  @Test
  @DisplayName("Should create a course for an active plan containing the space")
  void createsCourseForActivePlan() {
    stubInstitution();
    stubStudyPlanSpace();
    stubYear();
    given(courseRepository.save(any(Course.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    final var expectedClasses =
        List.of(new CourseClassResponse(UUID.randomUUID(), 1, List.of(), List.of()));
    given(courseTreeReader.readWithAudit(any()))
        .willAnswer(
            invocation -> {
              final Course course = invocation.getArgument(0);
              return new CourseTreeReader.CourseTree(
                  expectedClasses, course.auditSnapshot(), List.of());
            });

    final var response = useCase.execute(INSTITUTION_ID, request());

    assertThat(response.studyPlanId()).isEqualTo(PLAN_ID);
    assertThat(response.academicSpaceFormat()).isEqualTo("INDIVIDUAL");
    assertThat(response.classes()).isEqualTo(expectedClasses);
    verify(courseRepository).save(any(Course.class));
    verify(courseClassAssembler).assemble(any(), any(), any(), any());
  }

  @Test
  @DisplayName("Should reject creating a course when the plan is not active")
  void rejectsInactivePlan() {
    stubInstitution();
    stubStudyPlanSpace(StudyPlanStatus.DRAFT);

    assertThatThrownBy(() -> useCase.execute(INSTITUTION_ID, request()))
        .isInstanceOf(AcademicConflictException.class);
    verify(courseRepository, never()).save(any());
  }

  @Test
  @DisplayName("Should reject a course whose plan placement does not exist")
  void rejectsUnknownStudyPlanSpace() {
    stubInstitution();
    given(
            studyPlanSpaceRepository.findDetailsByIdAndInstitutionIdForUpdate(
                STUDY_PLAN_SPACE_ID, INSTITUTION_ID))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(INSTITUTION_ID, request()))
        .isInstanceOf(AcademicConflictException.class);
    verify(courseRepository, never()).save(any());
  }

  @Test
  @DisplayName("Should reject when the institution does not exist")
  void rejectsUnknownInstitution() {
    given(institutionRepository.findByIdForUpdate(INSTITUTION_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(INSTITUTION_ID, request()))
        .isInstanceOf(InstitutionNotFoundException.class);
  }
}
