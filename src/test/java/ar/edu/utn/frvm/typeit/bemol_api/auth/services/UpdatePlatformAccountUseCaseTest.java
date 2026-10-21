package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PlatformAccountEmailAlreadyExistsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PlatformAccountNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.UpdatePlatformAccountRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UpdatePlatformAccountUseCaseTest {

  private static final UUID ACCOUNT_ID = UUID.randomUUID();

  @Mock private PlatformAccountRepository platformAccountRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private SessionRevocationService sessionRevocationService;

  private UpdatePlatformAccountUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new UpdatePlatformAccountUseCase(
            org.mockito.Mockito.mock(AuditEventRecorder.class),
            platformAccountRepository,
            passwordEncoder,
            sessionRevocationService);
  }

  @Test
  void updateNormalizesIdentityWithoutChangingPasswordOrRevokingSessions() {
    final PlatformAccount account = account("admin@boero.edu.ar", "current-password");
    final var request =
        new UpdatePlatformAccountRequest(" María ", " González ", " ADMIN@BOERO.EDU.AR ", "");
    when(platformAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    when(platformAccountRepository.existsByEmailIgnoreCaseAndIdNot(
            "admin@boero.edu.ar", ACCOUNT_ID))
        .thenReturn(false);

    final var response = useCase.execute(ACCOUNT_ID, request);

    assertThat(response.name()).isEqualTo("María");
    assertThat(response.lastName()).isEqualTo("González");
    assertThat(response.email()).isEqualTo("admin@boero.edu.ar");
    assertThat(account.getPassword()).isEqualTo("current-password");
    verifyNoInteractions(passwordEncoder, sessionRevocationService);
  }

  @Test
  void updateHashesPasswordAndRevokesSessions() {
    final PlatformAccount account = account("admin@boero.edu.ar", "current-password");
    final var request =
        new UpdatePlatformAccountRequest("María", "González", "admin@boero.edu.ar", "new-password");
    when(platformAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    when(platformAccountRepository.existsByEmailIgnoreCaseAndIdNot(
            "admin@boero.edu.ar", ACCOUNT_ID))
        .thenReturn(false);
    when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

    useCase.execute(ACCOUNT_ID, request);

    assertThat(account.getPassword()).isEqualTo("new-hash");
    verify(sessionRevocationService).revokePlatformAccountSessions(ACCOUNT_ID);
  }

  @Test
  void updateRevokesSessionsWhenEmailChanges() {
    final PlatformAccount account = account("admin@boero.edu.ar", "current-password");
    final var request =
        new UpdatePlatformAccountRequest("María", "González", "new@boero.edu.ar", null);
    when(platformAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    when(platformAccountRepository.existsByEmailIgnoreCaseAndIdNot("new@boero.edu.ar", ACCOUNT_ID))
        .thenReturn(false);

    useCase.execute(ACCOUNT_ID, request);

    assertThat(account.getEmail()).isEqualTo("new@boero.edu.ar");
    verify(sessionRevocationService).revokePlatformAccountSessions(ACCOUNT_ID);
    verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void updateRejectsEmailUsedByAnotherAccount() {
    final PlatformAccount account = account("admin@boero.edu.ar", "current-password");
    final var request =
        new UpdatePlatformAccountRequest("María", "González", "used@boero.edu.ar", null);
    when(platformAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    when(platformAccountRepository.existsByEmailIgnoreCaseAndIdNot("used@boero.edu.ar", ACCOUNT_ID))
        .thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(ACCOUNT_ID, request))
        .isInstanceOf(PlatformAccountEmailAlreadyExistsException.class);
    verify(platformAccountRepository, never()).saveAndFlush(account);
    verifyNoInteractions(passwordEncoder, sessionRevocationService);
  }

  @Test
  void updateRejectsUnknownAccount() {
    final var request =
        new UpdatePlatformAccountRequest("María", "González", "admin@boero.edu.ar", null);
    when(platformAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(ACCOUNT_ID, request))
        .isInstanceOf(PlatformAccountNotFoundException.class);
    verifyNoInteractions(passwordEncoder, sessionRevocationService);
  }

  private PlatformAccount account(final String email, final String password) {
    return PlatformAccount.builder()
        .id(ACCOUNT_ID)
        .name("Original")
        .lastName("Administrator")
        .email(email)
        .password(password)
        .enabled(true)
        .build();
  }
}
