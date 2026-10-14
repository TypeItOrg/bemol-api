package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.PersonNotFoundInInstitutionException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionPersonResolver;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeletePersonUseCaseTest {

  @Mock private InstitutionPersonResolver institutionPersonResolver;
  @Mock private PersonRepository personRepository;
  @Mock private UserRepository userRepository;
  @Mock private SessionRevocationService sessionRevocationService;
  @Mock private InstitutionRepository institutionRepository;

  @InjectMocks private DeletePersonUseCase deletePersonUseCase;

  private UUID institutionId;
  private UUID personId;
  private Person person;
  private User user;

  @BeforeEach
  void setUp() {
    institutionId = UUID.randomUUID();
    personId = UUID.randomUUID();
    Institution institution = Institution.builder().id(institutionId).name("Boero").build();
    when(institutionRepository.findByIdForUpdate(institutionId))
        .thenReturn(Optional.of(institution));
    person =
        Person.builder()
            .id(personId)
            .institution(institution)
            .firstName("Ana")
            .lastName("García")
            .documentNumber("12345678")
            .build();
    user = User.builder().institution(institution).person(person).password("encoded").build();
  }

  @Test
  @DisplayName("Should soft delete person, disable user, and revoke sessions")
  void execute_softDeletesAndRevokesSessions() {
    when(institutionPersonResolver.requirePersonInInstitution(institutionId, personId))
        .thenReturn(person);
    when(userRepository.findByPerson_IdAndInstitution_Id(personId, institutionId))
        .thenReturn(Optional.of(user));

    deletePersonUseCase.execute(institutionId, personId);
    deletePersonUseCase.execute(institutionId, personId);

    assertThat(person.isDeleted()).isTrue();
    assertThat(user.isEnabled()).isFalse();
    verify(personRepository).save(person);
    verify(userRepository).save(user);
    verify(sessionRevocationService).revokeInstitutionalSessionsForPerson(personId, institutionId);
  }

  @Test
  @DisplayName("Should propagate not-found from resolver")
  void execute_propagatesNotFound() {
    when(institutionPersonResolver.requirePersonInInstitution(institutionId, personId))
        .thenThrow(new PersonNotFoundInInstitutionException());

    assertThatThrownBy(() -> deletePersonUseCase.execute(institutionId, personId))
        .isInstanceOf(PersonNotFoundInInstitutionException.class);
  }
}
