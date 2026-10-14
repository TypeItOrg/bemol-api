package ar.edu.utn.frvm.typeit.boero_api.auth.interfaces;

import static ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData.createInstitution;
import static ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData.createUser;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.support.JpaAuditingTestConfig;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingTestConfig.class)
class UserRepositoryTest {

  @Autowired private EntityManager entityManager;
  @Autowired private UserRepository userRepository;

  @Test
  @DisplayName("Document lookup and existence stay scoped even when institutions share a document")
  void documentQueriesCannotCrossInstitutionBoundaries() {
    Institution boero = createInstitution(entityManager, "boero");
    Institution other = createInstitution(entityManager, "other-school");
    User first = createUser(entityManager, boero, "12345678");
    User second = createUser(entityManager, other, "12345678");
    createUser(entityManager, boero, "87654321");
    entityManager.flush();
    entityManager.clear();

    assertThat(
            userRepository.findByPersonDocumentNumberAndInstitution_Id("12345678", boero.getId()))
        .get()
        .extracting(user -> user.getId())
        .isEqualTo(first.getId());
    assertThat(
            userRepository.findByPersonDocumentNumberAndInstitution_Id("12345678", other.getId()))
        .get()
        .extracting(user -> user.getId())
        .isEqualTo(second.getId());
    assertThat(
            userRepository.findByPersonDocumentNumberAndInstitution_Id("87654321", other.getId()))
        .isEmpty();
    assertThat(
            userRepository.existsByPersonDocumentNumberAndInstitution_Id("12345678", boero.getId()))
        .isTrue();
    assertThat(
            userRepository.existsByPersonDocumentNumberAndInstitution_Id("87654321", other.getId()))
        .isFalse();
  }

  @Test
  @DisplayName("Should find the active user when a deleted user has the same document")
  void shouldFindActiveUserWhenDeletedUserHasSameDocument() {
    Institution institution = createInstitution(entityManager, "boero");
    User deletedUser = createUser(entityManager, institution, "12345678");
    entityManager.flush();
    deletedUser.getPerson().delete();
    entityManager.flush();

    User activeUser = createUser(entityManager, institution, "12345678");
    entityManager.flush();

    assertThat(
            userRepository.findWithPersonAndInstitutionByPersonDocumentNumberAndInstitution_Id(
                "12345678", institution.getId()))
        .contains(activeUser);
    assertThat(
            userRepository.existsByPersonDocumentNumberAndInstitution_Id(
                "12345678", institution.getId()))
        .isTrue();
  }

  @Test
  @DisplayName("Should find the user for password recovery with their relations loaded")
  void shouldFindUserForPasswordRecovery() {
    Institution institution = createInstitution(entityManager, "boero-recovery");
    User user = createUser(entityManager, institution, "12345678");
    entityManager.flush();

    entityManager.clear();
    User loaded =
        userRepository
            .findWithPersonAndInstitutionForPasswordRecovery("12345678", institution.getId())
            .orElseThrow();
    assertThat(loaded.getId()).isEqualTo(user.getId());
    assertThat(Hibernate.isInitialized(loaded.getPerson())).isTrue();
    assertThat(Hibernate.isInitialized(loaded.getInstitution())).isTrue();
    entityManager.detach(loaded);
    assertThat(loaded.getPerson().getDocumentNumber()).isEqualTo("12345678");
    assertThat(loaded.getInstitution().getName()).isEqualTo(institution.getName());
  }

  @Test
  @DisplayName("Should count enabled users grouped by institution id for a set of institution ids")
  void countEnabledUsersByInstitutionIdIn_countsEnabledUsersPerInstitution() {
    Institution boero = createInstitution(entityManager, "boero");
    Institution other = createInstitution(entityManager, "other-school");
    createUser(entityManager, boero, "11111111");
    createUser(entityManager, boero, "22222222");
    User disabled = createUser(entityManager, boero, "33333333");
    disabled.updateAccess(false);
    entityManager.merge(disabled);
    entityManager.flush();
    entityManager.clear();

    var counts =
        userRepository.countEnabledUsersByInstitutionIdIn(List.of(boero.getId(), other.getId()));

    assertThat(counts).hasSize(1);
    var boeroCount = counts.getFirst();
    assertThat(boeroCount.getInstitutionId()).isEqualTo(boero.getId());
    assertThat(boeroCount.getUserCount()).isEqualTo(2L);
  }

  @Test
  @DisplayName("Should count only users with effective platform access")
  void countUsersWithAccess_excludesUnavailableAccounts() {
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

    assertThat(userRepository.countUsersWithAccess()).isEqualTo(1);
  }
}
