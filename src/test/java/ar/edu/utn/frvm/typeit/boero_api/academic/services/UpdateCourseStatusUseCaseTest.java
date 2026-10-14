package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicEntityTestFactory;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicYearFixtures;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.StudyPlanStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.InvalidAcademicStateException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.AcademicYearRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.StudyPlanRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CourseStatusRequest;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.CourseClosureService;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.EnrollmentInstitutionLock;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateCourseStatusUseCaseTest {
  private static final UUID INSTITUTION_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID COURSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID YEAR_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

  @Mock private CourseRepository courseRepository;
  @Mock private AcademicYearRepository academicYearRepository;
  @Mock private StudyPlanRepository studyPlanRepository;
  @Mock private CourseRepository.CourseAcademicContext context;
  @Mock private AcademicAccessGuard accessGuard;
  @Mock private CourseClosureService closureService;
  @Mock private EnrollmentInstitutionLock institutionLock;
  @InjectMocks private UpdateCourseStatusUseCase useCase;

  private AcademicYear year;
  private StudyPlan plan;
  private Course course;

  @BeforeEach
  void setUp() {
    final var institution = Institution.builder().id(INSTITUTION_ID).build();
    year = AcademicYearFixtures.planned(YEAR_ID, institution, 2026);
    plan = AcademicEntityTestFactory.studyPlan(institution);
    course =
        Course.create(institution, AcademicEntityTestFactory.studyPlanSpace(plan, COURSE_ID), year);
    course.deactivate();
    given(courseRepository.findAcademicContextByIdAndInstitution_Id(COURSE_ID, INSTITUTION_ID))
        .willReturn(Optional.of(context));
    given(context.getAcademicYearId()).willReturn(YEAR_ID);
    given(context.getStudyPlanId()).willReturn(plan.getId());
    given(studyPlanRepository.findByIdAndInstitution_IdForUpdate(plan.getId(), INSTITUTION_ID))
        .willReturn(Optional.of(plan));
    given(academicYearRepository.findByIdAndInstitution_IdForUpdate(YEAR_ID, INSTITUTION_ID))
        .willReturn(Optional.of(year));
    given(courseRepository.findByIdAndInstitution_IdForUpdate(COURSE_ID, INSTITUTION_ID))
        .willReturn(Optional.of(course));
  }

  static Stream<Arguments> invalidParents() {
    return Stream.of(
        Arguments.of(
            StudyPlanStatus.ACTIVE,
            AcademicYearStatus.PLANNED,
            AcademicConflictException.class,
            AcademicMessages.COURSE_YEAR_NOT_ACTIVE),
        Arguments.of(
            StudyPlanStatus.ACTIVE,
            AcademicYearStatus.CLOSED,
            AcademicConflictException.class,
            AcademicMessages.COURSE_YEAR_NOT_ACTIVE),
        Arguments.of(
            StudyPlanStatus.DRAFT,
            AcademicYearStatus.ACTIVE,
            AcademicConflictException.class,
            AcademicMessages.COURSE_STUDY_PLAN_NOT_ACTIVE),
        Arguments.of(
            StudyPlanStatus.INACTIVE,
            AcademicYearStatus.ACTIVE,
            InvalidAcademicStateException.class,
            AcademicMessages.INVALID_STATE));
  }

  @ParameterizedTest
  @MethodSource("invalidParents")
  void rejectsActivationForTheSpecifiedParentRule(
      StudyPlanStatus planStatus,
      AcademicYearStatus yearStatus,
      Class<? extends RuntimeException> failure,
      String message) {
    transitionParents(planStatus, yearStatus);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    INSTITUTION_ID, COURSE_ID, new CourseStatusRequest(CourseStatus.ACTIVE)))
        .isExactlyInstanceOf(failure)
        .hasMessage(message);

    assertThat(course.getStatus()).isEqualTo(CourseStatus.INACTIVE);
    assertThat(plan.getStatus()).isEqualTo(planStatus);
    assertThat(year.getStatus()).isEqualTo(yearStatus);
    verify(courseRepository, never()).flush();
    verifyNoInteractions(closureService);
  }

  @Test
  void activatesTheRealCourseWithActiveParents() {
    transitionParents(StudyPlanStatus.ACTIVE, AcademicYearStatus.ACTIVE);

    useCase.execute(INSTITUTION_ID, COURSE_ID, new CourseStatusRequest(CourseStatus.ACTIVE));

    assertThat(course.getStatus()).isEqualTo(CourseStatus.ACTIVE);
    verify(courseRepository).flush();
    verifyNoInteractions(closureService);
  }

  @Test
  void closesTheCourseAndReleasesItsEnrollments() {
    transitionParents(StudyPlanStatus.ACTIVE, AcademicYearStatus.ACTIVE);

    useCase.execute(INSTITUTION_ID, COURSE_ID, new CourseStatusRequest(CourseStatus.CLOSED));

    assertThat(course.getStatus()).isEqualTo(CourseStatus.CLOSED);
    verify(courseRepository).flush();
    verify(closureService).close(INSTITUTION_ID, COURSE_ID, null);
  }

  private void transitionParents(StudyPlanStatus planStatus, AcademicYearStatus yearStatus) {
    if (planStatus != StudyPlanStatus.DRAFT) {
      plan.activate();
      if (planStatus == StudyPlanStatus.INACTIVE) {
        plan.deactivate(LocalDate.of(2026, 10, 1));
      }
    }
    if (yearStatus != AcademicYearStatus.PLANNED) {
      year.transitionTo(AcademicYearStatus.ACTIVE);
      if (yearStatus == AcademicYearStatus.CLOSED) {
        year.transitionTo(AcademicYearStatus.CLOSED);
      }
    }
  }
}
