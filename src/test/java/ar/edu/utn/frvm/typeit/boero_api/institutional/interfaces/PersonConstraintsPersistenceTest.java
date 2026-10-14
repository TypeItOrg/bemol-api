package ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces;

import static ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData.createInstitution;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.support.JpaAuditingTestConfig;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingTestConfig.class)
class PersonConstraintsPersistenceTest {
  @Autowired private EntityManager entityManager;

  @ParameterizedTest
  @CsvSource(
      value = {
        "firstName|AB|12345678|ana@example.com",
        "documentNumber|Ana|abcdefgh|ana@example.com",
        "email|Ana|12345678|NULL"
      },
      delimiter = '|',
      nullValues = "NULL")
  void rejectsExactlyTheInvalidFieldDuringPersistence(
      String field, String firstName, String document, String email) {
    final var institution = createInstitution(entityManager, "person-validation");
    final var person =
        Person.builder()
            .institution(institution)
            .firstName(firstName)
            .lastName("Garcia")
            .documentNumber(document)
            .email(email)
            .build();

    assertThatThrownBy(
            () -> {
              entityManager.persist(person);
              entityManager.flush();
            })
        .isInstanceOf(ConstraintViolationException.class)
        .satisfies(
            error ->
                assertThat(((ConstraintViolationException) error).getConstraintViolations())
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly(field));
  }
}
