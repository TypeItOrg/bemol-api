package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.CourseClass;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseClassRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseClassTeacherRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollmentGrade;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.InstitutionEnrollmentLock;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentGradeRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.InstitutionEnrollmentLockRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Student;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseEnrollmentGradeServiceTest {

  private static final UUID INSTITUTION_ID = UUID.randomUUID();
  private static final UUID ENROLLMENT_ID = UUID.randomUUID();
  private static final UUID CLASS_ID = UUID.randomUUID();
  private static final UUID PERSON_ID = UUID.randomUUID();

  @Mock private InstitutionRepository institutionRepository;
  @Mock private InstitutionEnrollmentLockRepository institutionEnrollmentLockRepository;
  @Mock private CourseEnrollmentRepository courseEnrollmentRepository;
  @Mock private CourseEnrollmentGradeRepository gradeRepository;
  @Mock private CourseClassRepository courseClassRepository;
  @Mock private CourseClassTeacherRepository courseClassTeacherRepository;
  @Mock private PersonRoleAssignmentRepository roleAssignments;
  @Mock private PersonRepository personRepository;

  @Mock
  private ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces
          .CourseEnrollmentGradeHistoryRepository
      historyRepository;

  private final Clock clock = Clock.fixed(Instant.parse("2026-10-04T00:00:00Z"), ZoneId.of("UTC"));

  private CourseEnrollmentGradeService service;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    final var store =
        new CourseEnrollmentGradeStore(
            institutionRepository,
            institutionEnrollmentLockRepository,
            courseEnrollmentRepository,
            gradeRepository,
            courseClassRepository,
            clock);
    final var mapper = new CourseEnrollmentGradeResponseMapper(personRepository);
    final var historySupport = new CourseEnrollmentGradeHistorySupport(personRepository);
    final var journal = new CourseEnrollmentGradeJournal(historyRepository, clock);
    final var queries =
        new CourseEnrollmentGradeQueries(
            store, gradeRepository, historyRepository, mapper, historySupport);
    final var commands = new CourseEnrollmentGradeCommands(store, gradeRepository, mapper, journal);
    final var publisher = new CourseEnrollmentGradePublish(store, gradeRepository, journal, clock);
    final var access =
        new CourseEnrollmentGradeAccess(roleAssignments, courseClassTeacherRepository, store);

    service = new CourseEnrollmentGradeService(queries, commands, publisher, access);
  }

  @Test
  @DisplayName("Create rejects enrollment not ENROLLED")
  void create_rejectsNonEnrolled() {
    final var enrollment = enrolledEnrollment(CourseEnrollmentStatus.WITHDRAWN);

    when(institutionRepository.findByIdForUpdate(INSTITUTION_ID))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(Institution.class)));
    when(institutionEnrollmentLockRepository.findByInstitutionId(INSTITUTION_ID))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(InstitutionEnrollmentLock.class)));
    when(courseEnrollmentRepository.findByIdAndInstitutionIdForUpdate(
            ENROLLMENT_ID, INSTITUTION_ID))
        .thenReturn(Optional.of(enrollment));

    assertThatThrownBy(
            () ->
                service.create(
                    INSTITUTION_ID, ENROLLMENT_ID, "Parcial 1", new BigDecimal("7"), PERSON_ID))
        .isInstanceOf(EnrollmentValidationException.class);
  }

  @Test
  @DisplayName("Create rejects duplicate evaluation")
  void create_rejectsDuplicate() {
    final var enrollment = enrolledEnrollment(CourseEnrollmentStatus.ENROLLED);

    when(institutionRepository.findByIdForUpdate(INSTITUTION_ID))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(Institution.class)));
    when(institutionEnrollmentLockRepository.findByInstitutionId(INSTITUTION_ID))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(InstitutionEnrollmentLock.class)));
    when(courseEnrollmentRepository.findByIdAndInstitutionIdForUpdate(
            ENROLLMENT_ID, INSTITUTION_ID))
        .thenReturn(Optional.of(enrollment));
    when(gradeRepository.existsDuplicateEvaluation(any(), any(), any())).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.create(
                    INSTITUTION_ID, ENROLLMENT_ID, "Parcial 1", new BigDecimal("7"), PERSON_ID))
        .isInstanceOf(EnrollmentValidationException.class);
  }

  @Test
  @DisplayName("Draft delete removes immediately")
  void delete_draftRemovesImmediately() {
    final var enrollment = enrolledEnrollment(CourseEnrollmentStatus.ENROLLED);
    final var gradeId = UUID.randomUUID();
    final var grade =
        CourseEnrollmentGrade.builder()
            .id(gradeId)
            .institution(org.mockito.Mockito.mock(Institution.class))
            .courseEnrollment(enrollment)
            .evaluation("Parcial 1")
            .value(new BigDecimal("7"))
            .pendingDeletion(false)
            .build();

    when(institutionRepository.findByIdForUpdate(INSTITUTION_ID))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(Institution.class)));
    when(institutionEnrollmentLockRepository.findByInstitutionId(INSTITUTION_ID))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(InstitutionEnrollmentLock.class)));
    when(courseEnrollmentRepository.findByIdAndInstitutionIdForUpdate(
            ENROLLMENT_ID, INSTITUTION_ID))
        .thenReturn(Optional.of(enrollment));
    when(gradeRepository.findByIdAndInstitutionIdForUpdate(gradeId, INSTITUTION_ID))
        .thenReturn(Optional.of(grade));

    final var result = service.delete(INSTITUTION_ID, ENROLLMENT_ID, gradeId, null, PERSON_ID);

    assertThat(result.deletedImmediately()).isTrue();
  }

  @Test
  @DisplayName("Update rejects stale version")
  void update_rejectsStaleVersion() {
    final var enrollment = enrolledEnrollment(CourseEnrollmentStatus.ENROLLED);
    final var gradeId = UUID.randomUUID();
    final var grade =
        CourseEnrollmentGrade.builder()
            .id(gradeId)
            .institution(org.mockito.Mockito.mock(Institution.class))
            .courseEnrollment(enrollment)
            .evaluation("Parcial 1")
            .value(new BigDecimal("7"))
            .pendingDeletion(false)
            .version(3L)
            .build();

    when(institutionRepository.findByIdForUpdate(INSTITUTION_ID))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(Institution.class)));
    when(institutionEnrollmentLockRepository.findByInstitutionId(INSTITUTION_ID))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(InstitutionEnrollmentLock.class)));
    when(courseEnrollmentRepository.findByIdAndInstitutionIdForUpdate(
            ENROLLMENT_ID, INSTITUTION_ID))
        .thenReturn(Optional.of(enrollment));
    when(gradeRepository.findByIdAndInstitutionIdForUpdate(gradeId, INSTITUTION_ID))
        .thenReturn(Optional.of(grade));

    assertThatThrownBy(
            () ->
                service.update(
                    INSTITUTION_ID,
                    ENROLLMENT_ID,
                    gradeId,
                    "Parcial 1",
                    new BigDecimal("8"),
                    2L,
                    PERSON_ID))
        .isInstanceOf(EnrollmentValidationException.class);
  }

  @Test
  @DisplayName("Teacher without assignment cannot manage class")
  void requireTeacher_rejectsUnassigned() {
    when(roleAssignments.existsByPerson_IdAndInstitution_IdAndRole_Code(
            PERSON_ID, INSTITUTION_ID, "TEACHER"))
        .thenReturn(true);
    when(courseClassTeacherRepository.existsByInstitution_IdAndPerson_IdAndCourseClass_Id(
            INSTITUTION_ID, PERSON_ID, CLASS_ID))
        .thenReturn(false);

    assertThatThrownBy(() -> service.requireTeacherForClass(INSTITUTION_ID, PERSON_ID, CLASS_ID))
        .isInstanceOf(EnrollmentValidationException.class);
  }

  @Test
  @DisplayName("Non-teacher cannot manage class")
  void requireTeacher_rejectsNonTeacher() {
    when(roleAssignments.existsByPerson_IdAndInstitution_IdAndRole_Code(
            PERSON_ID, INSTITUTION_ID, "TEACHER"))
        .thenReturn(false);

    assertThatThrownBy(() -> service.requireTeacherForClass(INSTITUTION_ID, PERSON_ID, CLASS_ID))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  @DisplayName("Student cannot list another enrollment")
  void listOwnPublished_rejectsOtherEnrollment() {
    final var enrollment = enrolledEnrollment(CourseEnrollmentStatus.COMPLETED);
    final var otherPerson = UUID.randomUUID();

    when(courseEnrollmentRepository.findByIdAndInstitutionId(ENROLLMENT_ID, INSTITUTION_ID))
        .thenReturn(Optional.of(enrollment));

    assertThatThrownBy(() -> service.listOwnPublished(INSTITUTION_ID, ENROLLMENT_ID, otherPerson))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  @DisplayName("Grade from another enrollment is rejected")
  void update_rejectsGradeEnrollmentMismatch() {
    final var enrollment = enrolledEnrollment(CourseEnrollmentStatus.ENROLLED);
    final var otherEnrollmentId = UUID.randomUUID();
    final var institution = org.mockito.Mockito.mock(Institution.class);
    final var student = org.mockito.Mockito.mock(Student.class);
    final var person = org.mockito.Mockito.mock(Person.class);
    final var course = org.mockito.Mockito.mock(Course.class);
    final var courseClass = org.mockito.Mockito.mock(CourseClass.class);
    lenient().when(student.getPerson()).thenReturn(person);
    lenient().when(person.getId()).thenReturn(PERSON_ID);
    lenient().when(courseClass.getId()).thenReturn(CLASS_ID);
    final var otherEnrollment =
        CourseEnrollment.builder()
            .id(otherEnrollmentId)
            .institution(institution)
            .student(student)
            .course(course)
            .courseClass(courseClass)
            .status(CourseEnrollmentStatus.ENROLLED)
            .build();
    final var gradeId = UUID.randomUUID();
    final var grade =
        CourseEnrollmentGrade.builder()
            .id(gradeId)
            .institution(org.mockito.Mockito.mock(Institution.class))
            .courseEnrollment(otherEnrollment)
            .evaluation("Parcial 1")
            .value(new BigDecimal("7"))
            .pendingDeletion(false)
            .version(0L)
            .build();

    when(institutionRepository.findByIdForUpdate(INSTITUTION_ID))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(Institution.class)));
    when(institutionEnrollmentLockRepository.findByInstitutionId(INSTITUTION_ID))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(InstitutionEnrollmentLock.class)));
    when(courseEnrollmentRepository.findByIdAndInstitutionIdForUpdate(
            ENROLLMENT_ID, INSTITUTION_ID))
        .thenReturn(Optional.of(enrollment));
    when(gradeRepository.findByIdAndInstitutionIdForUpdate(gradeId, INSTITUTION_ID))
        .thenReturn(Optional.of(grade));

    assertThatThrownBy(
            () ->
                service.update(
                    INSTITUTION_ID,
                    ENROLLMENT_ID,
                    gradeId,
                    "Parcial 1",
                    new BigDecimal("8"),
                    null,
                    PERSON_ID))
        .isInstanceOf(EnrollmentValidationException.class);
  }

  private CourseEnrollment enrolledEnrollment(final CourseEnrollmentStatus status) {
    final var institution = org.mockito.Mockito.mock(Institution.class);
    final var student = org.mockito.Mockito.mock(Student.class);
    final var person = org.mockito.Mockito.mock(Person.class);
    final var course = org.mockito.Mockito.mock(Course.class);
    final var courseClass = org.mockito.Mockito.mock(CourseClass.class);

    lenient().when(student.getPerson()).thenReturn(person);
    lenient().when(person.getId()).thenReturn(PERSON_ID);
    lenient().when(courseClass.getId()).thenReturn(CLASS_ID);

    return CourseEnrollment.builder()
        .id(ENROLLMENT_ID)
        .institution(institution)
        .student(student)
        .course(course)
        .courseClass(courseClass)
        .status(status)
        .build();
  }
}
