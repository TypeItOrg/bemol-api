package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.PersonResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetPersonByIdUseCaseTest {

  @Mock private PersonRepository personRepository;

  @InjectMocks private GetPersonByIdUseCase getPersonByIdUseCase;

  @Test
  @DisplayName("Should return person response when person is in institution")
  void execute_returnsPerson() {
    UUID institutionId = UUID.randomUUID();
    UUID personId = UUID.randomUUID();
    Person person = personWith(institutionId, personId);
    when(personRepository.findWithDetailsByIdAndInstitution_Id(personId, institutionId))
        .thenReturn(Optional.of(person));

    PersonResponse response = getPersonByIdUseCase.execute(institutionId, personId);

    assertThat(response.personId()).isEqualTo(personId);
    assertThat(response.firstName()).isEqualTo("Ana");
  }

  @Test
  @DisplayName("Should throw when person is not in institution")
  void execute_throwsWhenPersonNotFound() {
    UUID institutionId = UUID.randomUUID();
    UUID personId = UUID.randomUUID();
    when(personRepository.findWithDetailsByIdAndInstitution_Id(personId, institutionId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> getPersonByIdUseCase.execute(institutionId, personId))
        .isInstanceOf(PersonNotFoundException.class);
  }

  @Test
  @DisplayName("Should return soft-deleted persons")
  void execute_returnsSoftDeletedPerson() {
    UUID institutionId = UUID.randomUUID();
    UUID personId = UUID.randomUUID();
    Person person = personWith(institutionId, personId);
    person.delete();
    when(personRepository.findWithDetailsByIdAndInstitution_Id(personId, institutionId))
        .thenReturn(Optional.of(person));

    PersonResponse response = getPersonByIdUseCase.execute(institutionId, personId);

    assertThat(response.deleted()).isTrue();
  }

  private Person personWith(UUID institutionId, UUID personId) {
    Institution institution = Institution.builder().id(institutionId).name("Conservatorio").build();
    return Person.builder()
        .id(personId)
        .institution(institution)
        .firstName("Ana")
        .lastName("García")
        .documentNumber("12345678")
        .build();
  }
}
