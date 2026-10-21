package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.DatabaseMigrationTestSupport;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.ApprovalMode;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.RequirementType;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplicationSpace;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPeriod;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPeriodOffering;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentPeriodStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.AcademicSpaceSelectionDto;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentApplicationResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentDraftData;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.ResponsibleDto;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.UpdateEnrollmentDraftRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Ejercita EnrollmentApplicationService contra un Postgres real migrado con Flyway (ddl-auto
 * validate), no contra repositorios mockeados: es la única forma de detectar tanto una violación
 * real de NOT NULL en el autoguardado parcial como el filtro real de institución en el listado
 * (mockear la Specification con any() nunca ejecuta el predicate).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@IntegrationTest
class EnrollmentApplicationPostgresIntegrationTest extends DatabaseMigrationTestSupport {

  @Autowired private EnrollmentApplicationService service;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void authenticateManagementOperations() {
    authenticatePlatformAdministrator();
  }

  @Test
  @Transactional
  @DisplayName(
      "Should autosave a partial responsible section without violating NOT NULL constraints")
  void updateDraft_partialResponsible_doesNotViolateConstraints() {
    EnrollmentApplication application = createDraftApplication();

    UpdateEnrollmentDraftRequest request =
        UpdateEnrollmentDraftRequest.builder()
            .data(
                EnrollmentDraftData.builder()
                    .responsible(ResponsibleDto.builder().fullName("Tutor Incompleto").build())
                    .build())
            .build();

    EnrollmentApplicationResponse response =
        service.updateDraft(application.getApplicantPerson().getId(), application.getId(), request);
    entityManager.flush();

    assertThat(requireNonNull(requireNonNull(response.getData()).getResponsible()).getFullName())
        .isEqualTo("Tutor Incompleto");
    assertThat(
            requireNonNull(requireNonNull(response.getData()).getResponsible()).getDocumentNumber())
        .isNull();
  }

  @Test
  @Transactional
  @DisplayName("Should isolate the application listing by institution using the real Specification")
  void listApplications_isolatesByRealInstitutionFilter() {
    EnrollmentApplication applicationA = createDraftApplication();
    EnrollmentApplication applicationB = createDraftApplication();

    PaginatedResponse<EnrollmentApplicationResponse> response =
        service.listApplications(
            applicationA.getInstitution().getId(), null, null, null, PageRequest.of(0, 10));

    assertThat(response.items())
        .extracting(
            mappedEnrollmentApplicationResponse ->
                mappedEnrollmentApplicationResponse.getApplicationId())
        .containsExactly(applicationA.getId())
        .doesNotContain(applicationB.getId());
  }

  @Test
  @Transactional
  @DisplayName(
      "Should enforce the unique constraint on (enrollment_application_id, study_plan_space_id)")
  void enrollmentApplicationSpace_rejectsDuplicatePairAtDatabaseLevel() {
    EnrollmentApplication application = createDraftApplication();
    StudyPlanSpace studyPlanSpace =
        createStudyPlanSpace(
            application.getInstitution(), requireNonNull(application.getStudyPlan()), 1);

    entityManager.persist(
        EnrollmentApplicationSpace.builder()
            .enrollmentApplication(application)
            .studyPlanSpace(studyPlanSpace)
            .build());
    entityManager.flush();

    entityManager.persist(
        EnrollmentApplicationSpace.builder()
            .enrollmentApplication(application)
            .studyPlanSpace(studyPlanSpace)
            .build());

    assertThatThrownBy(entityManager::flush).isInstanceOf(RuntimeException.class);
  }

  @Test
  @Transactional
  @DisplayName("Should physically delete a discarded selected space, not just detach it")
  void updateDraft_replacingSelection_deletesDiscardedSpaceRow() {
    EnrollmentApplication application = createDraftApplication();
    StudyPlanSpace firstSpace =
        createStudyPlanSpace(
            application.getInstitution(), requireNonNull(application.getStudyPlan()), 1);
    StudyPlanSpace secondSpace =
        createStudyPlanSpace(
            application.getInstitution(), requireNonNull(application.getStudyPlan()), 2);

    service.updateDraft(
        application.getApplicantPerson().getId(),
        application.getId(),
        UpdateEnrollmentDraftRequest.builder()
            .data(
                EnrollmentDraftData.builder()
                    .academicSpaceSelection(
                        new AcademicSpaceSelectionDto(List.of(firstSpace.getId())))
                    .build())
            .build());
    entityManager.flush();

    service.updateDraft(
        application.getApplicantPerson().getId(),
        application.getId(),
        UpdateEnrollmentDraftRequest.builder()
            .data(
                EnrollmentDraftData.builder()
                    .academicSpaceSelection(
                        new AcademicSpaceSelectionDto(List.of(secondSpace.getId())))
                    .build())
            .build());
    entityManager.flush();
    entityManager.clear();

    Long remainingForFirstSpace =
        entityManager
            .createQuery(
                "SELECT COUNT(s) FROM EnrollmentApplicationSpace s "
                    + "WHERE s.enrollmentApplication.id = :applicationId "
                    + "AND s.studyPlanSpace.id = :spaceId",
                Long.class)
            .setParameter("applicationId", application.getId())
            .setParameter("spaceId", firstSpace.getId())
            .getSingleResult();

    assertThat(remainingForFirstSpace).isZero();
  }

  @Test
  @Transactional
  @DisplayName("Should reject submission when no study plan space was selected")
  void submitApplication_withoutSelectedSpaces_throwsValidationException() {
    EnrollmentApplication application = createDraftApplication();

    assertThatThrownBy(
            () ->
                service.submitApplication(
                    application.getApplicantPerson().getId(), application.getId()))
        .isInstanceOf(EnrollmentValidationException.class);
  }

  private StudyPlanSpace createStudyPlanSpace(
      Institution institution, StudyPlan studyPlan, int displayOrder) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    AcademicSpace academicSpace =
        InstitutionalTestData.persist(
            entityManager,
            AcademicSpace.create(
                institution,
                "Espacio " + suffix,
                "",
                AcademicSpaceType.SUBJECT,
                AcademicSpaceFormat.INDIVIDUAL));
    StudyPlanSpace studyPlanSpace =
        InstitutionalTestData.persist(
            entityManager,
            StudyPlanSpace.create(
                institution,
                studyPlan,
                academicSpace,
                null,
                RequirementType.REQUIRED,
                displayOrder,
                ApprovalMode.FINAL_EXAM));
    entityManager.flush();
    return studyPlanSpace;
  }

  private EnrollmentApplication createDraftApplication() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    Institution institution =
        InstitutionalTestData.createInstitution(entityManager, "enrollment-" + suffix);
    Person person =
        InstitutionalTestData.persist(
            entityManager, InstitutionalTestData.person(institution, docNumber()));

    TrainingPath trainingPath =
        InstitutionalTestData.persist(
            entityManager, TrainingPath.create(institution, "Instrumento " + suffix, null));
    StudyPlan studyPlan =
        InstitutionalTestData.persist(
            entityManager,
            StudyPlan.create(institution, trainingPath, "Piano " + suffix, LocalDate.now(), null));
    studyPlan.activate();
    AcademicYear academicYear =
        InstitutionalTestData.persist(
            entityManager,
            AcademicYear.create(
                institution,
                2026,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 1, 1)));
    academicYear.transitionTo(AcademicYearStatus.ACTIVE);

    EnrollmentPeriod period =
        InstitutionalTestData.persist(
            entityManager,
            EnrollmentPeriod.builder()
                .institution(institution)
                .academicYear(academicYear)
                .name("Periodo " + suffix)
                .startDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .endDate(Instant.now().plus(30, ChronoUnit.DAYS))
                .status(EnrollmentPeriodStatus.OPEN)
                .build());

    entityManager.flush();
    period.markScopeConfigured();
    final var offering = EnrollmentPeriodOffering.create(period, studyPlan);
    offering.selectLevels(List.of(), true);
    period.getOfferings().add(offering);
    entityManager.persist(offering);

    EnrollmentApplication application =
        InstitutionalTestData.persist(
            entityManager,
            EnrollmentApplication.builder()
                .institution(institution)
                .applicantPerson(person)
                .studyPlan(studyPlan)
                .academicYear(academicYear)
                .enrollmentPeriod(period)
                .status(EnrollmentApplicationStatus.DRAFT)
                .build());

    entityManager.flush();
    return application;
  }

  private String docNumber() {
    return String.format("%08d", Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000));
  }
}
