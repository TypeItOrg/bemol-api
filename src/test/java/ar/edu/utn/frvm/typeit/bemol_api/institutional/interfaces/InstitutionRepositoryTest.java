package ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces;

import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.city;
import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.country;
import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.createInstitution;
import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.createUser;
import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.institution;
import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.persist;
import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.province;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.City;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Country;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Province;
import ar.edu.utn.frvm.typeit.bemol_api.support.JpaAuditingTestConfig;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@Import(JpaAuditingTestConfig.class)
class InstitutionRepositoryTest {

  @Autowired private EntityManager entityManager;
  @Autowired private InstitutionRepository institutionRepository;

  @Test
  @DisplayName("Should list active and inactive institutions with full location loaded")
  void findAllWithLocation_returnsAllInstitutionsWithLocation() {
    createInstitution(entityManager, "boero-active");
    Institution inactive = createInstitution(entityManager, "boero-inactive");
    inactive.updateStatus(false);
    entityManager.merge(inactive);
    entityManager.flush();
    entityManager.clear();

    var page = institutionRepository.findAllWithLocation(PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(2);
    assertThat(page.getContent())
        .extracting(mappedInstitution -> mappedInstitution.getSlug())
        .contains("boero-active", "boero-inactive");
    assertThat(page.getContent())
        .allSatisfy(
            institution -> {
              assertThat(institution.getCity().getName()).isEqualTo("Villa Maria");
              assertThat(institution.getCity().getProvince().getName()).isEqualTo("Cordoba");
              assertThat(institution.getCity().getProvince().getCountry().getIsoCode())
                  .isNotBlank();
            });
  }

  @Test
  @DisplayName("Should filter admin institution list by search and active status")
  void findWithLocationByFilters_filtersBySearchAndActive() {
    Institution active = createInstitution(entityManager, "boero-active");
    active.rename("Conservatorio Superior de Música Felipe Boero");
    entityManager.merge(active);

    Institution inactive = createInstitution(entityManager, "boero-inactive");
    inactive.updateStatus(false);
    entityManager.merge(inactive);

    Institution other = createInstitution(entityManager, "other-institution");
    other.rename("Escuela Municipal");
    entityManager.merge(other);
    entityManager.flush();
    entityManager.clear();

    var page =
        institutionRepository.findWithLocationByFilters("musica", true, PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent())
        .extracting(mappedInstitution -> mappedInstitution.getSlug())
        .containsExactly("boero-active");
    assertThat(page.getContent())
        .allSatisfy(
            institution -> {
              assertThat(institution.getCity().getName()).isEqualTo("Villa Maria");
              assertThat(institution.getCity().getProvince().getName()).isEqualTo("Cordoba");
              assertThat(institution.getCity().getProvince().getCountry().getIsoCode())
                  .isNotBlank();
            });
  }

  @Test
  @DisplayName("Should aggregate the complete platform dashboard summary in one query")
  void getPlatformDashboardSummaryCounts_countsAvailableRecords() {
    Institution active = createInstitution(entityManager, "boero-active");
    Institution inactive = createInstitution(entityManager, "boero-inactive");
    inactive.updateStatus(false);
    createUser(entityManager, active, "11111111");
    User disabled = createUser(entityManager, active, "22222222");
    disabled.updateAccess(false);
    User deletedPerson = createUser(entityManager, active, "33333333");
    deletedPerson.getPerson().delete();
    createUser(entityManager, inactive, "44444444");
    entityManager.flush();

    PlatformDashboardSummaryCounts summary =
        institutionRepository.getPlatformDashboardSummaryCounts();

    assertThat(summary.getInstitutions()).isEqualTo(2);
    assertThat(summary.getActiveInstitutions()).isEqualTo(1);
    assertThat(summary.getPeople()).isEqualTo(3);
    assertThat(summary.getUsersWithAccess()).isEqualTo(1);
  }

  @Test
  @DisplayName("Should detect slug duplicates")
  void existsBySlug_detectsDuplicates() {
    createInstitution(entityManager, "boero");
    entityManager.flush();

    assertThat(institutionRepository.existsBySlug("boero")).isTrue();
    assertThat(institutionRepository.existsBySlug("other-slug")).isFalse();
  }

  @Test
  @DisplayName("Should detect slug duplicates excluding current institution")
  void existsBySlugAndIdNot_excludesCurrentInstitution() {
    Institution institution = createInstitution(entityManager, "boero");
    entityManager.flush();

    assertThat(institutionRepository.existsBySlugAndIdNot("boero", institution.getId())).isFalse();
    assertThat(institutionRepository.existsBySlugAndIdNot("boero", UUID.randomUUID())).isTrue();
  }

  @Test
  @DisplayName("Should fetch institution with location by id")
  void findWithLocationById_loadsAssociations() {
    Country countryEntity = persist(entityManager, country("ARG"));
    Province provinceEntity = persist(entityManager, province(countryEntity, "14"));
    City cityEntity = persist(entityManager, city(provinceEntity, "140182"));
    Institution saved = persist(entityManager, institution(cityEntity, "boero-detail"));
    entityManager.flush();
    entityManager.clear();

    Institution found = institutionRepository.findWithLocationById(saved.getId()).orElseThrow();

    assertThat(found.getCity().getName()).isEqualTo("Villa Maria");
    assertThat(found.getCity().getProvince().getName()).isEqualTo("Cordoba");
    assertThat(found.getCity().getProvince().getCountry().getIsoCode()).isEqualTo("ARG");
  }

  @Test
  @DisplayName("Should aggregate institution creation counts by month")
  void countCreatedByMonth_groupsInstitutionsByMonth() {
    Institution first = createInstitution(entityManager, "boero-first");
    Institution second = createInstitution(entityManager, "boero-second");
    entityManager.flush();
    updateCreatedAt(
        first, java.time.LocalDateTime.of(2026, 6, 4, 10, 0).toInstant(java.time.ZoneOffset.UTC));
    updateCreatedAt(
        second, java.time.LocalDateTime.of(2026, 6, 20, 15, 0).toInstant(java.time.ZoneOffset.UTC));
    entityManager.clear();

    var counts =
        institutionRepository.countCreatedByMonth(
            java.time.LocalDateTime.of(2026, 5, 1, 0, 0).toInstant(java.time.ZoneOffset.UTC),
            java.time.LocalDateTime.of(2026, 8, 1, 0, 0).toInstant(java.time.ZoneOffset.UTC));

    assertThat(counts).hasSize(1);
    assertThat(counts.getFirst().getYear()).isEqualTo(2026);
    assertThat(counts.getFirst().getMonth()).isEqualTo(6);
    assertThat(counts.getFirst().getInstitutionCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("Should group institution creation counts by UTC month")
  void countCreatedByMonth_groupsInstitutionsByUtcMonth() {
    Institution june = createInstitution(entityManager, "boero-june");
    Institution july = createInstitution(entityManager, "boero-july");
    entityManager.flush();
    updateCreatedAt(june, Instant.parse("2026-06-30T23:30:00Z"));
    updateCreatedAt(july, Instant.parse("2026-07-01T00:30:00Z"));
    entityManager.clear();

    var counts =
        institutionRepository.countCreatedByMonth(
            Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"));

    assertThat(counts)
        .extracting(
            mappedMonthlyInstitutionCount -> mappedMonthlyInstitutionCount.getYear(),
            mappedMonthlyInstitutionCount -> mappedMonthlyInstitutionCount.getMonth())
        .containsExactly(tuple(2026, 6), tuple(2026, 7));
    assertThat(counts)
        .extracting(
            mappedMonthlyInstitutionCount -> mappedMonthlyInstitutionCount.getInstitutionCount())
        .containsExactly(1L, 1L);
  }

  @Test
  @DisplayName("Should return the five most recently created institutions with location")
  void findTop5ByOrderByCreatedAtDesc_returnsRecentInstitutions() {
    for (int index = 0; index < 6; index++) {
      Institution institution = createInstitution(entityManager, "boero-" + index);
      entityManager.flush();
      updateCreatedAt(
          institution,
          java.time.LocalDateTime.of(2026, 7, index + 1, 10, 0)
              .toInstant(java.time.ZoneOffset.UTC));
    }
    entityManager.clear();

    var institutions = institutionRepository.findTop5ByOrderByCreatedAtDesc();

    assertThat(institutions)
        .extracting(mappedInstitution -> mappedInstitution.getSlug())
        .containsExactly("boero-5", "boero-4", "boero-3", "boero-2", "boero-1");
    assertThat(institutions)
        .allSatisfy(
            institution -> {
              assertThat(institution.getCity().getName()).isEqualTo("Villa Maria");
              assertThat(institution.getCity().getProvince().getName()).isEqualTo("Cordoba");
            });
  }

  private void updateCreatedAt(final Institution institution, final Instant createdAt) {
    entityManager
        .createQuery(
            "UPDATE Institution institution SET institution.createdAt = :createdAt WHERE institution.id = :id")
        .setParameter("createdAt", createdAt)
        .setParameter("id", institution.getId())
        .executeUpdate();
  }
}
