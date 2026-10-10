package ar.edu.utn.frvm.typeit.boero_api.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicLevel;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicSpace;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Instrument;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Prerequisite;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.ApprovalMode;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.RequiredCondition;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.RequirementStage;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.RequirementType;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.AcademicLevelRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.AcademicSpaceRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.AcademicYearRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.InstrumentRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.PrerequisiteRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.StudyPlanRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.StudyPlanSpaceRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.GetAcademicSpaceUsageUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.GetStudyPlanCurriculumUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.GetStudyPlanSpaceUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.ListAcademicLevelsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.ListAcademicSpacesUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.ListAcademicYearsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.ListInstrumentsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.ListPrerequisitesUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.ListStudyPlanSpacesUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.ListStudyPlansUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.ListTrainingPathStudyPlansUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.ListTrainingPathsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.PermissionAccess;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.ScopedAuthorizationService;
import ar.edu.utn.frvm.typeit.boero_api.config.JpaAuditingConfig;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData;
import ar.edu.utn.frvm.typeit.boero_api.support.IntegrationTest;
import ar.edu.utn.frvm.typeit.boero_api.support.PostgresTestDatabase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.flyway.locations=classpath:db/migration,classpath:db/dev",
      "spring.flyway.sql-migration-prefix=",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.properties.hibernate.generate_statistics=true",
      "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@IntegrationTest
@Import({
  JpaAuditingConfig.class,
  AcademicAccessGuard.class,
  GetStudyPlanCurriculumUseCase.class,
  GetStudyPlanSpaceUseCase.class,
  GetAcademicSpaceUsageUseCase.class,
  ListAcademicLevelsUseCase.class,
  ListAcademicSpacesUseCase.class,
  ListAcademicYearsUseCase.class,
  ListInstrumentsUseCase.class,
  ListPrerequisitesUseCase.class,
  ListStudyPlanSpacesUseCase.class,
  ListStudyPlansUseCase.class,
  ListTrainingPathStudyPlansUseCase.class,
  ListTrainingPathsUseCase.class
})
class AcademicQueryPerformanceIntegrationTest {

  private static final int COLLECTION_SIZE = 21;
  private static final int PAGE_SIZE = 20;

  static final PostgresTestDatabase POSTGRES =
      new PostgresTestDatabase("postgres:18-alpine", "academic_query_performance");

  @Autowired private EntityManager entityManager;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private AcademicYearRepository academicYearRepository;
  @Autowired private TrainingPathRepository trainingPathRepository;
  @Autowired private StudyPlanRepository studyPlanRepository;
  @Autowired private AcademicLevelRepository academicLevelRepository;
  @Autowired private AcademicSpaceRepository academicSpaceRepository;
  @Autowired private InstrumentRepository instrumentRepository;
  @Autowired private StudyPlanSpaceRepository studyPlanSpaceRepository;
  @Autowired private PrerequisiteRepository prerequisiteRepository;
  @Autowired private ListAcademicYearsUseCase listAcademicYearsUseCase;
  @Autowired private ListTrainingPathsUseCase listTrainingPathsUseCase;
  @Autowired private ListStudyPlansUseCase listStudyPlansUseCase;
  @Autowired private ListTrainingPathStudyPlansUseCase listTrainingPathStudyPlansUseCase;
  @Autowired private ListAcademicLevelsUseCase listAcademicLevelsUseCase;
  @Autowired private ListAcademicSpacesUseCase listAcademicSpacesUseCase;
  @Autowired private ListInstrumentsUseCase listInstrumentsUseCase;
  @Autowired private ListStudyPlanSpacesUseCase listStudyPlanSpacesUseCase;
  @Autowired private ListPrerequisitesUseCase listPrerequisitesUseCase;
  @Autowired private GetStudyPlanSpaceUseCase getStudyPlanSpaceUseCase;
  @Autowired private GetStudyPlanCurriculumUseCase getStudyPlanCurriculumUseCase;
  @Autowired private GetAcademicSpaceUsageUseCase getAcademicSpaceUsageUseCase;

  @MockitoBean(name = "scopedAuthorization")
  private ScopedAuthorizationService scopedAuthorization;

  private Statistics statistics;
  private UUID institutionId;
  private String institutionName;
  private UUID trainingPathId;
  private UUID studyPlanId;
  private UUID targetStudyPlanSpaceId;
  private UUID academicSpaceId;

  @DynamicPropertySource
  static void databaseProperties(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @BeforeEach
  void setUp() {
    when(scopedAuthorization.managementAccess(any())).thenReturn(PermissionAccess.institution());
    when(scopedAuthorization.unrestricted(anyString())).thenReturn(true);
    when(scopedAuthorization.paths(anyString())).thenReturn(java.util.Set.of(new UUID(0, 0)));
    statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    final var institution =
        InstitutionalTestData.createInstitution(
            entityManager, "academic-query-" + UUID.randomUUID());
    institutionId = institution.getId();
    institutionName = institution.getName();

    persistAcademicYears(institution);
    final var trainingPaths = persistTrainingPaths(institution);
    final var primaryTrainingPath = trainingPaths.getFirst();
    trainingPathId = primaryTrainingPath.getId();

    final var studyPlans = persistStudyPlans(institution, primaryTrainingPath);
    final var primaryStudyPlan = studyPlans.getFirst();
    studyPlanId = primaryStudyPlan.getId();

    final var levels = persistLevels(primaryStudyPlan);
    final var academicSpaces = persistAcademicSpaces(institution);
    academicSpaceId = academicSpaces.getFirst().getId();
    persistInstruments(institution);
    final var planSpaces = persistPlanSpaces(institution, primaryStudyPlan, levels, academicSpaces);
    targetStudyPlanSpaceId = planSpaces.getFirst().getId();
    persistPrerequisites(primaryStudyPlan, planSpaces);

    entityManager.flush();
    entityManager.clear();
    statistics.clear();
  }

  @Test
  @DisplayName("Should list global academic years by institution name without N+1")
  void shouldListGlobalAcademicYearsByInstitutionNameWithoutNPlusOne() {
    final var response =
        listAcademicYearsUseCase.execute(
            null,
            institutionName,
            null,
            null,
            null,
            null,
            null,
            false,
            PageRequest.of(0, PAGE_SIZE));

    assertThat(response.items()).hasSize(PAGE_SIZE);
    assertThat(response.items())
        .allSatisfy(year -> assertThat(year.institutionName()).isEqualTo(institutionName));
    assertPreparedStatementCount(2);
  }

  @Test
  @DisplayName("Should list every global academic collection by institution name")
  void shouldListEveryGlobalAcademicCollectionByInstitutionName() {
    assertThat(
            listTrainingPathsUseCase
                .execute(null, institutionName, null, PageRequest.of(0, PAGE_SIZE))
                .items())
        .hasSize(PAGE_SIZE);
    entityManager.clear();

    assertThat(
            listStudyPlansUseCase
                .execute(null, institutionName, null, PageRequest.of(0, PAGE_SIZE))
                .items())
        .hasSize(PAGE_SIZE);
    entityManager.clear();

    assertThat(
            listAcademicSpacesUseCase
                .execute(null, institutionName, null, null, PageRequest.of(0, PAGE_SIZE))
                .items())
        .hasSize(PAGE_SIZE);
    entityManager.clear();

    assertThat(
            listInstrumentsUseCase
                .execute(null, institutionName, null, PageRequest.of(0, PAGE_SIZE))
                .items())
        .hasSize(PAGE_SIZE);
  }

  @Test
  @DisplayName("Should list academic years with a constant query count")
  void shouldListAcademicYearsWithoutNPlusOne() {
    final var response =
        listAcademicYearsUseCase.execute(
            institutionId, null, null, null, null, null, PageRequest.of(0, PAGE_SIZE));

    assertThat(response.items()).hasSize(PAGE_SIZE);
    assertPreparedStatementCount(2);
  }

  @Test
  @DisplayName("Should combine academic year filters with a constant query count")
  void shouldFilterAcademicYearsWithoutNPlusOne() {
    final var response =
        listAcademicYearsUseCase.execute(
            institutionId,
            null,
            AcademicYearStatus.PLANNED,
            2000,
            LocalDate.of(2000, 3, 1),
            LocalDate.of(2000, 12, 1),
            PageRequest.of(0, PAGE_SIZE));

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().getFirst().year()).isEqualTo(2000);
    assertPreparedStatementCount(1);
  }

  @ParameterizedTest(name = "{0} -> {1} result(s)")
  @CsvSource({
    "2000,1",
    "03/2000,1",
    "2000-03-01,1",
    "12/2000,1",
    "2000-12-01,1",
    "planificado,21",
    "PLANNED,21",
    "%,0",
    "_,0"
  })
  @DisplayName("Should search academic years across visible fields without N+1")
  void shouldSearchAcademicYearsAcrossVisibleFields(
      final String search, final int expectedTotalItems) {
    final var response =
        listAcademicYearsUseCase.execute(
            institutionId, search, null, null, null, null, PageRequest.of(0, PAGE_SIZE));

    assertThat(response.totalItems()).isEqualTo(expectedTotalItems);
    assertPreparedStatementCount(expectedTotalItems > PAGE_SIZE ? 2 : 1);
  }

  @Test
  @DisplayName("Should list training paths with a constant query count")
  void shouldListTrainingPathsWithoutNPlusOne() {
    final var response =
        listTrainingPathsUseCase.execute(institutionId, null, null, PageRequest.of(0, PAGE_SIZE));

    assertThat(response.items()).hasSize(PAGE_SIZE);
    assertPreparedStatementCount(2);
  }

  @Test
  @DisplayName("Should sort training paths by name")
  void shouldSortTrainingPathsByName() {
    final var response =
        listTrainingPathsUseCase.execute(
            institutionId, null, null, PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "name")));

    assertThat(response.items())
        .extracting(item -> item.name())
        .containsExactly("Performance Path 9", "Performance Path 8", "Performance Path 7");
  }

  @Test
  @DisplayName("Should list study plans with a constant query count")
  void shouldListStudyPlansWithoutNPlusOne() {
    final var response =
        listStudyPlansUseCase.execute(institutionId, null, null, PageRequest.of(0, PAGE_SIZE));

    assertThat(response.items()).hasSize(PAGE_SIZE);
    assertThat(response.items())
        .allSatisfy(plan -> assertThat(plan.trainingPathName()).isNotBlank());
    assertPreparedStatementCount(2);
  }

  @Test
  @DisplayName("Should filter study plans by validity date with a constant query count")
  void shouldFilterStudyPlansByValidityDateWithoutUntypedNullParameters() {
    final var response =
        listStudyPlansUseCase.execute(
            institutionId,
            null,
            null,
            null,
            LocalDate.of(2100, 6, 1),
            PageRequest.of(0, PAGE_SIZE));

    assertThat(response.items()).hasSize(PAGE_SIZE);
    assertThat(response.items())
        .allSatisfy(
            plan -> {
              assertThat(plan.effectiveFrom()).isEqualTo(LocalDate.of(2100, 1, 1));
              assertThat(plan.effectiveTo()).isEqualTo(LocalDate.of(2100, 12, 31));
            });
    assertPreparedStatementCount(2);
  }

  @Test
  @DisplayName("Should include open-ended study plans in validity date filters")
  void shouldIncludeOpenEndedStudyPlansInValidityDateFilters() {
    final var institution = institutionRepository.findById(institutionId).orElseThrow();
    final var trainingPath = trainingPathRepository.findById(trainingPathId).orElseThrow();
    studyPlanRepository.save(
        StudyPlan.create(
            institution,
            trainingPath,
            "Open-ended Performance Plan",
            LocalDate.of(2100, 1, 1),
            null));
    entityManager.flush();
    entityManager.clear();
    statistics.clear();

    final var response =
        listStudyPlansUseCase.execute(
            institutionId,
            null,
            null,
            null,
            LocalDate.of(2200, 6, 1),
            PageRequest.of(0, PAGE_SIZE));

    assertThat(response.items())
        .singleElement()
        .satisfies(
            plan -> {
              assertThat(plan.name()).isEqualTo("Open-ended Performance Plan");
              assertThat(plan.effectiveTo()).isNull();
            });
    assertPreparedStatementCount(1);
  }

  @Test
  @DisplayName("Should list study plans by training path with a constant query count")
  void shouldListTrainingPathStudyPlansWithoutNPlusOne() {
    final var response =
        listTrainingPathStudyPlansUseCase.execute(
            institutionId, trainingPathId, PageRequest.of(0, PAGE_SIZE));

    assertThat(response.items()).hasSize(PAGE_SIZE);
    assertThat(response.items())
        .allSatisfy(plan -> assertThat(plan.trainingPathName()).isNotBlank());
    assertPreparedStatementCount(2);
  }

  @Test
  @DisplayName("Should list academic levels with a constant query count")
  void shouldListAcademicLevelsWithoutNPlusOne() {
    final var response = listAcademicLevelsUseCase.execute(institutionId, studyPlanId);

    assertThat(response).hasSize(COLLECTION_SIZE);
    // Includes the scoped resource lookup performed by AcademicAccessGuard.
    assertPreparedStatementCount(3);
  }

  @Test
  @DisplayName("Should list academic spaces with a constant query count")
  void shouldListAcademicSpacesWithoutNPlusOne() {
    final var response =
        listAcademicSpacesUseCase.execute(
            institutionId, null, null, null, PageRequest.of(0, PAGE_SIZE));

    assertThat(response.items()).hasSize(PAGE_SIZE);
    assertPreparedStatementCount(2);
  }

  @Test
  @DisplayName("Should load academic space usage without N+1")
  void shouldLoadAcademicSpaceUsageWithoutNPlusOne() {
    final var response =
        getAcademicSpaceUsageUseCase.execute(
            institutionId, academicSpaceId, PageRequest.of(0, PAGE_SIZE));

    assertThat(response.summary().totalPlans()).isEqualTo(1);
    assertThat(response.summary().totalPlacements()).isEqualTo(1);
    assertThat(response.plans().items()).hasSize(1);
    assertThat(response.plans().items().getFirst().placements()).hasSize(1);
    assertThat(response.summary().deactivationBlocked()).isTrue();
    assertPreparedStatementCount(4);
  }

  @Test
  @DisplayName("Should list instruments with a constant query count")
  void shouldListInstrumentsWithoutNPlusOne() {
    final var response =
        listInstrumentsUseCase.execute(institutionId, null, null, PageRequest.of(0, PAGE_SIZE));

    assertThat(response.items()).hasSize(PAGE_SIZE);
    assertPreparedStatementCount(2);
  }

  @Test
  @DisplayName("Should list study plan spaces with a constant query count")
  void shouldListStudyPlanSpacesWithoutNPlusOne() {
    final var response = listStudyPlanSpacesUseCase.execute(institutionId, studyPlanId);

    assertThat(response).hasSize(COLLECTION_SIZE);
    // Includes the scoped resource lookup performed by AcademicAccessGuard.
    assertPreparedStatementCount(4);
  }

  @Test
  @DisplayName("Should list prerequisites with a constant query count")
  void shouldListPrerequisitesWithoutNPlusOne() {
    final var response = listPrerequisitesUseCase.execute(institutionId, targetStudyPlanSpaceId);

    assertThat(response).hasSize(COLLECTION_SIZE - 1);
    // Includes the scoped resource lookup performed by AcademicAccessGuard.
    assertPreparedStatementCount(3);
  }

  @Test
  @DisplayName("Should get a study plan space with a constant query count")
  void shouldGetStudyPlanSpaceWithoutNPlusOne() {
    final var response = getStudyPlanSpaceUseCase.execute(institutionId, targetStudyPlanSpaceId);

    assertThat(response.academicSpaceName()).isNotBlank();
    assertThat(response.academicLevelName()).isNotBlank();
    // Includes the scoped resource lookup performed by AcademicAccessGuard.
    assertPreparedStatementCount(3);
  }

  @Test
  @DisplayName("Should build the curriculum with a constant query count")
  void shouldBuildCurriculumWithoutNPlusOne() {
    final var response = getStudyPlanCurriculumUseCase.execute(institutionId, studyPlanId);

    assertThat(response.studyPlan().trainingPathName()).isNotBlank();
    assertThat(response.levels()).hasSize(COLLECTION_SIZE);
    assertThat(response.prerequisites()).hasSize(COLLECTION_SIZE - 1);
    // Includes the scoped resource lookup performed by AcademicAccessGuard.
    assertPreparedStatementCount(5);
  }

  private void persistAcademicYears(final Institution institution) {
    IntStream.range(0, COLLECTION_SIZE)
        .mapToObj(
            index ->
                AcademicYear.create(
                    institution,
                    2000 + index,
                    LocalDate.of(2000 + index, 3, 1),
                    LocalDate.of(2000 + index, 12, 1),
                    LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"))))
        .forEach(academicYearRepository::save);
  }

  private List<TrainingPath> persistTrainingPaths(final Institution institution) {
    final var paths =
        IntStream.range(0, COLLECTION_SIZE)
            .mapToObj(index -> TrainingPath.create(institution, "Performance Path " + index, null))
            .toList();
    return trainingPathRepository.saveAll(paths);
  }

  private List<StudyPlan> persistStudyPlans(
      final Institution institution, final TrainingPath trainingPath) {
    final var plans =
        IntStream.range(0, COLLECTION_SIZE)
            .mapToObj(
                index ->
                    StudyPlan.create(
                        institution,
                        trainingPath,
                        "Performance Plan " + index,
                        LocalDate.of(2100, 1, 1),
                        LocalDate.of(2100, 12, 31)))
            .toList();
    return studyPlanRepository.saveAll(plans);
  }

  private List<AcademicLevel> persistLevels(final StudyPlan studyPlan) {
    final var levels =
        IntStream.range(0, COLLECTION_SIZE)
            .mapToObj(
                index ->
                    AcademicLevel.create(studyPlan, "Performance Level " + index, index + 1, null))
            .toList();
    return academicLevelRepository.saveAll(levels);
  }

  private List<AcademicSpace> persistAcademicSpaces(final Institution institution) {
    final var spaces =
        IntStream.range(0, COLLECTION_SIZE)
            .mapToObj(
                index ->
                    AcademicSpace.create(
                        institution,
                        "Performance Space " + index,
                        null,
                        AcademicSpaceType.SUBJECT,
                        AcademicSpaceFormat.INDIVIDUAL))
            .toList();
    return academicSpaceRepository.saveAll(spaces);
  }

  private void persistInstruments(final Institution institution) {
    IntStream.range(0, COLLECTION_SIZE)
        .mapToObj(index -> Instrument.create(institution, "Performance Instrument " + index, null))
        .forEach(instrumentRepository::save);
  }

  private List<StudyPlanSpace> persistPlanSpaces(
      final Institution institution,
      final StudyPlan studyPlan,
      final List<AcademicLevel> levels,
      final List<AcademicSpace> academicSpaces) {
    final List<StudyPlanSpace> planSpaces = new ArrayList<>();
    IntStream.range(0, COLLECTION_SIZE)
        .mapToObj(
            index ->
                StudyPlanSpace.create(
                    institution,
                    studyPlan,
                    academicSpaces.get(index),
                    levels.get(index),
                    RequirementType.REQUIRED,
                    index + 1,
                    ApprovalMode.PROMOTION))
        .forEach(planSpaces::add);
    return studyPlanSpaceRepository.saveAll(planSpaces);
  }

  private void persistPrerequisites(
      final StudyPlan studyPlan, final List<StudyPlanSpace> planSpaces) {
    final var target = planSpaces.getFirst();
    IntStream.range(1, COLLECTION_SIZE)
        .mapToObj(
            index ->
                Prerequisite.create(
                    studyPlan,
                    target,
                    planSpaces.get(index),
                    RequirementStage.TO_ENROLL,
                    RequiredCondition.PASSED))
        .forEach(prerequisiteRepository::save);
  }

  private void assertPreparedStatementCount(final long expected) {
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(expected);
  }
}
