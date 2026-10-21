package ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces;

import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.createInstitution;
import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.persist;
import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.person;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import ar.edu.utn.frvm.typeit.bemol_api.support.JpaAuditingTestConfig;
import ar.edu.utn.frvm.typeit.bemol_api.support.PostgresTestDatabase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.flyway.locations=classpath:db/migration",
      "spring.flyway.sql-migration-prefix=",
      "spring.jpa.hibernate.ddl-auto=validate"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingTestConfig.class)
@Testcontainers
@IntegrationTest
class PersonRepositoryPostgresIntegrationTest {

  private static final PostgresTestDatabase POSTGRES =
      new PostgresTestDatabase("postgres:18-alpine", "institutional_people_repository");

  @Autowired private EntityManager entityManager;
  @Autowired private PersonRepository personRepository;

  @DynamicPropertySource
  static void databaseProperties(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @Test
  @DisplayName("Should find person by document number within institution")
  void shouldFindPersonByDocumentNumberWithinInstitution() {
    Institution institution = createInstitution(entityManager, "boero");
    Person person = persist(entityManager, person(institution, "12345678"));
    entityManager.flush();

    assertThat(
            personRepository.findByDocumentNumberAndInstitution_Id("12345678", institution.getId()))
        .contains(person);
  }

  @Test
  @DisplayName("Should isolate document lookup by institution")
  void shouldIsolateDocumentLookupByInstitution() {
    Institution boero = createInstitution(entityManager, "boero");
    Institution other = createInstitution(entityManager, "other-school");
    persist(entityManager, person(boero, "12345678"));
    entityManager.flush();

    assertThat(personRepository.findByDocumentNumberAndInstitution_Id("12345678", other.getId()))
        .isEmpty();
    assertThat(personRepository.existsByDocumentNumberAndInstitution_Id("12345678", boero.getId()))
        .isTrue();
    assertThat(personRepository.existsByDocumentNumberAndInstitution_Id("12345678", other.getId()))
        .isFalse();
  }

  @Test
  @DisplayName("Should allow reusing a document after the previous person is deleted")
  void shouldAllowReusingDocumentAfterSoftDelete() {
    Institution institution = createInstitution(entityManager, "boero");
    Person deleted = persist(entityManager, person(institution, "12345678"));
    entityManager.flush();

    deleted.delete();
    entityManager.flush();

    Person active = persist(entityManager, person(institution, "12345678"));
    entityManager.flush();

    assertThat(
            personRepository.findByDocumentNumberAndInstitution_Id("12345678", institution.getId()))
        .contains(active);
    assertThat(
            personRepository.findWithDetailsByIdAndInstitution_Id(
                deleted.getId(), institution.getId()))
        .contains(deleted);
  }

  @Test
  void rejectsDuplicateActiveDocumentsInTheSameInstitutionButAllowsOtherInstitutions() {
    final Institution institution = createInstitution(entityManager, "duplicate-document");
    final Institution otherInstitution = createInstitution(entityManager, "foreign-document");
    persist(entityManager, person(institution, "12345678"));
    persist(entityManager, person(otherInstitution, "12345678"));
    entityManager.flush();

    persist(entityManager, person(institution, "12345678"));

    assertThatThrownBy(entityManager::flush)
        .isInstanceOf(PersistenceException.class)
        .hasStackTraceContaining("people_active_document_number_unique");
  }

  @Test
  @DisplayName("Should count only people that are not deleted")
  void countByDeletedFalse_excludesDeletedPeople() {
    Institution institution = createInstitution(entityManager, "boero");
    persist(entityManager, person(institution, "12345678"));
    Person deleted = persist(entityManager, person(institution, "87654321"));
    deleted.delete();
    entityManager.flush();

    assertThat(personRepository.countByDeletedFalse()).isEqualTo(1);
  }

  @Test
  @DisplayName("Should filter platform people across institutions")
  void findPlatformPeople_filtersAcrossInstitutions() {
    Institution boero = createInstitution(entityManager, "boero");
    boero.rename("Boero");
    Institution alberdi = createInstitution(entityManager, "alberdi");
    alberdi.rename("Alberdi");
    Person ana = persist(entityManager, person(boero, "12345678"));
    ana.updateContact("ana@boero.edu.ar", ana.getPhoneNumber());
    Person deleted = persist(entityManager, person(alberdi, "87654321"));
    deleted.delete();
    Role teacher =
        persist(
            entityManager,
            Role.builder()
                .institution(boero)
                .code(SystemRoleCode.TEACHER.name())
                .name(SystemRoleCode.TEACHER.getDisplayName())
                .scope(RoleScope.INSTITUTION)
                .system(true)
                .build());
    persist(
        entityManager,
        PersonRoleAssignment.builder().person(ana).institution(boero).role(teacher).build());
    entityManager.flush();
    entityManager.clear();

    var result =
        personRepository.findPlatformPeople(
            "ana@boero",
            boero.getId(),
            SystemRoleCode.TEACHER.name(),
            PageRequest.of(0, 10, Sort.by("institution.name")));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().getFirst().getId()).isEqualTo(ana.getId());
    assertThat(result.getContent().getFirst().getInstitution().getName()).isEqualTo("Boero");
  }

  @Test
  @DisplayName("Should sort platform people by institution and exclude deleted people")
  void findPlatformPeople_sortsByInstitutionAndExcludesDeletedPeople() {
    Institution boero = createInstitution(entityManager, "boero");
    boero.rename("Boero");
    Institution alberdi = createInstitution(entityManager, "alberdi");
    alberdi.rename("Alberdi");
    persist(entityManager, person(boero, "12345678"));
    persist(entityManager, person(alberdi, "23456789"));
    Person deleted = persist(entityManager, person(alberdi, "87654321"));
    deleted.delete();
    entityManager.flush();
    entityManager.clear();

    var result =
        personRepository.findPlatformPeople(
            null, null, null, PageRequest.of(0, 10, Sort.by("institution.name")));

    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getContent())
        .extracting(person -> person.getInstitution().getName())
        .containsExactly("Alberdi", "Boero");
  }
}
