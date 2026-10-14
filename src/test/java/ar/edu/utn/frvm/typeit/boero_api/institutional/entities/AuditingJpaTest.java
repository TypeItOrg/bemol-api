package ar.edu.utn.frvm.typeit.boero_api.institutional.entities;

import static ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData.city;
import static ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData.country;
import static ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData.institution;
import static ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData.persist;
import static ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData.person;
import static ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData.province;
import static ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;

import ar.edu.utn.frvm.typeit.boero_api.auth.entities.RefreshToken;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.UserSession;
import ar.edu.utn.frvm.typeit.boero_api.support.JpaAuditingTestConfig;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@DataJpaTest
@Import({JpaAuditingTestConfig.class, AuditingJpaTest.FixedClockConfig.class})
class AuditingJpaTest {

  private static final Instant FIXED_INSTANT = Instant.parse("2026-09-09T12:00:00Z");
  private static final Instant EXPIRES_AT = Instant.parse("2026-12-01T18:45:30.123456Z");

  @Autowired private EntityManager entityManager;
  @Autowired private Clock clock;

  @BeforeEach
  void resetClock() {
    reset(clock);
    doReturn(FIXED_INSTANT).when(clock).instant();
  }

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    @Primary
    Clock fixedClock() {
      return spy(Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
    }
  }

  @Test
  @DisplayName("Should audit and persist institutional user mapping")
  void shouldAuditAndPersistInstitutionalUserMapping() {
    Country country = persist(entityManager, country("ARG"));
    Province province = persist(entityManager, province(country, "14"));
    City city = persist(entityManager, city(province, "14014010"));
    Institution institution = persist(entityManager, institution(city, "boero"));
    Person person = persist(entityManager, person(institution, "12345678"));
    User user = persist(entityManager, user(institution, person));

    entityManager.flush();

    assertThat(user.getId()).isNotNull();
    assertThat(user.getId().version()).isEqualTo(7);
    assertThat(user.getInstitution().getId()).isEqualTo(institution.getId());
    assertThat(user.getPerson().getId()).isEqualTo(person.getId());
    assertThat(country.getId().version()).isEqualTo(7);
    assertThat(province.getId().version()).isEqualTo(7);
    assertThat(city.getId().version()).isEqualTo(7);
    assertThat(institution.getId().version()).isEqualTo(7);
    assertThat(person.getId().version()).isEqualTo(7);
    assertAudited(country);
    assertAudited(province);
    assertAudited(city);
    assertAudited(institution);
    assertAudited(person);
    assertAudited(user);
  }

  @Test
  @DisplayName(
      "Should keep last modified date on the shared clock when an auditable entity changes")
  void shouldKeepLastModifiedDateOnSharedClockWhenAuditableEntityChanges() {
    Country country = persist(entityManager, country("URY"));
    entityManager.flush();
    final var later = FIXED_INSTANT.plusSeconds(3600);
    doReturn(later).when(clock).instant();

    country.setName("Republica Oriental del Uruguay");
    entityManager.flush();

    final UUID id = country.getId();
    entityManager.clear();
    final var reloaded = entityManager.find(Country.class, id);
    assertThat(reloaded.getCreatedAt()).isEqualTo(FIXED_INSTANT);
    assertThat(reloaded.getUpdatedAt()).isEqualTo(later);
    assertThat(reloaded.getName()).isEqualTo("Republica Oriental del Uruguay");
  }

  @Test
  @DisplayName("Should persist Instant audit and expiry values from the shared clock")
  void shouldPersistInstantValuesFromSharedClock() {
    Institution institution = createInstitution("refresh-audit");
    User user = createUser(institution, "20000001");
    UserSession session = persist(entityManager, userSession(user));
    RefreshToken refreshToken =
        RefreshToken.builder()
            .sessionId(session.getId())
            .tokenHash("refresh-hash")
            .familyId(UUID.randomUUID().toString())
            .expiresAt(EXPIRES_AT)
            .build();

    persist(entityManager, refreshToken);
    entityManager.flush();
    final UUID refreshTokenId = refreshToken.getId();
    entityManager.clear();

    RefreshToken found = entityManager.find(RefreshToken.class, refreshTokenId);
    UserSession foundSession = entityManager.find(UserSession.class, session.getId());

    assertThat(found.getId().version()).isEqualTo(7);
    assertThat(found.getExpiresAt()).isEqualTo(EXPIRES_AT);
    assertThat(found.getCreatedAt()).isEqualTo(FIXED_INSTANT);
    assertThat(foundSession.getId().version()).isEqualTo(7);
    assertThat(foundSession.getStartedAt()).isEqualTo(FIXED_INSTANT);
  }

  private Institution createInstitution(final String slug) {
    return ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData.createInstitution(
        entityManager, slug);
  }

  private User createUser(final Institution institution, final String documentNumber) {
    return ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData.createUser(
        entityManager, institution, documentNumber);
  }

  private static UserSession userSession(final User user) {
    return UserSession.builder()
        .userId(user.getId())
        .ipAddress("192.0.2.10")
        .userAgent("Mozilla/5.0")
        .rememberMe(true)
        .build();
  }

  private static void assertAudited(
      ar.edu.utn.frvm.typeit.boero_api.common.persistence.Auditable entity) {
    assertThat(entity.getCreatedAt()).isEqualTo(FIXED_INSTANT);
    assertThat(entity.getUpdatedAt()).isEqualTo(FIXED_INSTANT);
  }
}
