package ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces;

import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.createInstitution;
import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.persist;
import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.person;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.support.JpaAuditingTestConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataJpaTest
@Import(JpaAuditingTestConfig.class)
class PersonRepositoryTest {

  @Autowired private EntityManager entityManager;
  @Autowired private PersonRepository personRepository;

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
