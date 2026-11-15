package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.audit.AuthAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PlatformAccountConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PlatformAccountNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformPasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformAccountSetupCodeUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

  @Mock private PlatformAccountRepository accounts;
  @Mock private PlatformPasskeyCredentialRepository credentials;
  @Mock private PlatformPasskeyStateStore state;
  @Mock private AuditEventRecorder audit;

  private PlatformAccountSetupCodeUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new PlatformAccountSetupCodeUseCase(
            accounts, credentials, state, audit, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void issueGrantsSetupCodeForAnotherEnabledAccountAndAuditsRecoveryFlag() {
    final UUID actorId = UUID.randomUUID();
    final UUID targetId = UUID.randomUUID();
    final var principal = AuthTestData.platformPrincipal(actorId);
    final var target =
        PlatformAccount.builder().id(targetId).email("target@example.test").enabled(true).build();
    when(accounts.findWithLockById(targetId)).thenReturn(Optional.of(target));
    when(credentials.existsActiveByPlatformAccountId(targetId)).thenReturn(true);
    when(state.issueCode(PlatformPasskeyStateStore.CodeKind.SETUP, targetId))
        .thenReturn(new PlatformPasskeyStateStore.IssuedCode("ABCD-EF23", Duration.ofMinutes(15)));

    final var response = useCase.issue(targetId, principal);

    assertThat(response.code()).isEqualTo("ABCD-EF23");
    assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    assertThat(response.recovery()).isTrue();
    verify(state).requireRecent(actorId, principal.sessionId(), "setup-code:" + targetId, true);
    verify(audit)
        .recordCurrentActor(
            eq(null),
            eq(null),
            eq(AuthAuditAction.PLATFORM_PASSKEY_SETUP_CODE_ISSUED),
            eq(PlatformAccount.AUDIT_TYPE),
            eq(targetId),
            eq(AuditOrigin.PLATFORM_ADMINISTRATION),
            anyMap());
  }

  @Test
  void issueRejectsSelfTargetAndDisabledTarget() {
    final UUID actorId = UUID.randomUUID();
    final UUID disabledId = UUID.randomUUID();
    final var principal = AuthTestData.platformPrincipal(actorId);
    when(accounts.findWithLockById(disabledId))
        .thenReturn(
            Optional.of(
                PlatformAccount.builder()
                    .id(disabledId)
                    .email("disabled@example.test")
                    .enabled(false)
                    .build()));

    assertThatThrownBy(() -> useCase.issue(actorId, principal))
        .isInstanceOf(PlatformAccountConflictException.class);
    assertThatThrownBy(() -> useCase.issue(disabledId, principal))
        .isInstanceOf(PlatformAccountConflictException.class);
    verifyNoInteractions(state, audit);
  }

  @Test
  void cancelClearsSetupCodeForExistingPeerAccount() {
    final UUID actorId = UUID.randomUUID();
    final UUID targetId = UUID.randomUUID();
    final var principal = AuthTestData.platformPrincipal(actorId);
    when(accounts.existsById(targetId)).thenReturn(true);

    useCase.cancel(targetId, principal);

    verify(state).clearCode(PlatformPasskeyStateStore.CodeKind.SETUP, targetId);

    final UUID unknownId = UUID.randomUUID();
    when(accounts.existsById(unknownId)).thenReturn(false);
    assertThatThrownBy(() -> useCase.cancel(unknownId, principal))
        .isInstanceOf(PlatformAccountNotFoundException.class);
  }
}
