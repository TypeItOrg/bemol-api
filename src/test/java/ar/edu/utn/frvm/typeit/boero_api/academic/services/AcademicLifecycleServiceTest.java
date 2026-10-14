package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicLifecycleEvent;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicSpace;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Shift;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicLifecycleAction;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicLifecycleResource;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.ApprovalMode;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.RequirementType;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.AcademicLifecycleEventRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.AcademicSpaceRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.AcademicYearRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.InstrumentRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.ShiftRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.StudyPlanRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.AcademicLifecycleRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.AcademicYearStatusRequest;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.AccountType;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.CourseClosureService;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.EnrollmentInstitutionLock;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AcademicLifecycleServiceTest {
  @org.mockito.Mock private AcademicAccessGuard accessGuard;
  @Spy private Clock clock = Clock.systemUTC();

  @Mock private AcademicYearRepository academicYearRepository;
  @Mock private TrainingPathRepository trainingPathRepository;
  @Mock private StudyPlanRepository studyPlanRepository;
  @Mock private AcademicSpaceRepository academicSpaceRepository;
  @Mock private InstrumentRepository instrumentRepository;
  @Mock private ShiftRepository shiftRepository;
  @Mock private CourseRepository courseRepository;
  @Mock private AcademicLifecycleEventRepository eventRepository;
  @Mock private AcademicLifecycleActorResolver actorResolver;
  @Mock private CourseEnrollmentRepository courseEnrollmentRepository;
  private AcademicLifecycleService service;

  @BeforeEach
  void setUp() {
    final var journal = new AcademicLifecycleJournal(clock, eventRepository, actorResolver);
    service =
        new AcademicLifecycleService(
            new AcademicCatalogLifecycleUseCase(
                clock,
                academicYearRepository,
                academicSpaceRepository,
                instrumentRepository,
                shiftRepository,
                journal),
            new AcademicCurriculumLifecycleUseCase(
                accessGuard,
                clock,
                academicYearRepository,
                trainingPathRepository,
                studyPlanRepository,
                courseRepository,
                courseEnrollmentRepository,
                journal),
            journal);
  }

  @Test
  void deletesAnInactiveTrainingPathAndRecordsItsActorAndReason() {
    final var institutionId = UUID.randomUUID();
    final var resourceId = UUID.randomUUID();
    final var actorId = UUID.randomUUID();
    final var institution = Institution.builder().id(institutionId).build();
    final var path = TrainingPath.create(institution, "Tecnicatura", null);
    path.updateStatus(false);
    given(trainingPathRepository.findByIdAndInstitution_IdForLifecycle(resourceId, institutionId))
        .willReturn(Optional.of(path));
    given(actorResolver.resolve())
        .willReturn(new AcademicLifecycleActor(AccountType.INSTITUTION, actorId));

    service.deleteTrainingPath(
        institutionId, resourceId, new AcademicLifecycleRequest("  Baja solicitada  "));

    assertThat(path.isDeleted()).isTrue();
    final var eventCaptor = ArgumentCaptor.forClass(AcademicLifecycleEvent.class);
    verify(eventRepository).save(eventCaptor.capture());
    verify(eventRepository).flush();
    assertThat(eventCaptor.getValue())
        .extracting(
            mappedAcademicLifecycleEvent -> mappedAcademicLifecycleEvent.getResourceType(),
            mappedAcademicLifecycleEvent -> mappedAcademicLifecycleEvent.getResourceId(),
            mappedAcademicLifecycleEvent -> mappedAcademicLifecycleEvent.getAction(),
            mappedAcademicLifecycleEvent -> mappedAcademicLifecycleEvent.getActorType(),
            mappedAcademicLifecycleEvent -> mappedAcademicLifecycleEvent.getActorId(),
            mappedAcademicLifecycleEvent -> mappedAcademicLifecycleEvent.getReason())
        .containsExactly(
            AcademicLifecycleResource.TRAINING_PATH,
            resourceId,
            AcademicLifecycleAction.DELETE,
            AccountType.INSTITUTION,
            actorId,
            "Baja solicitada");
  }

  @Test
  void rejectsDeletingATrainingPathWithCurrentPlansWithoutRecordingAnEvent() {
    final var institutionId = UUID.randomUUID();
    final var resourceId = UUID.randomUUID();
    final var institution = Institution.builder().id(institutionId).build();
    final var path = TrainingPath.create(institution, "Tecnicatura", null);
    path.updateStatus(false);
    given(trainingPathRepository.findByIdAndInstitution_IdForLifecycle(resourceId, institutionId))
        .willReturn(Optional.of(path));
    given(trainingPathRepository.existsCurrentStudyPlan(resourceId)).willReturn(true);

    assertThatThrownBy(() -> service.deleteTrainingPath(institutionId, resourceId, null))
        .isInstanceOf(AcademicConflictException.class);

    assertThat(path.isDeleted()).isFalse();
    verifyNoInteractions(eventRepository, actorResolver);
  }

  @Test
  void deletesAnInactiveShiftAndRecordsItsActorAndReason() {
    final var institutionId = UUID.randomUUID();
    final var resourceId = UUID.randomUUID();
    final var actorId = UUID.randomUUID();
    final var institution = Institution.builder().id(institutionId).build();
    final var shift = Shift.create(institution, "Turno mañana", null);
    shift.updateStatus(false);
    given(shiftRepository.findByIdAndInstitution_IdForLifecycle(resourceId, institutionId))
        .willReturn(Optional.of(shift));
    given(actorResolver.resolve())
        .willReturn(new AcademicLifecycleActor(AccountType.INSTITUTION, actorId));

    service.deleteShift(
        institutionId, resourceId, new AcademicLifecycleRequest("  Baja solicitada  "));

    assertThat(shift.isDeleted()).isTrue();
    final var eventCaptor = ArgumentCaptor.forClass(AcademicLifecycleEvent.class);
    verify(eventRepository).save(eventCaptor.capture());
    verify(eventRepository).flush();
    assertThat(eventCaptor.getValue())
        .extracting(
            mappedAcademicLifecycleEvent -> mappedAcademicLifecycleEvent.getResourceType(),
            mappedAcademicLifecycleEvent -> mappedAcademicLifecycleEvent.getResourceId(),
            mappedAcademicLifecycleEvent -> mappedAcademicLifecycleEvent.getAction(),
            mappedAcademicLifecycleEvent -> mappedAcademicLifecycleEvent.getActorType(),
            mappedAcademicLifecycleEvent -> mappedAcademicLifecycleEvent.getActorId(),
            mappedAcademicLifecycleEvent -> mappedAcademicLifecycleEvent.getReason())
        .containsExactly(
            AcademicLifecycleResource.SHIFT,
            resourceId,
            AcademicLifecycleAction.DELETE,
            AccountType.INSTITUTION,
            actorId,
            "Baja solicitada");
  }

  @Test
  void restoresADeletedShiftAndRecordsTheRestoreAction() {
    final var institutionId = UUID.randomUUID();
    final var resourceId = UUID.randomUUID();
    final var actorId = UUID.randomUUID();
    final var institution = Institution.builder().id(institutionId).build();
    final var shift = Shift.create(institution, "Turno mañana", null);
    shift.updateStatus(false);
    shift.delete(java.time.Instant.now());
    given(shiftRepository.findByIdAndInstitution_IdForLifecycle(resourceId, institutionId))
        .willReturn(Optional.of(shift));
    given(actorResolver.resolve())
        .willReturn(new AcademicLifecycleActor(AccountType.PLATFORM, actorId));

    service.restoreShift(institutionId, resourceId, null);

    assertThat(shift.isDeleted()).isFalse();
    final var eventCaptor = ArgumentCaptor.forClass(AcademicLifecycleEvent.class);
    verify(eventRepository).save(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getResourceType()).isEqualTo(AcademicLifecycleResource.SHIFT);
    assertThat(eventCaptor.getValue().getAction()).isEqualTo(AcademicLifecycleAction.RESTORE);
    assertThat(eventCaptor.getValue().getActorType()).isEqualTo(AccountType.PLATFORM);
  }

  @Test
  void rejectsRestoringDeletedCourseAfterDeleteAndAcademicYearClosure() {
    final var institutionId = UUID.randomUUID();
    final var courseId = UUID.randomUUID();
    final var academicYearId = UUID.randomUUID();
    final var studyPlanId = UUID.randomUUID();
    final var institution = Institution.builder().id(institutionId).build();
    final var academicYear =
        Mockito.spy(
            AcademicYear.create(
                institution,
                2027,
                LocalDate.of(2027, 3, 1),
                LocalDate.of(2027, 12, 15),
                LocalDate.of(2026, 10, 7)));
    Mockito.doReturn(academicYearId).when(academicYear).getId();
    academicYear.transitionTo(AcademicYearStatus.ACTIVE);
    final var trainingPath = TrainingPath.create(institution, "Tecnicatura", null);
    final var studyPlan =
        StudyPlan.create(
            institution,
            trainingPath,
            "Plan 2027",
            LocalDate.of(2027, 3, 1),
            LocalDate.of(2027, 12, 31));
    studyPlan.activate();
    final var academicSpace =
        AcademicSpace.create(
            institution,
            "Programación",
            null,
            AcademicSpaceType.SUBJECT,
            AcademicSpaceFormat.INDIVIDUAL);
    final var course =
        Course.create(
            institution,
            StudyPlanSpace.create(
                institution,
                studyPlan,
                academicSpace,
                null,
                RequirementType.REQUIRED,
                1,
                ApprovalMode.FINAL_EXAM),
            academicYear);
    course.deactivate();
    final var lifecycleRequest = new AcademicLifecycleRequest("Prueba de ciclo");
    final var context = mock(CourseRepository.CourseAcademicContext.class);
    given(context.getAcademicYearId()).willReturn(academicYearId);
    given(context.getStudyPlanId()).willReturn(studyPlanId);
    given(courseRepository.findAcademicContextByIdAndInstitution_Id(courseId, institutionId))
        .willReturn(Optional.of(context));
    given(studyPlanRepository.findByIdAndInstitution_IdForLifecycle(studyPlanId, institutionId))
        .willReturn(Optional.of(studyPlan));
    given(academicYearRepository.findByIdAndInstitution_IdForUpdate(academicYearId, institutionId))
        .willReturn(Optional.of(academicYear));
    given(courseRepository.findByIdAndInstitution_IdForLifecycle(courseId, institutionId))
        .willReturn(Optional.of(course));
    given(actorResolver.resolve())
        .willReturn(new AcademicLifecycleActor(AccountType.INSTITUTION, UUID.randomUUID()));

    service.deleteCourse(institutionId, courseId, lifecycleRequest);

    given(
            courseRepository.findByAcademicYear_IdAndInstitution_IdAndDeletedAtIsNull(
                academicYearId, institutionId))
        .willReturn(List.of());

    new UpdateAcademicYearStatusUseCase(
            academicYearRepository,
            courseRepository,
            Mockito.mock(CourseClosureService.class),
            Mockito.mock(EnrollmentInstitutionLock.class))
        .execute(
            institutionId,
            academicYearId,
            new AcademicYearStatusRequest(AcademicYearStatus.CLOSED));

    assertThatThrownBy(() -> service.restoreCourse(institutionId, courseId, null))
        .isInstanceOf(AcademicConflictException.class);

    assertThat(course.isDeleted()).isTrue();
    assertThat(course.getStatus()).isEqualTo(CourseStatus.INACTIVE);
  }

  @Test
  void rejectsRestoringDeletedCourseWhenStudyPlanIsInactive() {
    final var institutionId = UUID.randomUUID();
    final var courseId = UUID.randomUUID();
    final var academicYearId = UUID.randomUUID();
    final var studyPlanId = UUID.randomUUID();
    final var institution = Institution.builder().id(institutionId).build();
    final var academicYear =
        AcademicYear.create(
            institution,
            2027,
            LocalDate.of(2027, 3, 1),
            LocalDate.of(2027, 12, 15),
            LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")));
    academicYear.transitionTo(AcademicYearStatus.ACTIVE);
    final var trainingPath = TrainingPath.create(institution, "Tecnicatura", null);
    final var studyPlan =
        StudyPlan.create(
            institution,
            trainingPath,
            "Plan 2027",
            LocalDate.of(2027, 3, 1),
            LocalDate.of(2027, 12, 31));
    studyPlan.activate();
    studyPlan.deactivate(LocalDate.of(2027, 12, 31));
    final var academicSpace =
        AcademicSpace.create(
            institution,
            "Programación",
            null,
            AcademicSpaceType.SUBJECT,
            AcademicSpaceFormat.INDIVIDUAL);
    final var course =
        Course.create(
            institution,
            StudyPlanSpace.create(
                institution,
                studyPlan,
                academicSpace,
                null,
                RequirementType.REQUIRED,
                1,
                ApprovalMode.FINAL_EXAM),
            academicYear);
    course.deactivate();
    course.delete(Instant.now());
    final var context = mock(CourseRepository.CourseAcademicContext.class);
    given(context.getAcademicYearId()).willReturn(academicYearId);
    given(context.getStudyPlanId()).willReturn(studyPlanId);
    given(courseRepository.findAcademicContextByIdAndInstitution_Id(courseId, institutionId))
        .willReturn(Optional.of(context));
    given(studyPlanRepository.findByIdAndInstitution_IdForLifecycle(studyPlanId, institutionId))
        .willReturn(Optional.of(studyPlan));
    given(academicYearRepository.findByIdAndInstitution_IdForUpdate(academicYearId, institutionId))
        .willReturn(Optional.of(academicYear));
    given(courseRepository.findByIdAndInstitution_IdForLifecycle(courseId, institutionId))
        .willReturn(Optional.of(course));

    assertThatThrownBy(() -> service.restoreCourse(institutionId, courseId, null))
        .isInstanceOf(AcademicConflictException.class);

    assertThat(course.isDeleted()).isTrue();
  }
}
