package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.LastPlatformAdminDisableException;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.PlatformAccountSelfDisableException;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PlatformAccountRoleRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdatePlatformAccountStatusUseCaseTest {

  @Mock private PlatformAccountRepository platformAccountRepository;
  @Mock private PlatformAccountRoleRepository platformAccountRoleRepository;
  @Mock private SessionRevocationService sessionRevocationService;

  private UpdatePlatformAccountStatusUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new UpdatePlatformAccountStatusUseCase(
            platformAccountRepository,
            platformAccountRoleRepository,
            sessionRevocationService,
            org.mockito.Mockito.mock(AuditEventRecorder.class));
  }

  @Test
  void disableRevokesAllSessionsWhenAnotherAdministratorRemainsActive() {
    final UUID currentAccountId = UUID.randomUUID();
    final PlatformAccount target = enabledAccount(UUID.randomUUID());
    when(platformAccountRepository.findAllForUpdate())
        .thenReturn(List.of(enabledAccount(currentAccountId), target));
    when(platformAccountRoleRepository.countEnabledAccountsByRoleCode(
            PlatformRoleCode.PLATFORM_ADMIN.name()))
        .thenReturn(2L);

    useCase.execute(target.getId(), currentAccountId, false);

    assertThat(target.isEnabled()).isFalse();
    verify(platformAccountRepository).save(target);
    verify(sessionRevocationService).revokePlatformAccountSessions(target.getId());
  }

  @Test
  void disableRejectsCurrentAccount() {
    final PlatformAccount currentAccount = enabledAccount(UUID.randomUUID());
    when(platformAccountRepository.findAllForUpdate()).thenReturn(List.of(currentAccount));

    assertThatThrownBy(() -> useCase.execute(currentAccount.getId(), currentAccount.getId(), false))
        .isInstanceOf(PlatformAccountSelfDisableException.class);
    verify(platformAccountRepository, never()).save(currentAccount);
    verifyNoInteractions(platformAccountRoleRepository, sessionRevocationService);
  }

  @Test
  void disableRejectsLastActiveAdministrator() {
    final PlatformAccount currentAccount = enabledAccount(UUID.randomUUID());
    final PlatformAccount target = enabledAccount(UUID.randomUUID());
    when(platformAccountRepository.findAllForUpdate()).thenReturn(List.of(currentAccount, target));
    when(platformAccountRoleRepository.countEnabledAccountsByRoleCode(
            PlatformRoleCode.PLATFORM_ADMIN.name()))
        .thenReturn(1L);

    assertThatThrownBy(() -> useCase.execute(target.getId(), currentAccount.getId(), false))
        .isInstanceOf(LastPlatformAdminDisableException.class);
    verify(platformAccountRepository, never()).save(target);
    verifyNoInteractions(sessionRevocationService);
  }

  private static PlatformAccount enabledAccount(final UUID id) {
    return PlatformAccount.builder()
        .id(id)
        .name("María")
        .lastName("González")
        .email(id + "@boero.edu.ar")
        .password("hashed-password")
        .enabled(true)
        .build();
  }
}
