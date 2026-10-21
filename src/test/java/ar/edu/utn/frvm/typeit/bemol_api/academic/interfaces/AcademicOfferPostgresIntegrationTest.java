package ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicLevel;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.ApprovalMode;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.RequirementType;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ScopedAuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import ar.edu.utn.frvm.typeit.bemol_api.support.JpaAuditingTestConfig;
import ar.edu.utn.frvm.typeit.bemol_api.support.PostgresTestDatabase;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.flyway.locations=classpath:db/migration,classpath:db/dev",
      "spring.flyway.sql-migration-prefix=",
      "spring.jpa.hibernate.ddl-auto=validate"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@IntegrationTest
@Import(JpaAuditingTestConfig.class)
class AcademicOfferPostgresIntegrationTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 8);
  private static final PostgresTestDatabase DATABASE =
      new PostgresTestDatabase("postgres:18-alpine", "academic_offer_queries");

  @DynamicPropertySource
  static void databaseProperties(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
    registry.add("spring.datasource.username", DATABASE::getUsername);
    registry.add("spring.datasource.password", DATABASE::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @Autowired private EntityManager entityManager;
  @Autowired private StudyPlanRepository studyPlanRepository;
  @Autowired private StudyPlanSpaceRepository studyPlanSpaceRepository;

  @MockitoBean(name = "scopedAuthorization")
  private ScopedAuthorizationService scopedAuthorization;

  private Institution institution;

  @BeforeEach
  void setUp() {
    institution = InstitutionalTestData.createInstitution(entityManager, "academic-offer-test");
  }

  @Test
  @DisplayName("Should expose only active and currently effective plans from active paths")
  void findAvailableOffers_filtersUnavailablePlans() {
    final var available = createPlan("CAVI", "Plan vigente", TODAY.minusYears(1), null, true, true);
    createPlan("Profesorado", "Plan futuro", TODAY.plusDays(1), null, true, true);
    createPlan("CAV Básico", "Plan en borrador", TODAY.minusYears(1), null, true, false);
    createPlan("CAV Avanzado", "Trayecto inactivo", TODAY.minusYears(1), null, false, true);
    entityManager.flush();
    entityManager.clear();

    when(scopedAuthorization.unrestricted("ACADEMIC_OFFER_READ")).thenReturn(true);

    final var result =
        studyPlanRepository.findAvailableOffers(institution.getId(), TODAY, PageRequest.of(0, 20));

    assertThat(result.getContent())
        .extracting(mappedStudyPlan -> mappedStudyPlan.getId())
        .containsExactly(available.getId());
    assertThat(
            studyPlanRepository.findAvailableOfferById(
                institution.getId(), available.getId(), TODAY))
        .isPresent();
  }

  @Test
  @DisplayName("Should exclude inactive academic spaces from an available plan detail")
  void findActiveByStudyPlanIdWithDetails_filtersInactiveSpaces() {
    final var plan = createPlan("CAVI", "Plan vigente", TODAY.minusYears(1), null, true, true);
    final var level = AcademicLevel.create(plan, "Nivel 1", 1, null);
    entityManager.persist(level);
    final var activeSpace = createSpace("Lenguaje Musical", true);
    final var inactiveSpace = createSpace("Materia histórica", false);
    entityManager.persist(
        StudyPlanSpace.create(
            institution,
            plan,
            activeSpace,
            level,
            RequirementType.REQUIRED,
            1,
            ApprovalMode.PROMOTION));
    entityManager.persist(
        StudyPlanSpace.create(
            institution,
            plan,
            inactiveSpace,
            level,
            RequirementType.OPTIONAL,
            2,
            ApprovalMode.FINAL_EXAM));
    entityManager.flush();
    entityManager.clear();

    final var result = studyPlanSpaceRepository.findActiveByStudyPlanIdWithDetails(plan.getId());

    assertThat(result)
        .extracting(space -> space.getAcademicSpace().getName())
        .containsExactly("Lenguaje Musical");
  }

  private StudyPlan createPlan(
      final String pathName,
      final String planName,
      final LocalDate effectiveFrom,
      final @Nullable LocalDate effectiveTo,
      final boolean activePath,
      final boolean activePlan) {
    final var path = TrainingPath.create(institution, pathName, null);
    path.updateStatus(activePath);
    entityManager.persist(path);
    final var plan = StudyPlan.create(institution, path, planName, effectiveFrom, effectiveTo);
    if (activePlan) {
      plan.activate();
    }
    entityManager.persist(plan);
    return plan;
  }

  private AcademicSpace createSpace(final String name, final boolean active) {
    final var space =
        AcademicSpace.create(
            institution, name, null, AcademicSpaceType.SUBJECT, AcademicSpaceFormat.GRUPAL);
    space.updateStatus(active);
    entityManager.persist(space);
    return space;
  }
}
