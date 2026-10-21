package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateInstitutionalUserStatusUseCaseTest {

  @Mock private InstitutionRepository institutionRepository;
  @Mock private UserRepository userRepository;
  @Mock private SessionRevocationService sessionRevocationService;

  @Mock private AuditEventRecorder auditEventRecorder;

  @InjectMocks private UpdateInstitutionalUserStatusUseCase useCase;

  @Test
  void disablesOwnAccessFlagEvenWhenInstitutionMakesEffectiveAccessUnavailable() {
    final UUID institutionId = UUID.randomUUID();
    final UUID actorPersonId = UUID.randomUUID();
    final UUID personId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();
    final Institution institution = Institution.builder().id(institutionId).active(false).build();
    final Person person = Person.builder().id(personId).institution(institution).build();
    final User user =
        User.builder()
            .id(userId)
            .institution(institution)
            .person(person)
            .password("encoded")
            .enabled(true)
            .build();
    when(institutionRepository.findByIdForUpdate(institutionId))
        .thenReturn(Optional.of(institution));
    when(userRepository.findByPerson_IdAndInstitution_Id(personId, institutionId))
        .thenReturn(Optional.of(user));

    useCase.execute(institutionId, actorPersonId, personId, false);

    assertThat(user.isAccessEnabled()).isFalse();
    verify(userRepository).save(user);
    verify(sessionRevocationService).revokeInstitutionalSessionsForUser(userId);
  }
}
