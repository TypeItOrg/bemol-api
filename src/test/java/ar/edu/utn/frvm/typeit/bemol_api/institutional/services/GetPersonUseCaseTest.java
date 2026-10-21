package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetPersonUseCaseTest {

  @Mock private PersonRepository personRepository;

  @InjectMocks private GetPersonUseCase getPersonUseCase;

  @Test
  @DisplayName("Should return person profile with details")
  void execute_returnsPersonProfile() {
    UUID institutionId = UUID.randomUUID();
    Institution institution =
        Institution.builder().id(institutionId).name("Conservatorio Boero").build();
    Person person =
        Person.builder()
            .id(UUID.randomUUID())
            .firstName("Juan")
            .lastName("Pérez")
            .documentNumber("12345678")
            .institution(institution)
            .build();
    JwtAuthenticatedUser principal =
        JwtAuthenticatedUser.builder()
            .userId(UUID.randomUUID())
            .personId(person.getId())
            .institutionId(institutionId)
            .build();

    when(personRepository.findWithDetailsByIdAndInstitution_Id(person.getId(), institutionId))
        .thenReturn(Optional.of(person));

    var response = getPersonUseCase.execute(principal);

    assertThat(response.personId()).isEqualTo(person.getId());
    assertThat(response.firstName()).isEqualTo("Juan");
    assertThat(response.lastName()).isEqualTo("Pérez");
    assertThat(response.documentNumber()).isEqualTo("12345678");
    assertThat(response.institutionName()).isEqualTo("Conservatorio Boero");
  }

  @Test
  @DisplayName("Should throw when person not found")
  void execute_throwsWhenPersonNotFound() {
    UUID personId = UUID.randomUUID();
    UUID institutionId = UUID.randomUUID();
    JwtAuthenticatedUser principal =
        JwtAuthenticatedUser.builder()
            .userId(UUID.randomUUID())
            .personId(personId)
            .institutionId(institutionId)
            .build();

    when(personRepository.findWithDetailsByIdAndInstitution_Id(personId, institutionId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> getPersonUseCase.execute(principal))
        .isInstanceOf(PersonNotFoundException.class);
  }
}
