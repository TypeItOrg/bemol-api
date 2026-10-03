package ar.edu.utn.frvm.typeit.boero_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.PermissionAccess;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.ScopedAuthorizationService;
import ar.edu.utn.frvm.typeit.boero_api.search.SearchEntityType;
import ar.edu.utn.frvm.typeit.boero_api.search.SearchService;
import ar.edu.utn.frvm.typeit.boero_api.search.SearchSummaryResponse;
import ar.edu.utn.frvm.typeit.boero_api.support.IntegrationTest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@IntegrationTest
class SearchMigrationIntegrationTest extends DatabaseMigrationTestSupport {

  @Autowired private SearchService searchService;

  @MockitoBean(name = "scopedAuthorization")
  private ScopedAuthorizationService authorization;

  @BeforeEach
  void allowInstitutionalSearch() {
    when(authorization.access(any())).thenReturn(PermissionAccess.institution());
  }

  @Test
  @DisplayName("Should search accent-insensitively with unordered prefixes")
  void shouldSearchWithUnorderedPrefixes() {
    final var result = searchService.platformSummary("musica boe", 5);

    assertThat(result.groups())
        .filteredOn(group -> group.entityType() == SearchEntityType.INSTITUTION)
        .singleElement()
        .satisfies(
            group ->
                assertThat(group.items())
                    .singleElement()
                    .satisfies(item -> assertThat(item.title()).contains("Música Felipe Boero")));
  }

  @Test
  @DisplayName("Should exclude deleted academic resources from contextual search")
  void shouldExcludeDeletedAcademicResourcesFromContextualSearch() {
    final UUID institutionId = fixtures.firstInstitutionId();
    final String searchTerm = "zzsoftdelete" + System.nanoTime();
    final int academicYear = 2891;
    final UUID deletedYearId = UUID.randomUUID();
    final UUID currentYearId = UUID.randomUUID();
    final UUID deletedPathId = UUID.randomUUID();
    final UUID currentPathId = UUID.randomUUID();
    final UUID orphanedPathId = UUID.randomUUID();
    final UUID deletedPlanId = UUID.randomUUID();
    final UUID currentPlanId = UUID.randomUUID();
    final UUID orphanedPlanId = UUID.randomUUID();
    final UUID deletedSpaceId = UUID.randomUUID();
    final UUID currentSpaceId = UUID.randomUUID();
    final UUID deletedInstrumentId = UUID.randomUUID();
    final UUID currentInstrumentId = UUID.randomUUID();
    final UUID deletedShiftId = UUID.randomUUID();
    final UUID currentShiftId = UUID.randomUUID();
    try {
      fixtures.insertAcademicYearWithStatus(deletedYearId, institutionId, academicYear, "PLANNED");
      jdbcTemplate.update(
          "UPDATE academic_years SET deleted_at = CURRENT_TIMESTAMP WHERE academic_year_id = ?",
          deletedYearId);
      fixtures.insertAcademicYearWithStatus(currentYearId, institutionId, academicYear, "PLANNED");

      fixtures.insertTrainingPath(deletedPathId, institutionId, searchTerm);
      jdbcTemplate.update(
          "UPDATE training_paths SET active = false, deleted_at = CURRENT_TIMESTAMP WHERE training_path_id = ?",
          deletedPathId);
      fixtures.insertTrainingPath(currentPathId, institutionId, searchTerm);

      fixtures.insertStudyPlan(deletedPlanId, institutionId, currentPathId, searchTerm);
      jdbcTemplate.update(
          "UPDATE study_plans SET deleted_at = CURRENT_TIMESTAMP WHERE study_plan_id = ?",
          deletedPlanId);
      fixtures.insertStudyPlan(currentPlanId, institutionId, currentPathId, searchTerm);

      fixtures.insertTrainingPath(orphanedPathId, institutionId, searchTerm + " parent");
      fixtures.insertStudyPlan(
          orphanedPlanId, institutionId, orphanedPathId, searchTerm + " inherited");
      jdbcTemplate.update(
          "UPDATE training_paths SET active = false, deleted_at = CURRENT_TIMESTAMP WHERE training_path_id = ?",
          orphanedPathId);

      fixtures.insertAcademicSpace(deletedSpaceId, institutionId, searchTerm);
      jdbcTemplate.update(
          "UPDATE academic_spaces SET active = false, deleted_at = CURRENT_TIMESTAMP WHERE academic_space_id = ?",
          deletedSpaceId);
      fixtures.insertAcademicSpace(currentSpaceId, institutionId, searchTerm);

      fixtures.insertInstrument(deletedInstrumentId, institutionId, searchTerm);
      jdbcTemplate.update(
          "UPDATE instruments SET deleted_at = CURRENT_TIMESTAMP WHERE instrument_id = ?",
          deletedInstrumentId);
      fixtures.insertInstrument(currentInstrumentId, institutionId, searchTerm);

      fixtures.insertShift(deletedShiftId, institutionId, searchTerm, false);
      jdbcTemplate.update(
          "UPDATE shifts SET deleted_at = CURRENT_TIMESTAMP WHERE shift_id = ?", deletedShiftId);
      fixtures.insertShift(currentShiftId, institutionId, searchTerm, true);

      assertSearchContainsOnly(
          searchService.platformSummary(String.valueOf(academicYear), 5),
          SearchEntityType.ACADEMIC_YEAR,
          currentYearId);
      assertSearchContainsOnly(
          searchService.platformSummary(searchTerm, 5),
          SearchEntityType.TRAINING_PATH,
          currentPathId);
      assertSearchContainsOnly(
          searchService.platformSummary(searchTerm, 5), SearchEntityType.STUDY_PLAN, currentPlanId);
      assertSearchContainsOnly(
          searchService.platformSummary(searchTerm, 5),
          SearchEntityType.ACADEMIC_SPACE,
          currentSpaceId);
      assertSearchContainsOnly(
          searchService.platformSummary(searchTerm, 5),
          SearchEntityType.INSTRUMENT,
          currentInstrumentId);
      assertSearchContainsOnly(
          searchService.platformSummary(searchTerm, 5), SearchEntityType.SHIFT, currentShiftId);

      final Set<PermissionCode> permissions =
          Set.of(
              PermissionCode.ACADEMIC_YEAR_READ,
              PermissionCode.TRAINING_PATH_READ,
              PermissionCode.STUDY_PLAN_READ,
              PermissionCode.ACADEMIC_SPACE_READ,
              PermissionCode.INSTRUMENT_READ,
              PermissionCode.SHIFT_READ);
      assertSearchContainsOnly(
          searchService.institutionalSummary(institutionId, null, searchTerm, 5, permissions),
          SearchEntityType.TRAINING_PATH,
          currentPathId);
      assertSearchContainsOnly(
          searchService.institutionalSummary(institutionId, null, searchTerm, 5, permissions),
          SearchEntityType.STUDY_PLAN,
          currentPlanId);
      assertSearchContainsOnly(
          searchService.institutionalSummary(
              institutionId, null, String.valueOf(academicYear), 5, permissions),
          SearchEntityType.ACADEMIC_YEAR,
          currentYearId);
      assertSearchContainsOnly(
          searchService.institutionalSummary(institutionId, null, searchTerm, 5, permissions),
          SearchEntityType.ACADEMIC_SPACE,
          currentSpaceId);
      assertSearchContainsOnly(
          searchService.institutionalSummary(institutionId, null, searchTerm, 5, permissions),
          SearchEntityType.INSTRUMENT,
          currentInstrumentId);
      assertSearchContainsOnly(
          searchService.institutionalSummary(institutionId, null, searchTerm, 5, permissions),
          SearchEntityType.SHIFT,
          currentShiftId);

      assertThat(
              searchService
                  .platformPage(SearchEntityType.ACADEMIC_YEAR, String.valueOf(academicYear), 0, 5)
                  .items())
          .extracting(item -> item.id())
          .containsExactly(currentYearId);
      assertThat(
              searchService.platformPage(SearchEntityType.TRAINING_PATH, searchTerm, 0, 5).items())
          .extracting(item -> item.id())
          .containsExactly(currentPathId);
      assertThat(searchService.platformPage(SearchEntityType.STUDY_PLAN, searchTerm, 0, 5).items())
          .extracting(item -> item.id())
          .containsExactly(currentPlanId);
      assertThat(
              searchService.platformPage(SearchEntityType.ACADEMIC_SPACE, searchTerm, 0, 5).items())
          .extracting(item -> item.id())
          .containsExactly(currentSpaceId);
      assertThat(searchService.platformPage(SearchEntityType.INSTRUMENT, searchTerm, 0, 5).items())
          .extracting(item -> item.id())
          .containsExactly(currentInstrumentId);
      assertThat(searchService.platformPage(SearchEntityType.SHIFT, searchTerm, 0, 5).items())
          .extracting(item -> item.id())
          .containsExactly(currentShiftId);
    } finally {
      jdbcTemplate.update(
          "DELETE FROM study_plans WHERE study_plan_id IN (?, ?, ?)",
          deletedPlanId,
          currentPlanId,
          orphanedPlanId);
      jdbcTemplate.update(
          "DELETE FROM training_paths WHERE training_path_id IN (?, ?, ?)",
          deletedPathId,
          currentPathId,
          orphanedPathId);
      jdbcTemplate.update(
          "DELETE FROM academic_years WHERE academic_year_id IN (?, ?)",
          deletedYearId,
          currentYearId);
      jdbcTemplate.update(
          "DELETE FROM academic_spaces WHERE academic_space_id IN (?, ?)",
          deletedSpaceId,
          currentSpaceId);
      jdbcTemplate.update(
          "DELETE FROM instruments WHERE instrument_id IN (?, ?)",
          deletedInstrumentId,
          currentInstrumentId);
      jdbcTemplate.update(
          "DELETE FROM shifts WHERE shift_id IN (?, ?)", deletedShiftId, currentShiftId);
    }
  }

  @Test
  @DisplayName("Should search only the caller's active guardian dependents")
  void shouldSearchOnlyTheCallersGuardianDependents() {
    final UUID institutionId = fixtures.firstInstitutionId();
    final UUID tutorAId = UUID.randomUUID();
    final UUID tutorBId = UUID.randomUUID();
    final UUID dependentAId = UUID.randomUUID();
    final UUID dependentBId = UUID.randomUUID();
    final UUID deletedDependentId = UUID.randomUUID();
    final UUID pendingDependentId = UUID.randomUUID();
    final String dependentADocument = fixtures.randomDocumentNumber();
    final String surname = "Zzdependiente" + System.nanoTime();
    final Set<PermissionCode> permissions = Set.of(PermissionCode.GUARDIAN_DEPENDENT_MANAGE);
    try {
      for (final UUID tutorId : List.of(tutorAId, tutorBId)) {
        fixtures.insertPerson(tutorId, institutionId, fixtures.randomDocumentNumber(), false);
      }
      insertDependent(dependentAId, institutionId, dependentADocument, surname, false);
      insertDependent(dependentBId, institutionId, fixtures.randomDocumentNumber(), surname, false);
      insertDependent(
          deletedDependentId, institutionId, fixtures.randomDocumentNumber(), surname, true);
      insertDependent(
          pendingDependentId, institutionId, fixtures.randomDocumentNumber(), surname, false);
      insertGuardianLink(institutionId, tutorAId, dependentAId, "ACTIVE");
      insertGuardianLink(institutionId, tutorAId, deletedDependentId, "ACTIVE");
      insertGuardianLink(institutionId, tutorAId, pendingDependentId, "PENDING");
      insertGuardianLink(institutionId, tutorBId, dependentBId, "ACTIVE");

      assertSearchContainsOnly(
          searchService.institutionalSummary(institutionId, tutorAId, surname, 5, permissions),
          SearchEntityType.GUARDIAN_DEPENDENT,
          dependentAId);
      assertSearchContainsOnly(
          searchService.institutionalSummary(
              institutionId, tutorAId, dependentADocument, 5, permissions),
          SearchEntityType.GUARDIAN_DEPENDENT,
          dependentAId);
      assertSearchContainsOnly(
          searchService.institutionalSummary(institutionId, tutorBId, surname, 5, permissions),
          SearchEntityType.GUARDIAN_DEPENDENT,
          dependentBId);
      assertThat(
              searchService
                  .institutionalSummary(institutionId, null, surname, 5, permissions)
                  .groups())
          .isEmpty();
    } finally {
      final List<UUID> personIds =
          List.of(
              tutorAId,
              tutorBId,
              dependentAId,
              dependentBId,
              deletedDependentId,
              pendingDependentId);
      for (final UUID personId : personIds) {
        jdbcTemplate.update(
            "DELETE FROM person_guardians WHERE tutor_person_id = ? OR dependent_person_id = ?",
            personId,
            personId);
        jdbcTemplate.update("DELETE FROM people WHERE person_id = ?", personId);
      }
    }
  }

  private void insertDependent(
      final UUID personId,
      final UUID institutionId,
      final String documentNumber,
      final String lastName,
      final boolean deleted) {
    jdbcTemplate.update(
        """
        INSERT INTO people (
          person_id, institution_id, document_number, first_name, last_name,
          email, created_at, updated_at, deleted
        ) VALUES (?, ?, ?, 'Mateo', ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
        """,
        personId,
        institutionId,
        documentNumber,
        lastName,
        deleted);
  }

  private void insertGuardianLink(
      final UUID institutionId, final UUID tutorId, final UUID dependentId, final String status) {
    jdbcTemplate.update(
        """
        INSERT INTO person_guardians (
          person_guardian_id, institution_id, tutor_person_id, dependent_person_id,
          relationship, is_primary_contact, status, created_at, updated_at
        ) VALUES (?, ?, ?, ?, 'MOTHER', false, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        UUID.randomUUID(),
        institutionId,
        tutorId,
        dependentId,
        status);
  }

  private void assertSearchContainsOnly(
      final SearchSummaryResponse summary,
      final SearchEntityType entityType,
      final UUID expectedId) {
    assertThat(summary.groups())
        .filteredOn(group -> group.entityType() == entityType)
        .singleElement()
        .satisfies(
            group ->
                assertThat(group.items())
                    .extracting(item -> item.id())
                    .containsExactly(expectedId));
  }
}
