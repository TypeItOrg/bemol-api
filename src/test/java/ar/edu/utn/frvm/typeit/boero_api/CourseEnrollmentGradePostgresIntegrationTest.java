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
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollmentGrade;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentGradePublicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentSource;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
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
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class CourseEnrollmentGradePostgresIntegrationTest extends DatabaseMigrationTestSupport {

  @Autowired EntityManager em;
  @Autowired PlatformTransactionManager transactions;
  @Autowired CourseEnrollmentGradeService grades;
  @Autowired InstitutionRoleProvisioner roles;

  @BeforeEach
  void authenticate() {
    authenticatePlatformAdministrator();
  }

  @Test
  void draftIsInvisibleToStudentUntilPublish() {
    final var f = fixture();

    final var gradeId =
        tx(
            () ->
                grades
                    .create(
                        f.institutionId(),
                        f.enrollmentA(),
                        "Parcial 1",
                        new BigDecimal("7.50"),
                        f.teacher())
                    .id());

    tx(
        () -> {
          final var grade = em.find(CourseEnrollmentGrade.class, gradeId);
          assertThat(grade.publicationStatus())
              .isEqualTo(CourseEnrollmentGradePublicationStatus.DRAFT);
          return null;
        });

    assertThat(tx(() -> grades.listPublished(f.institutionId(), f.enrollmentA()))).isEmpty();
    assertThat(tx(() -> grades.listForManagement(f.institutionId(), f.enrollmentA()))).hasSize(1);

    tx(
        () -> {
          grades.publishClass(f.institutionId(), f.classId(), f.teacher());
          return null;
        });

    final var published = tx(() -> grades.listPublished(f.institutionId(), f.enrollmentA()));
    assertThat(published).hasSize(1);
    assertThat(published.get(0).evaluation()).isEqualTo("Parcial 1");
    assertThat(published.get(0).value()).isEqualByComparingTo("7.50");
  }

  @Test
  void editPublishedKeepsOldValueUntilRepublish() {
    final var f = fixture();

    tx(
        () -> {
          grades.create(
              f.institutionId(), f.enrollmentA(), "Parcial 1", new BigDecimal("6"), f.teacher());
          grades.publishClass(f.institutionId(), f.classId(), f.teacher());
          return null;
        });

    assertThat(tx(() -> grades.listPublished(f.institutionId(), f.enrollmentA())).get(0).value())
        .isEqualByComparingTo("6");

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

    assertThat(tx(() -> grades.listPublished(f.institutionId(), f.enrollmentA())).get(0).value())
        .isEqualByComparingTo("6");
    assertThat(
            tx(() -> grades.listForManagement(f.institutionId(), f.enrollmentA()))
                .get(0)
                .publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.PENDING_CHANGES);

    tx(
        () -> {
          grades.publishClass(f.institutionId(), f.classId(), f.teacher());
          return null;
        });

    assertThat(tx(() -> grades.listPublished(f.institutionId(), f.enrollmentA())).get(0).value())
        .isEqualByComparingTo("8");
  }

  @Test
  void deletePublishedKeepsVisibleUntilPublish() {
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

    assertThat(tx(() -> grades.listPublished(f.institutionId(), f.enrollmentA()))).hasSize(1);
    assertThat(
            tx(() -> grades.listForManagement(f.institutionId(), f.enrollmentA()))
                .get(0)
                .publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.PENDING_DELETION);

    tx(
        () -> {
          grades.publishClass(f.institutionId(), f.classId(), f.teacher());
          return null;
        });

    assertThat(tx(() -> grades.listPublished(f.institutionId(), f.enrollmentA()))).isEmpty();
    assertThat(tx(() -> grades.listForManagement(f.institutionId(), f.enrollmentA()))).isEmpty();
  }

  @Test
  void publishHandlesNewModifiedDeletedAtomically() {
    final var f = fixture();

    tx(
        () -> {
          grades.create(
              f.institutionId(), f.enrollmentA(), "Parcial 1", new BigDecimal("6"), f.teacher());
          grades.create(
              f.institutionId(), f.enrollmentB(), "Parcial 1", new BigDecimal("6"), f.teacher());
          grades.create(
              f.institutionId(), f.enrollmentC(), "Parcial 1", new BigDecimal("6"), f.teacher());
          grades.create(
              f.institutionId(), f.enrollmentD(), "Parcial 1", new BigDecimal("6"), f.teacher());
          grades.publishClass(f.institutionId(), f.classId(), f.teacher());
          return null;
        });

    tx(
        () -> {
          final var b = grades.listForManagement(f.institutionId(), f.enrollmentB()).get(0);
          grades.update(
              f.institutionId(),
              f.enrollmentB(),
              b.id(),
              "Parcial 1",
              new BigDecimal("9"),
              b.version(),
              f.teacher());

          final var c = grades.listForManagement(f.institutionId(), f.enrollmentC()).get(0);
          grades.delete(f.institutionId(), f.enrollmentC(), c.id(), c.version(), f.teacher());

          grades.create(
              f.institutionId(), f.enrollmentA(), "Parcial 2", new BigDecimal("8"), f.teacher());
          return null;
        });

    final var summary = tx(() -> grades.pendingSummary(f.institutionId(), f.classId()));
    assertThat(summary.pendingChanges()).isEqualTo(3);
    assertThat(summary.affectedStudents()).isEqualTo(3);

    final var result = tx(() -> grades.publishClass(f.institutionId(), f.classId(), f.teacher()));
    assertThat(result.publishedGrades()).isEqualTo(2);
    assertThat(result.deletedGrades()).isEqualTo(1);
    assertThat(result.affectedStudents()).isEqualTo(3);

    assertThat(tx(() -> grades.listPublished(f.institutionId(), f.enrollmentA()))).hasSize(2);
    assertThat(tx(() -> grades.listPublished(f.institutionId(), f.enrollmentB())).get(0).value())
        .isEqualByComparingTo("9");
    assertThat(tx(() -> grades.listPublished(f.institutionId(), f.enrollmentC()))).isEmpty();
    assertThat(tx(() -> grades.listPublished(f.institutionId(), f.enrollmentD()))).hasSize(1);
  }

  @Test
  void duplicateEvaluationIsRejectedIgnoringCaseAndSpaces() {
    final var f = fixture();

    tx(
        () -> {
          grades.create(
              f.institutionId(), f.enrollmentA(), "Parcial 1", new BigDecimal("7"), f.teacher());
          return null;
        });

    assertThatThrownBy(
            () ->
                tx(
                    () ->
                        grades.create(
                            f.institutionId(),
                            f.enrollmentA(),
                            "  PARCIAL 1 ",
                            new BigDecimal("8"),
                            f.teacher())))
        .isInstanceOf(EnrollmentValidationException.class);
  }

  @Test
  void publishWithoutChangesIsRejectedAndLeavesDataUntouched() {
    final var f = fixture();

    tx(
        () -> {
          grades.create(
              f.institutionId(), f.enrollmentA(), "Parcial 1", new BigDecimal("7"), f.teacher());
          grades.publishClass(f.institutionId(), f.classId(), f.teacher());
          return null;
        });

    final var before =
        tx(() -> grades.listForManagement(f.institutionId(), f.enrollmentA()).get(0));

    assertThatThrownBy(
            () -> tx(() -> grades.publishClass(f.institutionId(), f.classId(), f.teacher())))
        .isInstanceOf(EnrollmentValidationException.class);

    final var after = tx(() -> grades.listForManagement(f.institutionId(), f.enrollmentA()).get(0));
    assertThat(after.publishedAt()).isEqualTo(before.publishedAt());
    assertThat(after.version()).isEqualTo(before.version());
  }

  @Test
  void concurrentDuplicateEvaluationsAreRejected() throws Exception {
    final var f = fixture();
    final var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    final var latch = new java.util.concurrent.CountDownLatch(1);

    try {
      final var first =
          executor.submit(
              () -> {
                latch.await();
                return tx(
                    () ->
                        grades.create(
                            f.institutionId(),
                            f.enrollmentA(),
                            "Parcial 1",
                            new BigDecimal("7"),
                            f.teacher()));
              });
      final var second =
          executor.submit(
              () -> {
                latch.await();
                return tx(
                    () ->
                        grades.create(
                            f.institutionId(),
                            f.enrollmentA(),
                            "  parcial 1 ",
                            new BigDecimal("8"),
                            f.teacher()));
              });

      latch.countDown();

      int successes = 0;
      int failures = 0;

      for (final var future : java.util.List.of(first, second)) {
        try {
          future.get(30, java.util.concurrent.TimeUnit.SECONDS);
          successes++;
        } catch (final java.util.concurrent.ExecutionException failure) {
          failures++;
        }
      }

      assertThat(successes).isEqualTo(1);
      assertThat(failures).isEqualTo(1);
      assertThat(tx(() -> grades.listForManagement(f.institutionId(), f.enrollmentA()))).hasSize(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void studentSeesOnlyPublishedWithoutAudit() {
    final var f = fixture();
    final var studentPerson =
        tx(
            () -> {
              final var enrollment = em.find(CourseEnrollment.class, f.enrollmentA());
              return enrollment.getStudent().getPerson().getId();
            });

    tx(
        () -> {
          grades.create(
              f.institutionId(), f.enrollmentA(), "Parcial 1", new BigDecimal("7"), f.teacher());
          return null;
        });

    assertThat(tx(() -> grades.listOwnPublished(f.institutionId(), f.enrollmentA(), studentPerson)))
        .isEmpty();

    tx(
        () -> {
          grades.publishClass(f.institutionId(), f.classId(), f.teacher());
          return null;
        });

    final var studentView =
        tx(() -> grades.listOwnPublished(f.institutionId(), f.enrollmentA(), studentPerson));

    assertThat(studentView).hasSize(1);
    assertThat(studentView.get(0).evaluation()).isEqualTo("Parcial 1");

    final var otherPerson = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                tx(() -> grades.listOwnPublished(f.institutionId(), f.enrollmentA(), otherPerson)))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  void pendingClassesGroupsByClass() {
    final var f = fixture();

    assertThat(tx(() -> grades.pendingClasses(f.institutionId()))).isEmpty();

    tx(
        () -> {
          grades.create(
              f.institutionId(), f.enrollmentA(), "Parcial 1", new BigDecimal("7"), f.teacher());
          grades.create(
              f.institutionId(), f.enrollmentB(), "Parcial 1", new BigDecimal("8"), f.teacher());
          return null;
        });

    final var pending = tx(() -> grades.pendingClasses(f.institutionId()));
    assertThat(pending).hasSize(1);
    assertThat(pending.get(0).classId()).isEqualTo(f.classId());
    assertThat(pending.get(0).pendingChanges()).isEqualTo(2);
    assertThat(pending.get(0).affectedStudents()).isEqualTo(2);

    tx(
        () -> {
          grades.publishClass(f.institutionId(), f.classId(), f.teacher());
          return null;
        });

    assertThat(tx(() -> grades.pendingClasses(f.institutionId()))).isEmpty();
  }

  private Fixture fixture() {
    return tx(
        () -> {
          final Institution institution =
              InstitutionalTestData.createInstitution(em, "grades-" + UUID.randomUUID());
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
              persist(InstitutionalTestData.person(institution, "10000001"));
          final Person personA = persist(InstitutionalTestData.person(institution, "10000002"));
          final Person personB = persist(InstitutionalTestData.person(institution, "10000003"));
          final Person personC = persist(InstitutionalTestData.person(institution, "10000004"));
          final Person personD = persist(InstitutionalTestData.person(institution, "10000005"));

          final var enrollmentA = persist(enrollment(institution, personA, course, courseClass));
          final var enrollmentB = persist(enrollment(institution, personB, course, courseClass));
          final var enrollmentC = persist(enrollment(institution, personC, course, courseClass));
          final var enrollmentD = persist(enrollment(institution, personD, course, courseClass));

          return new Fixture(
              institution.getId(),
              courseClass.getId(),
              teacherPerson.getId(),
              enrollmentA.getId(),
              enrollmentB.getId(),
              enrollmentC.getId(),
              enrollmentD.getId());
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
      UUID institutionId,
      UUID classId,
      UUID teacher,
      UUID enrollmentA,
      UUID enrollmentB,
      UUID enrollmentC,
      UUID enrollmentD) {}
}
