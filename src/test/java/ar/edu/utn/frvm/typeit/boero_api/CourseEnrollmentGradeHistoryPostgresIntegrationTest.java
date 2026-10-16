package ar.edu.utn.frvm.typeit.boero_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicSpace;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.CourseClass;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.ApprovalMode;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.RequirementType;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionRoleProvisioner;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollmentGradeHistory;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentGradeHistoryOperation;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentSource;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.GradeMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentGradeResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.CourseEnrollmentGradeService;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Student;
import ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class CourseEnrollmentGradeHistoryPostgresIntegrationTest extends DatabaseMigrationTestSupport {

  @Autowired EntityManager em;
  @Autowired PlatformTransactionManager transactions;
  @Autowired CourseEnrollmentGradeService grades;
  @Autowired InstitutionRoleProvisioner roles;

  @BeforeEach
  void authenticate() {
    authenticatePlatformAdministrator();
  }

  @Test
  void createAndChainedEditsKeepFullSequence() {
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
          final var current = grades.listForManagement(f.institutionId(), f.enrollmentA()).get(0);
          grades.update(
              f.institutionId(),
              f.enrollmentA(),
              current.id(),
              "Parcial 1",
              new BigDecimal("8"),
              current.version(),
              f.teacher());
          return null;
        });

    tx(
        () -> {
          final var current = grades.listForManagement(f.institutionId(), f.enrollmentA()).get(0);
          grades.update(
              f.institutionId(),
              f.enrollmentA(),
              current.id(),
              "Parcial 1",
              new BigDecimal("9"),
              current.version(),
              f.teacher());
          return null;
        });

    final var page =
        tx(
            () ->
                grades.history(f.institutionId(), f.enrollmentA(), gradeId, PageRequest.of(0, 50)));

    assertThat(page.getTotalElements()).isEqualTo(3);

    final var latest = page.getContent().get(0);
    assertThat(latest.operation()).isEqualTo(CourseEnrollmentGradeHistoryOperation.UPDATED);
    assertThat(latest.valueBefore()).isEqualByComparingTo("8");
    assertThat(latest.valueAfter()).isEqualByComparingTo("9");
    assertThat(latest.actor()).isNotNull();
    assertThat(latest.actor().id()).isEqualTo(f.teacher());

    final var middle = page.getContent().get(1);
    assertThat(middle.operation()).isEqualTo(CourseEnrollmentGradeHistoryOperation.UPDATED);
    assertThat(middle.valueBefore()).isEqualByComparingTo("7");
    assertThat(middle.valueAfter()).isEqualByComparingTo("8");

    final var created = page.getContent().get(2);
    assertThat(created.operation()).isEqualTo(CourseEnrollmentGradeHistoryOperation.CREATED);
    assertThat(created.valueBefore()).isNull();
    assertThat(created.valueAfter()).isEqualByComparingTo("7");
  }

  @Test
  void publishKeepsPublishedSnapshots() {
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
          grades.publishClass(f.institutionId(), f.classId(), f.teacher());
          return null;
        });

    tx(
        () -> {
          final var current = grades.listForManagement(f.institutionId(), f.enrollmentA()).get(0);
          grades.update(
              f.institutionId(),
              f.enrollmentA(),
              current.id(),
              "Parcial 1",
              new BigDecimal("9"),
              current.version(),
              f.teacher());
          return null;
        });

    tx(
        () -> {
          grades.publishClass(f.institutionId(), f.classId(), f.teacher());
          return null;
        });

    final var page =
        tx(
            () ->
                grades.history(f.institutionId(), f.enrollmentA(), gradeId, PageRequest.of(0, 50)));

    assertThat(page.getTotalElements()).isEqualTo(4);

    final var republished = page.getContent().get(0);
    assertThat(republished.operation()).isEqualTo(CourseEnrollmentGradeHistoryOperation.PUBLISHED);
    assertThat(republished.publishedValueBefore()).isEqualByComparingTo("7");
    assertThat(republished.publishedValueAfter()).isEqualByComparingTo("9");

    assertThat(tx(() -> grades.listPublished(f.institutionId(), f.enrollmentA())).get(0).value())
        .isEqualByComparingTo("9");
  }

  @Test
  void noOpUpdateCreatesNoEvent() {
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
          final var current = grades.listForManagement(f.institutionId(), f.enrollmentA()).get(0);
          grades.update(
              f.institutionId(),
              f.enrollmentA(),
              current.id(),
              "Parcial 1",
              new BigDecimal("7"),
              current.version(),
              f.teacher());
          return null;
        });

    final var page =
        tx(
            () ->
                grades.history(f.institutionId(), f.enrollmentA(), gradeId, PageRequest.of(0, 50)));

    assertThat(page.getTotalElements()).isEqualTo(1);
  }

  @Test
  void draftDeletionSurvivesInArchived() {
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
          final var current = grades.listForManagement(f.institutionId(), f.enrollmentA()).get(0);
          grades.delete(
              f.institutionId(), f.enrollmentA(), current.id(), current.version(), f.teacher());
          return null;
        });

    assertThat(tx(() -> grades.listForManagement(f.institutionId(), f.enrollmentA()))).isEmpty();

    final var archived = tx(() -> grades.archived(f.institutionId(), f.enrollmentA()));
    assertThat(archived).hasSize(1);
    assertThat(archived.get(0).gradeId()).isEqualTo(gradeId);
    assertThat(archived.get(0).deletionType())
        .isEqualTo(CourseEnrollmentGradeHistoryOperation.DELETED_DRAFT);

    final var page =
        tx(
            () ->
                grades.history(f.institutionId(), f.enrollmentA(), gradeId, PageRequest.of(0, 50)));
    assertThat(page.getTotalElements()).isEqualTo(2);
    assertThat(page.getContent().get(0).operation())
        .isEqualTo(CourseEnrollmentGradeHistoryOperation.DELETED_DRAFT);
  }

  @Test
  void markCancelAndEffectiveDeletionKeepEvents() {
    final var f = fixture();

    tx(
        () -> {
          grades.create(
              f.institutionId(), f.enrollmentA(), "Parcial 1", new BigDecimal("7"), f.teacher());
          grades.publishClass(f.institutionId(), f.classId(), f.teacher());
          return null;
        });

    tx(
        () -> {
          final var current = grades.listForManagement(f.institutionId(), f.enrollmentA()).get(0);
          grades.delete(
              f.institutionId(), f.enrollmentA(), current.id(), current.version(), f.teacher());
          return null;
        });

    tx(
        () -> {
          final var current = grades.listForManagement(f.institutionId(), f.enrollmentA()).get(0);
          grades.cancelDeletion(f.institutionId(), f.enrollmentA(), current.id(), f.teacher());
          return null;
        });

    tx(
        () -> {
          final var current = grades.listForManagement(f.institutionId(), f.enrollmentA()).get(0);
          grades.delete(
              f.institutionId(), f.enrollmentA(), current.id(), current.version(), f.teacher());
          grades.publishClass(f.institutionId(), f.classId(), f.teacher());
          return null;
        });

    assertThat(tx(() -> grades.listPublished(f.institutionId(), f.enrollmentA()))).isEmpty();

    final var gradeId =
        tx(
            () -> {
              final var archived = grades.archived(f.institutionId(), f.enrollmentA());
              assertThat(archived).hasSize(1);
              return archived.get(0).gradeId();
            });

    final var page =
        tx(
            () ->
                grades.history(f.institutionId(), f.enrollmentA(), gradeId, PageRequest.of(0, 50)));

    assertThat(page.getContent().stream().map(event -> event.operation()).toList())
        .contains(
            CourseEnrollmentGradeHistoryOperation.CREATED,
            CourseEnrollmentGradeHistoryOperation.PUBLISHED,
            CourseEnrollmentGradeHistoryOperation.MARKED_FOR_DELETION,
            CourseEnrollmentGradeHistoryOperation.CANCELLED_DELETION,
            CourseEnrollmentGradeHistoryOperation.DELETED_PUBLISHED);
  }

  @Test
  void unknownGradeHistoryIsRejected() {
    final var f = fixture();

    assertThatThrownBy(
            () ->
                tx(
                    () ->
                        grades.history(
                            f.institutionId(),
                            f.enrollmentA(),
                            UUID.randomUUID(),
                            PageRequest.of(0, 50))))
        .isInstanceOf(EnrollmentValidationException.class);
  }

  @Test
  void outOfRangeHistoryPageOnDeletedGradeReturnsEmptyPage() {
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
          final var current = grades.listForManagement(f.institutionId(), f.enrollmentA()).get(0);
          grades.delete(
              f.institutionId(), f.enrollmentA(), current.id(), current.version(), f.teacher());
          return null;
        });

    final var firstPage =
        tx(
            () ->
                grades.history(f.institutionId(), f.enrollmentA(), gradeId, PageRequest.of(0, 50)));
    assertThat(firstPage.getTotalElements()).isEqualTo(2);
    assertThat(firstPage.getContent()).isNotEmpty();

    final var outOfRange =
        tx(
            () ->
                grades.history(
                    f.institutionId(), f.enrollmentA(), gradeId, PageRequest.of(50, 50)));
    assertThat(outOfRange.getContent()).isEmpty();
    assertThat(outOfRange.getTotalElements()).isEqualTo(2);
    assertThat(outOfRange.getTotalPages()).isEqualTo(1);
  }

  @Test
  void historyOfGradeFromAnotherEnrollmentIsRejected() {
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

    assertThatThrownBy(
            () ->
                tx(
                    () ->
                        grades.history(
                            f.institutionId(), f.enrollmentB(), gradeId, PageRequest.of(0, 50))))
        .isInstanceOf(EnrollmentValidationException.class)
        .hasMessage(GradeMessages.GRADE_NOT_FOUND);
  }

  @Test
  void historyWithForeignInstitutionIsRejected() {
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

    assertThatThrownBy(
            () ->
                tx(
                    () ->
                        grades.history(
                            UUID.randomUUID(), f.enrollmentA(), gradeId, PageRequest.of(0, 50))))
        .isInstanceOf(EnrollmentValidationException.class)
        .hasMessage(EnrollmentMessages.COURSE_ENROLLMENT_NOT_FOUND);
  }

  @Test
  void historyWithEqualChangedAtPagesDeterministically() {
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

    final var syntheticIds =
        tx(
            () -> {
              final Instant changedAt = Instant.now().plusSeconds(3600);
              final Institution institution = em.find(Institution.class, f.institutionId());
              final CourseEnrollment enrollment = em.find(CourseEnrollment.class, f.enrollmentA());
              final CourseEnrollmentGradeHistory first =
                  CourseEnrollmentGradeHistory.create(
                      institution,
                      enrollment,
                      gradeId,
                      CourseEnrollmentGradeHistoryOperation.UPDATED,
                      "Parcial 1",
                      new BigDecimal("6"),
                      "Parcial 1",
                      new BigDecimal("7"),
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      f.teacher(),
                      changedAt);
              final CourseEnrollmentGradeHistory second =
                  CourseEnrollmentGradeHistory.create(
                      institution,
                      enrollment,
                      gradeId,
                      CourseEnrollmentGradeHistoryOperation.UPDATED,
                      "Parcial 1",
                      new BigDecimal("7"),
                      "Parcial 1",
                      new BigDecimal("8"),
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      f.teacher(),
                      changedAt);

              em.persist(first);
              em.persist(second);
              em.flush();

              return List.of(first.getId(), second.getId());
            });

    // PostgreSQL sorts uuid byte-wise; for UUIDv7 that matches Java's natural order, so reversing
    // it reproduces the changed_at tie-break (course_enrollment_grade_history_id DESC).
    final var expectedIds = syntheticIds.stream().sorted(Comparator.reverseOrder()).toList();

    final var page0 =
        tx(() -> grades.history(f.institutionId(), f.enrollmentA(), gradeId, PageRequest.of(0, 1)));
    final var page1 =
        tx(() -> grades.history(f.institutionId(), f.enrollmentA(), gradeId, PageRequest.of(1, 1)));
    final var page2 =
        tx(() -> grades.history(f.institutionId(), f.enrollmentA(), gradeId, PageRequest.of(2, 1)));

    assertThat(page0.getTotalElements()).isEqualTo(3);
    assertThat(page0.getContent().stream().map(event -> event.historyId()).toList())
        .containsExactly(expectedIds.get(0));
    assertThat(page1.getContent().stream().map(event -> event.historyId()).toList())
        .containsExactly(expectedIds.get(1));
    assertThat(page2.getContent().get(0).operation())
        .isEqualTo(CourseEnrollmentGradeHistoryOperation.CREATED);
  }

  @Test
  void noOpUpdateOnPendingDeletionIsRejectedWithoutNewEvent() {
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

    tx(
        () -> {
          final var current = grades.listForManagement(f.institutionId(), f.enrollmentA()).get(0);
          grades.delete(
              f.institutionId(), f.enrollmentA(), current.id(), current.version(), f.teacher());
          return null;
        });

    assertThatThrownBy(
            () ->
                tx(
                    () -> {
                      final var current =
                          grades.listForManagement(f.institutionId(), f.enrollmentA()).get(0);
                      grades.update(
                          f.institutionId(),
                          f.enrollmentA(),
                          current.id(),
                          "Parcial 1",
                          new BigDecimal("7"),
                          current.version(),
                          f.teacher());
                      return null;
                    }))
        .isInstanceOf(EnrollmentValidationException.class)
        .hasMessage(GradeMessages.GRADE_PENDING_DELETION_IMMUTABLE);

    final var history =
        tx(
            () ->
                grades.history(f.institutionId(), f.enrollmentA(), gradeId, PageRequest.of(0, 50)));
    assertThat(history.getTotalElements()).isEqualTo(3);
    assertThat(history.getContent().get(0).operation())
        .isEqualTo(CourseEnrollmentGradeHistoryOperation.MARKED_FOR_DELETION);
  }

  @Test
  void effectiveUpdateOnPendingDeletionIsRejected() {
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

    tx(
        () -> {
          final var current = grades.listForManagement(f.institutionId(), f.enrollmentA()).get(0);
          grades.delete(
              f.institutionId(), f.enrollmentA(), current.id(), current.version(), f.teacher());
          return null;
        });

    assertThatThrownBy(
            () ->
                tx(
                    () -> {
                      final var current =
                          grades.listForManagement(f.institutionId(), f.enrollmentA()).get(0);
                      grades.update(
                          f.institutionId(),
                          f.enrollmentA(),
                          current.id(),
                          "Parcial 1",
                          new BigDecimal("9"),
                          current.version(),
                          f.teacher());
                      return null;
                    }))
        .isInstanceOf(EnrollmentValidationException.class)
        .hasMessage(GradeMessages.GRADE_PENDING_DELETION_IMMUTABLE);

    final var history =
        tx(
            () ->
                grades.history(f.institutionId(), f.enrollmentA(), gradeId, PageRequest.of(0, 50)));
    assertThat(history.getTotalElements()).isEqualTo(3);
    assertThat(history.getContent().get(0).operation())
        .isEqualTo(CourseEnrollmentGradeHistoryOperation.MARKED_FOR_DELETION);
  }

  @Test
  void cancelDeletionThenEditIsAllowed() {
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

    tx(
        () -> {
          final var current = grades.listForManagement(f.institutionId(), f.enrollmentA()).get(0);
          grades.delete(
              f.institutionId(), f.enrollmentA(), current.id(), current.version(), f.teacher());
          return null;
        });

    tx(
        () -> {
          final var current = grades.listForManagement(f.institutionId(), f.enrollmentA()).get(0);
          grades.cancelDeletion(f.institutionId(), f.enrollmentA(), current.id(), f.teacher());
          return null;
        });

    tx(
        () -> {
          final var current = grades.listForManagement(f.institutionId(), f.enrollmentA()).get(0);
          grades.update(
              f.institutionId(),
              f.enrollmentA(),
              current.id(),
              "Parcial 1",
              new BigDecimal("9"),
              current.version(),
              f.teacher());
          return null;
        });

    final var history =
        tx(
            () ->
                grades.history(f.institutionId(), f.enrollmentA(), gradeId, PageRequest.of(0, 50)));
    assertThat(history.getTotalElements()).isEqualTo(5);
    assertThat(history.getContent().get(0).operation())
        .isEqualTo(CourseEnrollmentGradeHistoryOperation.UPDATED);
    assertThat(history.getContent().get(0).valueAfter()).isEqualByComparingTo("9");
    assertThat(
            tx(() -> grades.listForManagement(f.institutionId(), f.enrollmentA())).get(0).value())
        .isEqualByComparingTo("9");
  }

  @Test
  void archivedReflectsLatestEventPerGrade() {
    final var f = fixture();

    final var gradeA =
        tx(
            () ->
                grades
                    .create(
                        f.institutionId(),
                        f.enrollmentA(),
                        "Prac A",
                        new BigDecimal("7"),
                        f.teacher())
                    .id());
    final var gradeB =
        tx(
            () ->
                grades
                    .create(
                        f.institutionId(),
                        f.enrollmentA(),
                        "Prac B",
                        new BigDecimal("7"),
                        f.teacher())
                    .id());
    final var gradeC =
        tx(
            () ->
                grades
                    .create(
                        f.institutionId(),
                        f.enrollmentA(),
                        "Prac C",
                        new BigDecimal("8"),
                        f.teacher())
                    .id());
    final var gradeD =
        tx(
            () ->
                grades
                    .create(
                        f.institutionId(),
                        f.enrollmentA(),
                        "Prac D",
                        new BigDecimal("6"),
                        f.teacher())
                    .id());

    tx(
        () -> {
          final var current = currentOf(f, gradeA);
          grades.update(
              f.institutionId(),
              f.enrollmentA(),
              gradeA,
              "Prac A",
              new BigDecimal("8"),
              current.version(),
              f.teacher());
          return null;
        });

    tx(
        () -> {
          final var current = currentOf(f, gradeA);
          grades.update(
              f.institutionId(),
              f.enrollmentA(),
              gradeA,
              "Prac A",
              new BigDecimal("9"),
              current.version(),
              f.teacher());
          return null;
        });

    tx(
        () -> {
          final var current = currentOf(f, gradeA);
          grades.delete(f.institutionId(), f.enrollmentA(), gradeA, current.version(), f.teacher());
          return null;
        });

    tx(
        () -> {
          grades.publishClass(f.institutionId(), f.classId(), f.teacher());
          return null;
        });

    tx(
        () -> {
          final var current = currentOf(f, gradeB);
          grades.delete(f.institutionId(), f.enrollmentA(), gradeB, current.version(), f.teacher());
          return null;
        });

    tx(
        () -> {
          grades.cancelDeletion(f.institutionId(), f.enrollmentA(), gradeB, f.teacher());
          return null;
        });

    tx(
        () -> {
          final var current = currentOf(f, gradeC);
          grades.delete(f.institutionId(), f.enrollmentA(), gradeC, current.version(), f.teacher());
          return null;
        });

    tx(
        () -> {
          grades.publishClass(f.institutionId(), f.classId(), f.teacher());
          return null;
        });

    final var archived = tx(() -> grades.archived(f.institutionId(), f.enrollmentA()));

    assertThat(archived).hasSize(2);
    assertThat(archived.get(0).gradeId()).isEqualTo(gradeC);
    assertThat(archived.get(0).lastEvaluation()).isEqualTo("Prac C");
    assertThat(archived.get(0).lastValue()).isEqualByComparingTo("8");
    assertThat(archived.get(0).deletionType())
        .isEqualTo(CourseEnrollmentGradeHistoryOperation.DELETED_PUBLISHED);
    assertThat(archived.get(1).gradeId()).isEqualTo(gradeA);
    assertThat(archived.get(1).lastEvaluation()).isEqualTo("Prac A");
    assertThat(archived.get(1).lastValue()).isEqualByComparingTo("9");
    assertThat(archived.get(1).deletionType())
        .isEqualTo(CourseEnrollmentGradeHistoryOperation.DELETED_DRAFT);
    assertThat(
            tx(() -> grades.listForManagement(f.institutionId(), f.enrollmentA())).stream()
                .map(grade -> grade.id())
                .toList())
        .containsExactlyInAnyOrder(gradeB, gradeD);
  }

  private CourseEnrollmentGradeResponse currentOf(final Fixture f, final UUID gradeId) {
    return grades.listForManagement(f.institutionId(), f.enrollmentA()).stream()
        .filter(grade -> grade.id().equals(gradeId))
        .findFirst()
        .orElseThrow();
  }

  private Fixture fixture() {
    return tx(
        () -> {
          final Institution institution =
              InstitutionalTestData.createInstitution(em, "grade-history-" + UUID.randomUUID());
          roles.provision(institution);

          final var path = persist(TrainingPath.create(institution, "Trayecto", null));
          final var plan =
              persist(StudyPlan.create(institution, path, "Plan", LocalDate.of(2026, 1, 1), null));
          plan.activate();
          final var space =
              persist(
                  AcademicSpace.create(
                      institution,
                      "Lenguaje",
                      null,
                      ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceType.SUBJECT,
                      AcademicSpaceFormat.GRUPAL,
                      false));
          final var placement =
              persist(
                  StudyPlanSpace.create(
                      institution,
                      plan,
                      space,
                      null,
                      RequirementType.REQUIRED,
                      1,
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
          final var course = persist(Course.create(institution, placement, year, null));
          final var courseClass = persist(CourseClass.create(institution, course, 1));

          final Person teacherPerson =
              persist(InstitutionalTestData.person(institution, "20000001"));
          final Person personA = persist(InstitutionalTestData.person(institution, "20000002"));
          final Person personB = persist(InstitutionalTestData.person(institution, "20000003"));

          final var enrollmentA = persist(enrollment(institution, personA, course, courseClass));
          final var enrollmentB = persist(enrollment(institution, personB, course, courseClass));

          return new Fixture(
              institution.getId(),
              courseClass.getId(),
              teacherPerson.getId(),
              enrollmentA.getId(),
              enrollmentB.getId());
        });
  }

  private CourseEnrollment enrollment(
      final Institution institution,
      final Person person,
      final Course course,
      final CourseClass courseClass) {
    final var student =
        persist(
            Student.builder()
                .institution(institution)
                .person(person)
                .fileNumber("F-" + UUID.randomUUID())
                .enrollmentDate(LocalDate.of(2026, 1, 1))
                .build());

    return CourseEnrollment.create(
        institution,
        student,
        course,
        courseClass,
        CourseEnrollmentSource.MANUAL,
        null,
        Instant.now(Clock.systemUTC()));
  }

  private <T> T persist(final T entity) {
    em.persist(entity);
    return entity;
  }

  private <T> T tx(final Supplier<T> action) {
    return new TransactionTemplate(transactions).execute(status -> action.get());
  }

  private record Fixture(
      UUID institutionId, UUID classId, UUID teacher, UUID enrollmentA, UUID enrollmentB) {}
}
