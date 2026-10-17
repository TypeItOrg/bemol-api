package ar.edu.utn.frvm.typeit.boero_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicSpace;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.CourseClass;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.CourseClassDay;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.CourseClassSchedule;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.ApprovalMode;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseDay;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.RequirementType;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.AcademicYearStatusRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CourseStatusRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.UpdateAcademicYearStatusUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.UpdateCourseStatusUseCase;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionRoleProvisioner;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollmentSchedule;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.AcademicEnrollmentStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentGradeHistoryOperation;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentGradePublicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentSource;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseWithdrawalType;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.GradeMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentGradeHistoryResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentGradeResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.UpdateAcademicEnrollmentStatusRequest;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.WithdrawCourseEnrollmentRequest;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.CourseEnrollmentGradeService;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.CourseEnrollmentService;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Student;
import ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class CourseEnrollmentGradeExitPostgresIntegrationTest extends DatabaseMigrationTestSupport {

  @Autowired EntityManager em;
  @Autowired PlatformTransactionManager transactions;
  @Autowired CourseEnrollmentGradeService grades;
  @Autowired CourseEnrollmentService enrollments;
  @Autowired UpdateCourseStatusUseCase closeCourse;
  @Autowired UpdateAcademicYearStatusUseCase closeYear;
  @Autowired InstitutionRoleProvisioner roles;

  @BeforeEach
  void authenticate() {
    authenticatePlatformAdministrator();
  }

  @Test
  void academicResultDiscardsDraftOnExitAndKeepsAuditTrail() {
    final var f = fixture();

    final var gradeId =
        tx(
            () ->
                grades
                    .create(
                        f.institutionId(),
                        f.enrollmentA(),
                        "Parcial 1",
                        new BigDecimal("7"),
                        f.teacher())
                    .id());

    tx(
        () -> {
          enrollments.updateAcademicStatus(
              f.institutionId(),
              f.enrollmentA(),
              new UpdateAcademicEnrollmentStatusRequest(
                  AcademicEnrollmentStatus.REGULARIZED,
                  "Resultado final",
                  enrollmentVersion(f, f.enrollmentA())),
              f.teacher());
          return null;
        });

    assertThat(tx(() -> grades.listForManagement(f.institutionId(), f.enrollmentA()))).isEmpty();
    assertThat(tx(() -> grades.listPublished(f.institutionId(), f.enrollmentA()))).isEmpty();

    final var archived = tx(() -> grades.archived(f.institutionId(), f.enrollmentA()));
    assertThat(archived).hasSize(1);
    assertThat(archived.get(0).gradeId()).isEqualTo(gradeId);
    assertThat(archived.get(0).deletionType())
        .isEqualTo(CourseEnrollmentGradeHistoryOperation.DISCARDED_DRAFT_ON_EXIT);
    assertThat(archived.get(0).lastEvaluation()).isEqualTo("Parcial 1");
    assertThat(archived.get(0).lastValue()).isEqualByComparingTo("7");

    final var page =
        tx(
            () ->
                grades.history(f.institutionId(), f.enrollmentA(), gradeId, PageRequest.of(0, 50)));

    assertThat(page.getContent().stream().map(event -> event.operation()).toList())
        .containsExactlyInAnyOrder(
            CourseEnrollmentGradeHistoryOperation.CREATED,
            CourseEnrollmentGradeHistoryOperation.DISCARDED_DRAFT_ON_EXIT);

    final var discard = page.getContent().get(0);
    assertThat(discard.operation())
        .isEqualTo(CourseEnrollmentGradeHistoryOperation.DISCARDED_DRAFT_ON_EXIT);
    assertThat(discard.evaluationBefore()).isEqualTo("Parcial 1");
    assertThat(discard.valueBefore()).isEqualByComparingTo("7");
    assertThat(discard.evaluationAfter()).isNull();
    assertThat(discard.previousStatus()).isEqualTo(CourseEnrollmentGradePublicationStatus.DRAFT);
    assertThat(discard.newStatus()).isNull();
    assertThat(discard.actor()).isNotNull();
    assertThat(discard.actor().id()).isEqualTo(f.teacher());
  }

  @Test
  void exitRestoresWorkingEditsToPublishedSnapshotWithAudit() {
    final var f = fixture();

    final var gradeId =
        tx(
            () -> {
              final var created =
                  grades.create(
                      f.institutionId(),
                      f.enrollmentA(),
                      "Parcial 1",
                      new BigDecimal("7"),
                      f.teacher());
              grades.publishClass(f.institutionId(), f.classId(), f.teacher());
              return created.id();
            });

    final var published = gradeOf(f, f.enrollmentA(), gradeId);

    updateGrade(f, f.enrollmentA(), gradeId, "Parcial 2", "9");

    assertThat(gradeOf(f, f.enrollmentA(), gradeId).publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.PENDING_CHANGES);

    completeEnrollment(f, f.enrollmentA());

    final var restored = gradeOf(f, f.enrollmentA(), gradeId);
    assertThat(restored.evaluation()).isEqualTo("Parcial 1");
    assertThat(restored.value()).isEqualByComparingTo("7");
    assertThat(restored.publishedEvaluation()).isEqualTo("Parcial 1");
    assertThat(restored.publishedValue()).isEqualByComparingTo("7");
    assertThat(restored.publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.PUBLISHED);
    assertThat(restored.publishedAt()).isEqualTo(published.publishedAt());
    assertThat(requireNonNull(restored.publishedBy()).id())
        .isEqualTo(requireNonNull(published.publishedBy()).id());

    final var events = historyOf(f, f.enrollmentA(), gradeId);
    final var reverted = events.getContent().get(0);
    assertThat(reverted.operation())
        .isEqualTo(CourseEnrollmentGradeHistoryOperation.REVERTED_TO_PUBLISHED_ON_EXIT);
    assertThat(reverted.evaluationBefore()).isEqualTo("Parcial 2");
    assertThat(reverted.valueBefore()).isEqualByComparingTo("9");
    assertThat(reverted.evaluationAfter()).isEqualTo("Parcial 1");
    assertThat(reverted.valueAfter()).isEqualByComparingTo("7");
    assertThat(reverted.publishedEvaluationAfter()).isEqualTo("Parcial 1");
    assertThat(reverted.publishedValueAfter()).isEqualByComparingTo("7");
    assertThat(reverted.previousStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.PENDING_CHANGES);
    assertThat(reverted.newStatus()).isEqualTo(CourseEnrollmentGradePublicationStatus.PUBLISHED);
    assertThat(requireNonNull(reverted.actor()).id()).isEqualTo(f.teacher());
  }

  @Test
  void pendingDeletionWithPriorEditIsCancelledOnExit() {
    final var f = fixture();

    final var gradeId =
        tx(
            () -> {
              final var created =
                  grades.create(
                      f.institutionId(),
                      f.enrollmentA(),
                      "Parcial 1",
                      new BigDecimal("7"),
                      f.teacher());
              grades.publishClass(f.institutionId(), f.classId(), f.teacher());
              return created.id();
            });

    updateGrade(f, f.enrollmentA(), gradeId, "Parcial 1", "9");

    tx(
        () -> {
          final var current = gradeOf(f, f.enrollmentA(), gradeId);
          grades.delete(
              f.institutionId(), f.enrollmentA(), gradeId, current.version(), f.teacher());
          return null;
        });

    assertThat(gradeOf(f, f.enrollmentA(), gradeId).publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.PENDING_DELETION);

    completeEnrollment(f, f.enrollmentA());

    final var restored = gradeOf(f, f.enrollmentA(), gradeId);
    assertThat(restored.value()).isEqualByComparingTo("7");
    assertThat(restored.evaluation()).isEqualTo("Parcial 1");
    assertThat(restored.publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.PUBLISHED);
    assertThat(tx(() -> grades.archived(f.institutionId(), f.enrollmentA()))).isEmpty();

    final var events = historyOf(f, f.enrollmentA(), gradeId);
    final var reverted = events.getContent().get(0);
    assertThat(reverted.operation())
        .isEqualTo(CourseEnrollmentGradeHistoryOperation.REVERTED_TO_PUBLISHED_ON_EXIT);
    assertThat(reverted.valueBefore()).isEqualByComparingTo("9");
    assertThat(reverted.valueAfter()).isEqualByComparingTo("7");
    assertThat(reverted.previousStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.PENDING_DELETION);
    assertThat(reverted.newStatus()).isEqualTo(CourseEnrollmentGradePublicationStatus.PUBLISHED);
  }

  @Test
  void cleanPublishedGradeIsUntouchedOnExit() {
    final var f = fixture();

    final var gradeId =
        tx(
            () -> {
              final var created =
                  grades.create(
                      f.institutionId(),
                      f.enrollmentA(),
                      "Parcial 1",
                      new BigDecimal("7"),
                      f.teacher());
              grades.publishClass(f.institutionId(), f.classId(), f.teacher());
              return created.id();
            });

    final var before = gradeOf(f, f.enrollmentA(), gradeId);

    completeEnrollment(f, f.enrollmentA());

    final var after = gradeOf(f, f.enrollmentA(), gradeId);
    assertThat(after.version()).isEqualTo(before.version());
    assertThat(after.updatedAt()).isEqualTo(before.updatedAt());
    assertThat(after.publishedAt()).isEqualTo(before.publishedAt());
    assertThat(requireNonNull(after.publishedBy()).id())
        .isEqualTo(requireNonNull(before.publishedBy()).id());
    assertThat(after.publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.PUBLISHED);

    final var events = historyOf(f, f.enrollmentA(), gradeId);
    assertThat(events.getTotalElements()).isEqualTo(2);
    assertThat(events.getContent().stream().map(event -> event.operation()).toList())
        .doesNotContain(CourseEnrollmentGradeHistoryOperation.REVERTED_TO_PUBLISHED_ON_EXIT);
  }

  @Test
  void crossedWorkingTitlesAreRestoredOnExitWithoutUniqueViolation() {
    final var f = fixture();

    final var gradeA =
        tx(
            () -> {
              final var createdA =
                  grades.create(
                      f.institutionId(),
                      f.enrollmentA(),
                      "Parcial 1",
                      new BigDecimal("7"),
                      f.teacher());
              grades.create(
                  f.institutionId(),
                  f.enrollmentA(),
                  "Parcial 2",
                  new BigDecimal("8"),
                  f.teacher());
              grades.publishClass(f.institutionId(), f.classId(), f.teacher());
              return createdA.id();
            });
    final var gradeB =
        tx(
            () ->
                grades.listForManagement(f.institutionId(), f.enrollmentA()).stream()
                    .filter(grade -> !grade.id().equals(gradeA))
                    .findFirst()
                    .orElseThrow()
                    .id());

    updateGrade(f, f.enrollmentA(), gradeA, "Parcial 3", "7");
    updateGrade(f, f.enrollmentA(), gradeB, "Parcial 1", "8");
    updateGrade(f, f.enrollmentA(), gradeA, "Parcial 2", "7");

    completeEnrollment(f, f.enrollmentA());

    assertThat(gradeOf(f, f.enrollmentA(), gradeA).evaluation()).isEqualTo("Parcial 1");
    assertThat(gradeOf(f, f.enrollmentA(), gradeB).evaluation()).isEqualTo("Parcial 2");
    assertThat(gradeOf(f, f.enrollmentA(), gradeA).publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.PUBLISHED);
    assertThat(gradeOf(f, f.enrollmentA(), gradeB).publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.PUBLISHED);

    final var eventsA = historyOf(f, f.enrollmentA(), gradeA).getContent();
    assertThat(eventsA.get(0).operation())
        .isEqualTo(CourseEnrollmentGradeHistoryOperation.REVERTED_TO_PUBLISHED_ON_EXIT);
    assertThat(eventsA.get(0).evaluationBefore()).isEqualTo("Parcial 2");
    assertThat(eventsA.get(0).evaluationAfter()).isEqualTo("Parcial 1");
    assertThat(eventsA)
        .filteredOn(
            event ->
                event.operation()
                    == CourseEnrollmentGradeHistoryOperation.REVERTED_TO_PUBLISHED_ON_EXIT)
        .hasSize(1);

    final var eventsB = historyOf(f, f.enrollmentA(), gradeB).getContent();
    assertThat(eventsB.get(0).operation())
        .isEqualTo(CourseEnrollmentGradeHistoryOperation.REVERTED_TO_PUBLISHED_ON_EXIT);
    assertThat(eventsB.get(0).evaluationBefore()).isEqualTo("Parcial 1");
    assertThat(eventsB.get(0).evaluationAfter()).isEqualTo("Parcial 2");
    assertThat(eventsB)
        .filteredOn(
            event ->
                event.operation()
                    == CourseEnrollmentGradeHistoryOperation.REVERTED_TO_PUBLISHED_ON_EXIT)
        .hasSize(1);
  }

  @Test
  void permanentTitleConflictRejectsExitAndRollsBack() {
    final var f = fixture();

    final var gradeA =
        tx(
            () -> {
              final var createdA =
                  grades.create(
                      f.institutionId(),
                      f.enrollmentA(),
                      "Parcial 1",
                      new BigDecimal("7"),
                      f.teacher());
              grades.create(
                  f.institutionId(),
                  f.enrollmentA(),
                  "Parcial 2",
                  new BigDecimal("8"),
                  f.teacher());
              grades.publishClass(f.institutionId(), f.classId(), f.teacher());
              return createdA.id();
            });
    final var gradeB =
        tx(
            () ->
                grades.listForManagement(f.institutionId(), f.enrollmentA()).stream()
                    .filter(grade -> !grade.id().equals(gradeA))
                    .findFirst()
                    .orElseThrow()
                    .id());

    updateGrade(f, f.enrollmentA(), gradeA, "Parcial 3", "7");

    // Simulates anomalous preexisting data: two rows converge on the same published title.
    jdbcTemplate.update(
        "UPDATE course_enrollment_grades SET evaluation = 'Parcial 1',"
            + " published_evaluation = 'Parcial 1' WHERE course_enrollment_grade_id = ?",
        gradeB);

    assertThatThrownBy(() -> completeEnrollment(f, f.enrollmentA()))
        .isInstanceOf(EnrollmentValidationException.class)
        .hasMessage(GradeMessages.EXIT_PUBLISHED_TITLE_CONFLICT);

    assertThat(tx(() -> enrollments.get(f.institutionId(), f.enrollmentA()).status()))
        .isEqualTo(CourseEnrollmentStatus.ENROLLED);

    final var untouched = gradeOf(f, f.enrollmentA(), gradeA);
    assertThat(untouched.evaluation()).isEqualTo("Parcial 3");
    assertThat(untouched.publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.PENDING_CHANGES);

    assertThat(
            historyOf(f, f.enrollmentA(), gradeA).getContent().stream()
                .map(event -> event.operation())
                .toList())
        .doesNotContain(CourseEnrollmentGradeHistoryOperation.REVERTED_TO_PUBLISHED_ON_EXIT);
  }

  @Test
  void courseClosureDiscardsDraftAndRestoresPublishedGrade() {
    final var f = fixture();

    final var publishedId =
        tx(
            () -> {
              final var published =
                  grades.create(
                      f.institutionId(),
                      f.enrollmentA(),
                      "Parcial 1",
                      new BigDecimal("7"),
                      f.teacher());
              grades.publishClass(f.institutionId(), f.classId(), f.teacher());
              return published.id();
            });
    final var draftId =
        tx(
            () ->
                grades
                    .create(
                        f.institutionId(),
                        f.enrollmentA(),
                        "Borrador",
                        new BigDecimal("5"),
                        f.teacher())
                    .id());

    updateGrade(f, f.enrollmentA(), publishedId, "Parcial 1", "9");

    final var beforeClose = tx(() -> grades.pendingSummary(f.institutionId(), f.classId()));
    assertThat(beforeClose.pendingChanges()).isEqualTo(2);

    tx(
        () -> {
          closeCourse.execute(
              f.institutionId(), f.courseId(), new CourseStatusRequest(CourseStatus.CLOSED));
          return null;
        });

    final var enrollment = tx(() -> enrollments.get(f.institutionId(), f.enrollmentA()));
    assertThat(enrollment.status()).isEqualTo(CourseEnrollmentStatus.COMPLETED);
    assertThat(enrollment.academicStatus()).isEqualTo(AcademicEnrollmentStatus.PENDING_RESULT);

    final var management = tx(() -> grades.listForManagement(f.institutionId(), f.enrollmentA()));
    assertThat(management).hasSize(1);
    assertThat(management.get(0).id()).isEqualTo(publishedId);
    assertThat(management.get(0).value()).isEqualByComparingTo("7");
    assertThat(management.get(0).publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.PUBLISHED);

    assertThat(tx(() -> grades.listPublished(f.institutionId(), f.enrollmentA())).get(0).value())
        .isEqualByComparingTo("7");

    final var archived = tx(() -> grades.archived(f.institutionId(), f.enrollmentA()));
    assertThat(archived).hasSize(1);
    assertThat(archived.get(0).gradeId()).isEqualTo(draftId);
    assertThat(archived.get(0).deletionType())
        .isEqualTo(CourseEnrollmentGradeHistoryOperation.DISCARDED_DRAFT_ON_EXIT);

    assertThat(tx(() -> grades.pendingClasses(f.institutionId()))).isEmpty();
    final var afterClose = tx(() -> grades.pendingSummary(f.institutionId(), f.classId()));
    assertThat(afterClose.pendingChanges()).isZero();
    assertThat(afterClose.affectedStudents()).isZero();
  }

  @Test
  void yearClosureCleansOnExitAndLaterResultDoesNotRepeatDiscard() {
    final var f = fixture();

    final var gradeId =
        tx(
            () -> {
              final var published =
                  grades.create(
                      f.institutionId(),
                      f.enrollmentA(),
                      "Parcial 1",
                      new BigDecimal("7"),
                      f.teacher());
              grades.publishClass(f.institutionId(), f.classId(), f.teacher());
              grades.create(
                  f.institutionId(), f.enrollmentA(), "Borrador", new BigDecimal("5"), f.teacher());
              return published.id();
            });

    updateGrade(f, f.enrollmentA(), gradeId, "Parcial 1", "9");

    tx(
        () -> {
          closeYear.execute(
              f.institutionId(),
              f.yearId(),
              new AcademicYearStatusRequest(AcademicYearStatus.CLOSED));
          return null;
        });

    final var closed = tx(() -> enrollments.get(f.institutionId(), f.enrollmentA()));
    assertThat(closed.status()).isEqualTo(CourseEnrollmentStatus.COMPLETED);
    assertThat(closed.academicStatus()).isEqualTo(AcademicEnrollmentStatus.PENDING_RESULT);
    assertThat(closed.schedules()).allMatch(schedule -> schedule.releasedAt() != null);

    final var eventsBefore = historyOf(f, f.enrollmentA(), gradeId).getTotalElements();
    final var archivedBefore = tx(() -> grades.archived(f.institutionId(), f.enrollmentA())).size();
    assertThat(archivedBefore).isEqualTo(1);

    tx(
        () -> {
          enrollments.updateAcademicStatus(
              f.institutionId(),
              f.enrollmentA(),
              new UpdateAcademicEnrollmentStatusRequest(
                  AcademicEnrollmentStatus.PASSED,
                  "Aprobado",
                  enrollmentVersion(f, f.enrollmentA())),
              f.teacher());
          return null;
        });

    final var updated = tx(() -> enrollments.get(f.institutionId(), f.enrollmentA()));
    assertThat(updated.status()).isEqualTo(CourseEnrollmentStatus.COMPLETED);
    assertThat(updated.academicStatus()).isEqualTo(AcademicEnrollmentStatus.PASSED);
    assertThat(historyOf(f, f.enrollmentA(), gradeId).getTotalElements()).isEqualTo(eventsBefore);
    assertThat(tx(() -> grades.archived(f.institutionId(), f.enrollmentA())))
        .hasSize(archivedBefore);
  }

  @Test
  void repeatedCourseCloseDoesNotDuplicateExitEvents() {
    final var f = fixture();

    final var gradeId =
        tx(
            () -> {
              final var published =
                  grades.create(
                      f.institutionId(),
                      f.enrollmentA(),
                      "Parcial 1",
                      new BigDecimal("7"),
                      f.teacher());
              grades.publishClass(f.institutionId(), f.classId(), f.teacher());
              grades.create(
                  f.institutionId(), f.enrollmentA(), "Borrador", new BigDecimal("5"), f.teacher());
              return published.id();
            });

    updateGrade(f, f.enrollmentA(), gradeId, "Parcial 1", "9");

    tx(
        () -> {
          closeCourse.execute(
              f.institutionId(), f.courseId(), new CourseStatusRequest(CourseStatus.CLOSED));
          return null;
        });

    final var eventsBefore = historyOf(f, f.enrollmentA(), gradeId).getTotalElements();
    final var managementBefore =
        tx(() -> grades.listForManagement(f.institutionId(), f.enrollmentA())).size();
    final var archivedBefore = tx(() -> grades.archived(f.institutionId(), f.enrollmentA())).size();

    tx(
        () -> {
          closeCourse.execute(
              f.institutionId(), f.courseId(), new CourseStatusRequest(CourseStatus.CLOSED));
          return null;
        });

    assertThat(tx(() -> enrollments.get(f.institutionId(), f.enrollmentA()).status()))
        .isEqualTo(CourseEnrollmentStatus.COMPLETED);
    assertThat(historyOf(f, f.enrollmentA(), gradeId).getTotalElements()).isEqualTo(eventsBefore);
    assertThat(tx(() -> grades.listForManagement(f.institutionId(), f.enrollmentA())))
        .hasSize(managementBefore);
    assertThat(tx(() -> grades.archived(f.institutionId(), f.enrollmentA())))
        .hasSize(archivedBefore);
  }

  @Test
  void voluntaryAndAdministrativeWithdrawalsDiscardOnlyTheirEnrollment() {
    final var f = fixture();

    final var publishedA =
        tx(
            () -> {
              final var created =
                  grades.create(
                      f.institutionId(),
                      f.enrollmentA(),
                      "Parcial 1",
                      new BigDecimal("7"),
                      f.teacher());
              grades.publishClass(f.institutionId(), f.classId(), f.teacher());
              return created.id();
            });
    final var publishedC =
        tx(
            () -> {
              final var created =
                  grades.create(
                      f.institutionId(),
                      f.enrollmentC(),
                      "Parcial 1",
                      new BigDecimal("8"),
                      f.teacher());
              grades.publishClass(f.institutionId(), f.otherClassId(), f.teacher());
              return created.id();
            });

    final var draftA =
        tx(
            () ->
                grades
                    .create(
                        f.institutionId(),
                        f.enrollmentA(),
                        "Borrador A",
                        new BigDecimal("5"),
                        f.teacher())
                    .id());
    final var draftC =
        tx(
            () ->
                grades
                    .create(
                        f.institutionId(),
                        f.enrollmentC(),
                        "Borrador C",
                        new BigDecimal("4"),
                        f.teacher())
                    .id());
    final var draftB =
        tx(
            () ->
                grades
                    .create(
                        f.institutionId(),
                        f.enrollmentB(),
                        "Borrador B",
                        new BigDecimal("3"),
                        f.teacher())
                    .id());

    updateGrade(f, f.enrollmentA(), publishedA, "Parcial 1", "9");
    updateGrade(f, f.enrollmentC(), publishedC, "Parcial 1", "6");

    withdraw(f, f.enrollmentA(), CourseWithdrawalType.VOLUNTARY, "Baja voluntaria");
    withdraw(f, f.enrollmentC(), CourseWithdrawalType.ADMINISTRATIVE, "Baja administrativa");

    final var withdrawnA = tx(() -> enrollments.get(f.institutionId(), f.enrollmentA()));
    assertThat(withdrawnA.status()).isEqualTo(CourseEnrollmentStatus.WITHDRAWN);
    assertThat(withdrawnA.academicStatus()).isEqualTo(AcademicEnrollmentStatus.NOT_APPLICABLE);
    assertThat(withdrawnA.schedules()).allMatch(schedule -> schedule.releasedAt() != null);

    final var withdrawnC = tx(() -> enrollments.get(f.institutionId(), f.enrollmentC()));
    assertThat(withdrawnC.status()).isEqualTo(CourseEnrollmentStatus.ADMINISTRATIVELY_WITHDRAWN);
    assertThat(withdrawnC.academicStatus()).isEqualTo(AcademicEnrollmentStatus.NOT_APPLICABLE);
    assertThat(withdrawnC.schedules()).allMatch(schedule -> schedule.releasedAt() != null);

    final var restoredA = gradeOf(f, f.enrollmentA(), publishedA);
    assertThat(restoredA.evaluation()).isEqualTo("Parcial 1");
    assertThat(restoredA.value()).isEqualByComparingTo("7");
    assertThat(restoredA.publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.PUBLISHED);
    assertThat(tx(() -> grades.listForManagement(f.institutionId(), f.enrollmentA()))).hasSize(1);

    final var revertedA = historyOf(f, f.enrollmentA(), publishedA).getContent().get(0);
    assertThat(revertedA.operation())
        .isEqualTo(CourseEnrollmentGradeHistoryOperation.REVERTED_TO_PUBLISHED_ON_EXIT);
    assertThat(revertedA.valueBefore()).isEqualByComparingTo("9");
    assertThat(revertedA.valueAfter()).isEqualByComparingTo("7");

    final var archivedA = tx(() -> grades.archived(f.institutionId(), f.enrollmentA()));
    assertThat(archivedA).hasSize(1);
    assertThat(archivedA.get(0).gradeId()).isEqualTo(draftA);
    assertThat(archivedA.get(0).deletionType())
        .isEqualTo(CourseEnrollmentGradeHistoryOperation.DISCARDED_DRAFT_ON_EXIT);

    final var restoredC = gradeOf(f, f.enrollmentC(), publishedC);
    assertThat(restoredC.evaluation()).isEqualTo("Parcial 1");
    assertThat(restoredC.value()).isEqualByComparingTo("8");
    assertThat(restoredC.publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.PUBLISHED);

    final var revertedC = historyOf(f, f.enrollmentC(), publishedC).getContent().get(0);
    assertThat(revertedC.operation())
        .isEqualTo(CourseEnrollmentGradeHistoryOperation.REVERTED_TO_PUBLISHED_ON_EXIT);
    assertThat(revertedC.valueBefore()).isEqualByComparingTo("6");
    assertThat(revertedC.valueAfter()).isEqualByComparingTo("8");

    final var archivedC = tx(() -> grades.archived(f.institutionId(), f.enrollmentC()));
    assertThat(archivedC).hasSize(1);
    assertThat(archivedC.get(0).gradeId()).isEqualTo(draftC);
    assertThat(archivedC.get(0).deletionType())
        .isEqualTo(CourseEnrollmentGradeHistoryOperation.DISCARDED_DRAFT_ON_EXIT);

    final var untouchedB = tx(() -> enrollments.get(f.institutionId(), f.enrollmentB()));
    assertThat(untouchedB.status()).isEqualTo(CourseEnrollmentStatus.ENROLLED);
    assertThat(untouchedB.schedules()).allMatch(schedule -> schedule.releasedAt() == null);

    final var managementB = tx(() -> grades.listForManagement(f.institutionId(), f.enrollmentB()));
    assertThat(managementB).hasSize(1);
    assertThat(managementB.get(0).id()).isEqualTo(draftB);
    assertThat(managementB.get(0).publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.DRAFT);
    assertThat(tx(() -> grades.archived(f.institutionId(), f.enrollmentB()))).isEmpty();
  }

  @Test
  void failedReconciliationRollsBackEnrollmentSchedulesAndGrades() {
    final var f = fixture();

    final var published =
        tx(
            () -> {
              final var created =
                  grades.create(
                      f.institutionId(),
                      f.enrollmentA(),
                      "Parcial 1",
                      new BigDecimal("7"),
                      f.teacher());
              grades.publishClass(f.institutionId(), f.classId(), f.teacher());
              return created.id();
            });
    final var draft =
        tx(
            () ->
                grades
                    .create(
                        f.institutionId(),
                        f.enrollmentA(),
                        "Borrador",
                        new BigDecimal("5"),
                        f.teacher())
                    .id());

    updateGrade(f, f.enrollmentA(), published, "Parcial 1", "9");

    assertThatThrownBy(
            () ->
                tx(
                    () -> {
                      enrollments.withdraw(
                          f.institutionId(),
                          f.enrollmentA(),
                          new WithdrawCourseEnrollmentRequest(
                              CourseWithdrawalType.VOLUNTARY,
                              "Baja",
                              enrollmentVersion(f, f.enrollmentA())),
                          UUID.randomUUID());
                      return null;
                    }))
        .isInstanceOf(DataAccessException.class);

    final var enrollment = tx(() -> enrollments.get(f.institutionId(), f.enrollmentA()));
    assertThat(enrollment.status()).isEqualTo(CourseEnrollmentStatus.ENROLLED);
    assertThat(enrollment.academicStatus()).isEqualTo(AcademicEnrollmentStatus.IN_PROGRESS);
    assertThat(enrollment.schedules()).allMatch(schedule -> schedule.releasedAt() == null);

    final var management = tx(() -> grades.listForManagement(f.institutionId(), f.enrollmentA()));
    assertThat(management.stream().map(grade -> grade.id()).toList())
        .containsExactlyInAnyOrder(published, draft);

    final var pending = gradeOf(f, f.enrollmentA(), published);
    assertThat(pending.value()).isEqualByComparingTo("9");
    assertThat(pending.publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.PENDING_CHANGES);

    assertThat(historyOf(f, f.enrollmentA(), published).getTotalElements()).isEqualTo(3);
    assertThat(tx(() -> grades.archived(f.institutionId(), f.enrollmentA()))).isEmpty();
  }

  @Test
  void publishClassRejectsPendingGradesOfClosedEnrollmentsAtomically() {
    final var f = fixture();

    final var draftA =
        tx(
            () ->
                grades
                    .create(
                        f.institutionId(),
                        f.enrollmentA(),
                        "Parcial A",
                        new BigDecimal("7"),
                        f.teacher())
                    .id());
    final var draftB =
        tx(
            () ->
                grades
                    .create(
                        f.institutionId(),
                        f.enrollmentB(),
                        "Parcial B",
                        new BigDecimal("8"),
                        f.teacher())
                    .id());

    // Simulates a preexisting anomaly: a pending draft belongs to an already closed enrollment.
    jdbcTemplate.update(
        "UPDATE course_enrollments SET status = 'COMPLETED', academic_status = 'PENDING_RESULT'"
            + " WHERE course_enrollment_id = ?",
        f.enrollmentA());

    assertThatThrownBy(
            () ->
                tx(
                    () -> {
                      grades.publishClass(f.institutionId(), f.classId(), f.teacher());
                      return null;
                    }))
        .isInstanceOf(EnrollmentValidationException.class)
        .hasMessage(GradeMessages.ENROLLMENT_NOT_ENROLLED);

    assertThat(gradeOf(f, f.enrollmentA(), draftA).publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.DRAFT);
    assertThat(gradeOf(f, f.enrollmentB(), draftB).publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.DRAFT);
    assertThat(tx(() -> grades.listPublished(f.institutionId(), f.enrollmentA()))).isEmpty();
    assertThat(tx(() -> grades.listPublished(f.institutionId(), f.enrollmentB()))).isEmpty();
    assertThat(historyOf(f, f.enrollmentB(), draftB).getTotalElements()).isEqualTo(1);

    // Sanitized closed record: the orphan pending draft is removed; enrolled peers can publish.
    jdbcTemplate.update(
        "DELETE FROM course_enrollment_grades WHERE course_enrollment_grade_id = ?", draftA);

    final var result = tx(() -> grades.publishClass(f.institutionId(), f.classId(), f.teacher()));
    assertThat(result.publishedGrades()).isEqualTo(1);
    assertThat(result.deletedGrades()).isZero();

    assertThat(gradeOf(f, f.enrollmentB(), draftB).publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.PUBLISHED);
    assertThat(tx(() -> grades.listPublished(f.institutionId(), f.enrollmentB()))).hasSize(1);
    assertThat(tx(() -> grades.listPublished(f.institutionId(), f.enrollmentA()))).isEmpty();
    assertThat(tx(() -> grades.listForManagement(f.institutionId(), f.enrollmentA()))).isEmpty();
  }

  @Test
  void publishClassRejectsPendingChangesOfClosedEnrollments() {
    final var f = fixture();

    final var gradeA =
        tx(
            () -> {
              final var created =
                  grades.create(
                      f.institutionId(),
                      f.enrollmentA(),
                      "Parcial A",
                      new BigDecimal("7"),
                      f.teacher());
              grades.publishClass(f.institutionId(), f.classId(), f.teacher());
              return created.id();
            });

    updateGrade(f, f.enrollmentA(), gradeA, "Parcial A bis", "9");

    final var draftB =
        tx(
            () ->
                grades
                    .create(
                        f.institutionId(),
                        f.enrollmentB(),
                        "Parcial B",
                        new BigDecimal("8"),
                        f.teacher())
                    .id());

    // Simulates a preexisting anomaly: unpublished changes on an already closed enrollment.
    jdbcTemplate.update(
        "UPDATE course_enrollments SET status = 'COMPLETED', academic_status = 'PENDING_RESULT'"
            + " WHERE course_enrollment_id = ?",
        f.enrollmentA());

    assertThatThrownBy(
            () ->
                tx(
                    () -> {
                      grades.publishClass(f.institutionId(), f.classId(), f.teacher());
                      return null;
                    }))
        .isInstanceOf(EnrollmentValidationException.class)
        .hasMessage(GradeMessages.ENROLLMENT_NOT_ENROLLED);

    assertThat(gradeOf(f, f.enrollmentA(), gradeA).publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.PENDING_CHANGES);
    assertThat(gradeOf(f, f.enrollmentB(), draftB).publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.DRAFT);
    assertThat(tx(() -> grades.listPublished(f.institutionId(), f.enrollmentB()))).isEmpty();
  }

  @Test
  void historyAndArchivedListingsShowNewOperationsWithIsolation() {
    final var f = fixture();
    final var other = fixture();

    final var gradeA =
        tx(
            () -> {
              final var created =
                  grades.create(
                      f.institutionId(),
                      f.enrollmentA(),
                      "Parcial 1",
                      new BigDecimal("7"),
                      f.teacher());
              grades.publishClass(f.institutionId(), f.classId(), f.teacher());
              return created.id();
            });
    final var draftA =
        tx(
            () ->
                grades
                    .create(
                        f.institutionId(),
                        f.enrollmentA(),
                        "Borrador",
                        new BigDecimal("5"),
                        f.teacher())
                    .id());

    updateGrade(f, f.enrollmentA(), gradeA, "Parcial 1", "9");
    completeEnrollment(f, f.enrollmentA());

    final var firstPage = historyOf(f, f.enrollmentA(), gradeA, PageRequest.of(0, 2));
    assertThat(firstPage.getTotalElements()).isEqualTo(4);
    assertThat(firstPage.getTotalPages()).isEqualTo(2);
    assertThat(firstPage.getContent().stream().map(event -> event.operation()).toList())
        .containsExactly(
            CourseEnrollmentGradeHistoryOperation.REVERTED_TO_PUBLISHED_ON_EXIT,
            CourseEnrollmentGradeHistoryOperation.UPDATED);

    final var secondPage = historyOf(f, f.enrollmentA(), gradeA, PageRequest.of(1, 2));
    assertThat(secondPage.getContent().stream().map(event -> event.operation()).toList())
        .containsExactlyInAnyOrder(
            CourseEnrollmentGradeHistoryOperation.PUBLISHED,
            CourseEnrollmentGradeHistoryOperation.CREATED);

    final var discarded = historyOf(f, f.enrollmentA(), draftA);
    assertThat(discarded.getTotalElements()).isEqualTo(2);
    assertThat(discarded.getContent().get(0).operation())
        .isEqualTo(CourseEnrollmentGradeHistoryOperation.DISCARDED_DRAFT_ON_EXIT);

    final var archived = tx(() -> grades.archived(f.institutionId(), f.enrollmentA()));
    assertThat(archived).hasSize(1);
    assertThat(archived.get(0).gradeId()).isEqualTo(draftA);
    assertThat(archived.get(0).deletionType())
        .isEqualTo(CourseEnrollmentGradeHistoryOperation.DISCARDED_DRAFT_ON_EXIT);

    assertThatThrownBy(
            () ->
                tx(
                    () ->
                        grades.history(
                            f.institutionId(), f.enrollmentB(), gradeA, PageRequest.of(0, 50))))
        .isInstanceOf(EnrollmentValidationException.class)
        .hasMessage(GradeMessages.GRADE_NOT_FOUND);
    assertThat(tx(() -> grades.archived(f.institutionId(), f.enrollmentB()))).isEmpty();

    assertThatThrownBy(
            () ->
                tx(
                    () ->
                        grades.history(
                            other.institutionId(), f.enrollmentA(), gradeA, PageRequest.of(0, 50))))
        .isInstanceOf(EnrollmentValidationException.class)
        .hasMessage(EnrollmentMessages.COURSE_ENROLLMENT_NOT_FOUND);
    assertThatThrownBy(() -> tx(() -> grades.archived(other.institutionId(), f.enrollmentA())))
        .isInstanceOf(EnrollmentValidationException.class)
        .hasMessage(EnrollmentMessages.COURSE_ENROLLMENT_NOT_FOUND);
  }

  private void withdraw(
      final Fixture f,
      final UUID enrollmentId,
      final CourseWithdrawalType type,
      final String reason) {
    tx(
        () -> {
          enrollments.withdraw(
              f.institutionId(),
              enrollmentId,
              new WithdrawCourseEnrollmentRequest(type, reason, enrollmentVersion(f, enrollmentId)),
              f.teacher());
          return null;
        });
  }

  private Long enrollmentVersion(final Fixture f, final UUID enrollmentId) {
    return tx(() -> enrollments.get(f.institutionId(), enrollmentId).version());
  }

  private void completeEnrollment(final Fixture f, final UUID enrollmentId) {
    tx(
        () -> {
          enrollments.updateAcademicStatus(
              f.institutionId(),
              enrollmentId,
              new UpdateAcademicEnrollmentStatusRequest(
                  AcademicEnrollmentStatus.REGULARIZED,
                  "Resultado final",
                  enrollmentVersion(f, enrollmentId)),
              f.teacher());
          return null;
        });
  }

  private CourseEnrollmentGradeResponse gradeOf(
      final Fixture f, final UUID enrollmentId, final UUID gradeId) {
    return tx(
        () ->
            grades.listForManagement(f.institutionId(), enrollmentId).stream()
                .filter(grade -> grade.id().equals(gradeId))
                .findFirst()
                .orElseThrow());
  }

  private void updateGrade(
      final Fixture f,
      final UUID enrollmentId,
      final UUID gradeId,
      final String evaluation,
      final String value) {
    tx(
        () -> {
          final var current = gradeOf(f, enrollmentId, gradeId);
          grades.update(
              f.institutionId(),
              enrollmentId,
              gradeId,
              evaluation,
              new BigDecimal(value),
              current.version(),
              f.teacher());
          return null;
        });
  }

  private Page<CourseEnrollmentGradeHistoryResponse> historyOf(
      final Fixture f, final UUID enrollmentId, final UUID gradeId) {
    return historyOf(f, enrollmentId, gradeId, PageRequest.of(0, 50));
  }

  private Page<CourseEnrollmentGradeHistoryResponse> historyOf(
      final Fixture f, final UUID enrollmentId, final UUID gradeId, final Pageable pageable) {
    return tx(() -> grades.history(f.institutionId(), enrollmentId, gradeId, pageable));
  }

  private Fixture fixture() {
    return tx(
        () -> {
          final Institution institution =
              InstitutionalTestData.createInstitution(em, "grade-exit-" + UUID.randomUUID());
          roles.provision(institution);

          final var path = persist(TrainingPath.create(institution, "Trayecto", null));
          final var plan =
              persist(StudyPlan.create(institution, path, "Plan", LocalDate.of(2026, 1, 1), null));
          plan.activate();
          final var primarySpace = persist(space(institution, "Lenguaje"));
          final var primaryPlacement =
              persist(
                  StudyPlanSpace.create(
                      institution,
                      plan,
                      primarySpace,
                      null,
                      RequirementType.REQUIRED,
                      1,
                      ApprovalMode.PROMOTION));
          final var secondarySpace = persist(space(institution, "Matemática"));
          final var secondaryPlacement =
              persist(
                  StudyPlanSpace.create(
                      institution,
                      plan,
                      secondarySpace,
                      null,
                      RequirementType.REQUIRED,
                      2,
                      ApprovalMode.PROMOTION));
          final var year =
              persist(
                  AcademicYear.create(
                      institution,
                      2026,
                      LocalDate.of(2026, 1, 1),
                      LocalDate.of(2026, 12, 31),
                      LocalDate.of(2026, 1, 1)));
          year.transitionTo(AcademicYearStatus.ACTIVE);

          final var course = persist(Course.create(institution, primaryPlacement, year, null));
          final var courseClass = persist(CourseClass.create(institution, course, 1));
          final var classSchedule = schedule(institution, courseClass, CourseDay.MONDAY);

          final var otherCourse =
              persist(Course.create(institution, secondaryPlacement, year, null));
          final var otherClass = persist(CourseClass.create(institution, otherCourse, 1));
          final var otherSchedule = schedule(institution, otherClass, CourseDay.TUESDAY);

          final Person teacher = persist(InstitutionalTestData.person(institution, "30000001"));

          final var enrollmentA =
              enroll(institution, "30000002", course, courseClass, classSchedule, CourseDay.MONDAY);
          final var enrollmentB =
              enroll(institution, "30000003", course, courseClass, classSchedule, CourseDay.MONDAY);
          final var enrollmentC =
              enroll(
                  institution,
                  "30000004",
                  otherCourse,
                  otherClass,
                  otherSchedule,
                  CourseDay.TUESDAY);

          return new Fixture(
              institution.getId(),
              teacher.getId(),
              course.getId(),
              courseClass.getId(),
              otherCourse.getId(),
              otherClass.getId(),
              year.getId(),
              enrollmentA.getId(),
              enrollmentB.getId(),
              enrollmentC.getId());
        });
  }

  private AcademicSpace space(final Institution institution, final String name) {
    return AcademicSpace.create(
        institution, name, null, AcademicSpaceType.SUBJECT, AcademicSpaceFormat.GRUPAL, false);
  }

  private CourseClassSchedule schedule(
      final Institution institution, final CourseClass courseClass, final CourseDay day) {
    final var classDay = persist(CourseClassDay.create(institution, courseClass, day, 3, null));

    return persist(
        CourseClassSchedule.create(
            institution, classDay, LocalTime.of(10, 0), LocalTime.of(11, 0)));
  }

  private CourseEnrollment enroll(
      final Institution institution,
      final String documentNumber,
      final Course course,
      final CourseClass courseClass,
      final CourseClassSchedule classSchedule,
      final CourseDay day) {
    final Person person = persist(InstitutionalTestData.person(institution, documentNumber));
    final var student =
        persist(
            Student.builder()
                .institution(institution)
                .person(person)
                .fileNumber("F-" + UUID.randomUUID())
                .enrollmentDate(LocalDate.of(2026, 1, 1))
                .build());
    final var enrollment =
        persist(
            CourseEnrollment.create(
                institution,
                student,
                course,
                courseClass,
                CourseEnrollmentSource.MANUAL,
                null,
                Instant.now(Clock.systemUTC())));
    persist(
        CourseEnrollmentSchedule.create(
            institution,
            enrollment,
            classSchedule,
            null,
            day,
            LocalTime.of(10, 0),
            LocalTime.of(11, 0)));

    return enrollment;
  }

  private <T> T persist(final T entity) {
    em.persist(entity);
    return entity;
  }

  private <T> T tx(final Supplier<T> action) {
    return new TransactionTemplate(transactions).execute(status -> action.get());
  }

  private record Fixture(
      UUID institutionId,
      UUID teacher,
      UUID courseId,
      UUID classId,
      UUID otherCourseId,
      UUID otherClassId,
      UUID yearId,
      UUID enrollmentA,
      UUID enrollmentB,
      UUID enrollmentC) {}
}
