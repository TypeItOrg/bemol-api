package ar.edu.utn.frvm.typeit.boero_api.enrollment;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.ScopedResourceNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.ScopedAuthorizationService;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentPeriod;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentPeriodStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.InvalidEnrollmentApplicationStateException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.MissingRejectionReasonException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentApplicationRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.RejectEnrollmentApplicationRequest;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.ApproveEnrollmentApplicationUseCase;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.EnrollmentAdmissionHistory;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.EnrollmentApplicationCourseApprovalService;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.EnrollmentApplicationPeriodService;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.EnrollmentApplicationResponseFactory;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.EnrollmentDocumentAudit;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.EnrollmentDocumentAuthorization;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.EnrollmentDocumentRequirementsService;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.EnrollmentInstitutionLock;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.RejectEnrollmentApplicationUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Student;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.StudentRepository;
import ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData;
import ar.edu.utn.frvm.typeit.boero_api.support.IntegrationTest;
import ar.edu.utn.frvm.typeit.boero_api.support.JpaAuditingTestConfig;
import ar.edu.utn.frvm.typeit.boero_api.support.PostgresTestDatabase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.flyway.locations=classpath:db/migration",
      "spring.flyway.sql-migration-prefix=",
      "spring.jpa.hibernate.ddl-auto=validate"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@IntegrationTest
@Import({
  JpaAuditingTestConfig.class,
  EnrollmentInstitutionLock.class,
  AcademicAccessGuard.class,
  EnrollmentApplicationResponseFactory.class,
  EnrollmentDocumentRequirementsService.class,
  EnrollmentDocumentAudit.class,
  EnrollmentAdmissionHistory.class,
  ApproveEnrollmentApplicationUseCase.class,
  RejectEnrollmentApplicationUseCase.class
})
class EnrollmentResolutionPostgresIntegrationTest {

  static final PostgresTestDatabase POSTGRES =
      new PostgresTestDatabase("postgres:18-alpine", "enrollment_resolution");

  @MockitoBean private EnrollmentApplicationCourseApprovalService applicationCourseApprovalService;

  @MockitoBean(name = "scopedAuthorization")
  private ScopedAuthorizationService authorization;

  @MockitoBean private EnrollmentApplicationPeriodService periods;
  @MockitoBean private EnrollmentDocumentAuthorization documentAuthorization;

  @Autowired private EntityManager entityManager;
  @Autowired private EnrollmentApplicationRepository enrollmentApplicationRepository;
  @Autowired private StudentRepository studentRepository;
  @Autowired private ApproveEnrollmentApplicationUseCase approveUseCase;
  @Autowired private RejectEnrollmentApplicationUseCase rejectUseCase;

  private Institution institution;
  private Person person;
  private Person constraintPerson;
  private UUID studyPlanId;
  private UUID trainingPathId;
  private UUID academicYearId;
  private EnrollmentPeriod enrollmentPeriod;
  private UUID resolverPersonId;

  @DynamicPropertySource
  static void databaseProperties(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @BeforeEach
  void seedFixture() {
    institution = InstitutionalTestData.createInstitution(entityManager, "cons-enrollment");
    person = InstitutionalTestData.person(institution, "30000001");
    InstitutionalTestData.persist(entityManager, person);
    constraintPerson = InstitutionalTestData.person(institution, "30000002");
    InstitutionalTestData.persist(entityManager, constraintPerson);

    final var resolver = InstitutionalTestData.person(institution, "20000001");
    InstitutionalTestData.persist(entityManager, resolver);
    resolverPersonId = resolver.getId();

    final var trainingPath =
        TrainingPath.create(institution, "Formación Básica", "Programa de ingreso");
    InstitutionalTestData.persist(entityManager, trainingPath);
    trainingPathId = trainingPath.getId();
    final var studyPlan =
        StudyPlan.create(
            institution,
            trainingPath,
            "Profesorado de Música",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2030, 12, 31));
    InstitutionalTestData.persist(entityManager, studyPlan);
    studyPlanId = studyPlan.getId();

    final int year = Year.now().getValue() + 1;
    final var academicYear =
        AcademicYear.create(
            institution,
            year,
            LocalDate.of(year, 2, 1),
            LocalDate.of(year, 12, 15),
            LocalDate.of(year, 1, 1));
    InstitutionalTestData.persist(entityManager, academicYear);
    academicYearId = academicYear.getId();

    enrollmentPeriod =
        EnrollmentPeriod.builder()
            .institution(institution)
            .academicYear(academicYear)
            .name("Inscripción " + year)
            .startDate(LocalDateTime.of(year, 2, 1, 0, 0).toInstant(ZoneOffset.UTC))
            .endDate(LocalDateTime.of(year, 12, 15, 23, 59).toInstant(ZoneOffset.UTC))
            .status(EnrollmentPeriodStatus.OPEN)
            .build();
    InstitutionalTestData.persist(entityManager, enrollmentPeriod);

    entityManager.flush();
    entityManager.clear();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @DisplayName(
      "Should approve a submitted application, create the student and block a second approval")
  void approve_createsOrPreservesTheStudentAndCannotResolveTwice(boolean alreadyStudent) {
    UUID existingStudentId = null;
    if (alreadyStudent) {
      Student existing =
          Student.builder()
              .institution(entityManager.find(Institution.class, institution.getId()))
              .person(entityManager.find(Person.class, person.getId()))
              .fileNumber("2020-00001")
              .enrollmentDate(LocalDate.of(2020, 3, 1))
              .build();
      InstitutionalTestData.persist(entityManager, existing);
      existingStudentId = existing.getId();
    }
    final var applicationId = startAndSubmit();

    final var approved =
        approveUseCase.execute(institution.getId(), applicationId, resolverPersonId);

    assertThat(approved.status()).isEqualTo(EnrollmentApplicationStatus.APPROVED);
    entityManager.flush();
    entityManager.clear();
    assertThat(
            studentRepository.existsByInstitution_IdAndPerson_Id(
                institution.getId(), person.getId()))
        .isTrue();
    assertThat(studentRepository.countByInstitution_Id(institution.getId())).isEqualTo(1);
    var student =
        studentRepository
            .findByInstitution_IdAndPerson_Id(institution.getId(), person.getId())
            .orElseThrow();
    if (alreadyStudent) {
      assertThat(student.getId()).isEqualTo(existingStudentId);
      assertThat(student.getFileNumber()).isEqualTo("2020-00001");
      assertThat(student.getEnrollmentDate()).isEqualTo(LocalDate.of(2020, 3, 1));
    } else {
      assertThat(student.getFileNumber()).matches("\\d{4}-\\d{5}");
      assertThat(student.getEnrollmentDate()).isNotNull();
    }

    assertThatThrownBy(
            () -> approveUseCase.execute(institution.getId(), applicationId, resolverPersonId))
        .isInstanceOf(InvalidEnrollmentApplicationStateException.class);

    final var reloaded = enrollmentApplicationRepository.findById(applicationId).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(EnrollmentApplicationStatus.APPROVED);
    assertThat(reloaded.getResolvedAt()).isNotNull();
    assertThat(reloaded.getResolvedByPersonId()).isEqualTo(resolverPersonId);
    assertThat(studentRepository.countByInstitution_Id(institution.getId())).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "Should reject a submitted application storing the reason and block a second resolution")
  void reject_storesReasonAndCannotResolveTwice() {
    final var applicationId = startAndSubmit();

    final var rejected =
        rejectUseCase.execute(
            institution.getId(),
            applicationId,
            new RejectEnrollmentApplicationRequest("Documentación incompleta"),
            resolverPersonId);

    assertThat(rejected.status()).isEqualTo(EnrollmentApplicationStatus.REJECTED);
    assertThat(rejected.rejectionReason()).isEqualTo("Documentación incompleta");
    assertThat(rejected.resolvedAt()).isNotNull();

    assertThatThrownBy(
            () ->
                rejectUseCase.execute(
                    institution.getId(),
                    applicationId,
                    new RejectEnrollmentApplicationRequest("Otro motivo"),
                    resolverPersonId))
        .isInstanceOf(InvalidEnrollmentApplicationStateException.class);

    final var reloaded = enrollmentApplicationRepository.findById(applicationId).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(EnrollmentApplicationStatus.REJECTED);
    assertThat(reloaded.getRejectionReason()).isEqualTo("Documentación incompleta");
    assertThat(reloaded.getResolvedAt()).isNotNull();
    assertThat(reloaded.getResolvedByPersonId()).isEqualTo(resolverPersonId);
  }

  @Test
  @DisplayName("Should enforce non-blank rejection reason constraint through JPA pre-check")
  void reject_rejectsBlankReason() {
    final var applicationId = startAndSubmit();

    assertThatThrownBy(
            () ->
                rejectUseCase.execute(
                    institution.getId(),
                    applicationId,
                    new RejectEnrollmentApplicationRequest(" "),
                    resolverPersonId))
        .isInstanceOf(MissingRejectionReasonException.class);
    entityManager.flush();
    entityManager.clear();
    var preserved = enrollmentApplicationRepository.findById(applicationId).orElseThrow();
    assertThat(preserved.getStatus()).isEqualTo(EnrollmentApplicationStatus.SUBMITTED);
    assertThat(preserved.getRejectionReason()).isNull();
    assertThat(preserved.getResolvedAt()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"approve", "reject"})
  void resolutionCannotFindMissingOrForeignApplications(String operation) {
    UUID applicationId = startAndSubmit();
    Institution foreign =
        InstitutionalTestData.createInstitution(entityManager, "foreign-resolution");
    entityManager.flush();
    UUID missing = UUID.fromString("00000000-0000-0000-0000-000000000001");

    assertThatThrownBy(() -> resolve(operation, institution.getId(), missing))
        .isExactlyInstanceOf(ScopedResourceNotFoundException.class);
    assertThatThrownBy(() -> resolve(operation, foreign.getId(), applicationId))
        .isExactlyInstanceOf(ScopedResourceNotFoundException.class);

    entityManager.flush();
    entityManager.clear();
    var preserved = enrollmentApplicationRepository.findById(applicationId).orElseThrow();
    assertThat(preserved.getStatus()).isEqualTo(EnrollmentApplicationStatus.SUBMITTED);
    assertThat(preserved.getRejectionReason()).isNull();
    assertThat(preserved.getResolvedAt()).isNull();
    assertThat(studentRepository.countByInstitution_Id(institution.getId())).isZero();
    assertThat(studentRepository.countByInstitution_Id(foreign.getId())).isZero();
  }

  private void resolve(String operation, UUID tenantId, UUID applicationId) {
    if (operation.equals("approve")) {
      approveUseCase.execute(tenantId, applicationId, resolverPersonId);
    } else {
      rejectUseCase.execute(
          tenantId,
          applicationId,
          new RejectEnrollmentApplicationRequest("Documentación incompleta"),
          resolverPersonId);
    }
  }

  @Test
  @DisplayName(
      "Should enforce PostgreSQL consistency check: rejection_reason cannot be set when status is not REJECTED")
  void database_enforcesResolutionConsistencyCheck_cannotHaveRejectionReasonWhenNotRejected() {
    final var applicationId = startAndSubmit();

    assertThatThrownBy(
            () ->
                entityManager
                    .createNativeQuery(
                        """
                        INSERT INTO enrollment_applications (
                          enrollment_application_id, institution_id, applicant_person_id,
                          study_plan_id, training_path_id, academic_year_id, enrollment_period_id, status,
                          rejection_reason, created_at, updated_at
                        ) VALUES (:id, :institutionId, :personId, :studyPlanId, :trainingPathId, :academicYearId,
                                 :periodId, 'SUBMITTED', 'Reason without rejected status', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                    .setParameter("id", UUID.randomUUID())
                    .setParameter("institutionId", institution.getId())
                    .setParameter("personId", constraintPerson.getId())
                    .setParameter("studyPlanId", studyPlanId)
                    .setParameter("trainingPathId", trainingPathId)
                    .setParameter("academicYearId", academicYearId)
                    .setParameter(
                        "periodId",
                        requireNonNull(
                                enrollmentApplicationRepository
                                    .findById(applicationId)
                                    .orElseThrow()
                                    .getEnrollmentPeriod())
                            .getId())
                    .executeUpdate())
        .isInstanceOf(PersistenceException.class)
        .hasMessageContaining("enrollment_applications_resolution_consistency_check");
  }

  @Test
  @DisplayName(
      "Should enforce PostgreSQL consistency check: rejection_reason must be set when status is REJECTED")
  void database_enforcesResolutionConsistencyCheck_cannotHaveNullRejectionReasonWhenRejected() {
    final var applicationId = startAndSubmit();

    assertThatThrownBy(
            () ->
                entityManager
                    .createNativeQuery(
                        """
                        INSERT INTO enrollment_applications (
                          enrollment_application_id, institution_id, applicant_person_id,
                          study_plan_id, training_path_id, academic_year_id, enrollment_period_id, status,
                          rejection_reason, created_at, updated_at
                        ) VALUES (:id, :institutionId, :personId, :studyPlanId, :trainingPathId, :academicYearId,
                                 :periodId, 'REJECTED', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                    .setParameter("id", UUID.randomUUID())
                    .setParameter("institutionId", institution.getId())
                    .setParameter("personId", constraintPerson.getId())
                    .setParameter("studyPlanId", studyPlanId)
                    .setParameter("trainingPathId", trainingPathId)
                    .setParameter("academicYearId", academicYearId)
                    .setParameter(
                        "periodId",
                        requireNonNull(
                                enrollmentApplicationRepository
                                    .findById(applicationId)
                                    .orElseThrow()
                                    .getEnrollmentPeriod())
                            .getId())
                    .executeUpdate())
        .isInstanceOf(PersistenceException.class)
        .hasMessageContaining("enrollment_applications_resolution_consistency_check");
  }

  private UUID startAndSubmit() {
    final var application =
        EnrollmentApplication.builder()
            .institution(institution)
            .applicantPerson(person)
            .studyPlan(entityManager.find(StudyPlan.class, studyPlanId))
            .academicYear(entityManager.find(AcademicYear.class, academicYearId))
            .enrollmentPeriod(entityManager.find(EnrollmentPeriod.class, enrollmentPeriod.getId()))
            .status(EnrollmentApplicationStatus.SUBMITTED)
            .build();
    enrollmentApplicationRepository.saveAndFlush(application);
    return application.getId();
  }
}
