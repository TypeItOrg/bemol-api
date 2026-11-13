package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.DatabaseMigrationTestSupport;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AssignPersonSystemRoleUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionRoleProvisioner;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPeriod;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPeriodOffering;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentPeriodStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
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
@Testcontainers
@IntegrationTest
class EnrollmentApplicationPostgresIntegrationTest extends DatabaseMigrationTestSupport {

  @Autowired private EnrollmentApplicationService service;
  @Autowired private EntityManager entityManager;
  @Autowired private InstitutionRoleProvisioner roles;
  @Autowired private AssignPersonSystemRoleUseCase assignRole;

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
            .expectedRevision(0L)
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
  @DisplayName("Should reject submission without selected courses and preserve the draft")
  void submitApplication_withoutSelectedCourses_preservesDraft() {
    EnrollmentApplication application = createDraftApplication();

    assertThatThrownBy(
            () ->
                service.submitApplication(
                    application.getApplicantPerson().getId(), application.getId(), 0L))
        .isInstanceOfSatisfying(
            EnrollmentValidationException.class,
            error -> assertThat(error.fieldErrors()).containsKey("courses"));

    entityManager.flush();
    entityManager.clear();
    assertThat(entityManager.find(EnrollmentApplication.class, application.getId()).getStatus())
        .isEqualTo(EnrollmentApplicationStatus.DRAFT);
  }

  private EnrollmentApplication createDraftApplication() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    Institution institution =
        InstitutionalTestData.createInstitution(entityManager, "enrollment-" + suffix);
    roles.provision(institution);
    Person person =
        InstitutionalTestData.persist(
            entityManager, InstitutionalTestData.person(institution, docNumber()));
    assignRole.execute(person, SystemRoleCode.APPLICANT, false);

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
                .trainingPathId(trainingPath.getId())
                .trainingPath(trainingPath)
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
