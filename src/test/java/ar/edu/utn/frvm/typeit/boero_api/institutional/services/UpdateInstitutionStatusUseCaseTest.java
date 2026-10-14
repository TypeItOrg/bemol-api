package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateInstitutionStatusUseCaseTest {

  @Mock private InstitutionRepository institutionRepository;
  @Mock private SessionRevocationService sessionRevocationService;
  @InjectMocks private UpdateInstitutionStatusUseCase useCase;

  @Test
  @DisplayName("Should revoke all institutional sessions when deactivating an institution")
  void execute_revokesSessionsWhenDeactivating() {
    final UUID institutionId = UUID.randomUUID();
    final Institution institution = Institution.builder().id(institutionId).active(true).build();
    when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(institution));

    useCase.execute(institutionId, false);
    useCase.execute(institutionId, false);

    assertThat(institution.isActive()).isFalse();
    verify(institutionRepository).save(institution);
    verify(sessionRevocationService).revokeInstitutionalSessionsForInstitution(institutionId);
  }

  @Test
  @DisplayName("Should not revoke sessions when activating an institution")
  void execute_doesNotRevokeSessionsWhenActivating() {
    final UUID institutionId = UUID.randomUUID();
    final Institution institution = Institution.builder().id(institutionId).active(false).build();
    when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(institution));

    useCase.execute(institutionId, true);

    assertThat(institution.isActive()).isTrue();
    verify(institutionRepository).save(institution);
    verify(sessionRevocationService, never())
        .revokeInstitutionalSessionsForInstitution(institutionId);
  }

  @Test
  @DisplayName("Should fail when updating the status of a missing institution")
  void execute_throwsWhenInstitutionDoesNotExist() {
    final UUID institutionId = UUID.randomUUID();
    when(institutionRepository.findById(institutionId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(institutionId, false))
        .isInstanceOf(InstitutionNotFoundException.class);

    verify(sessionRevocationService, never())
        .revokeInstitutionalSessionsForInstitution(institutionId);
  }
}
