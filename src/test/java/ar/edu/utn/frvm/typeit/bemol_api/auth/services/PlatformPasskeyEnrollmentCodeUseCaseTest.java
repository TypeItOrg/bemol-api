package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.audit.AuthAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PasskeyLimitExceededException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
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
class PlatformPasskeyEnrollmentCodeUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

  @Mock private PlatformAccountRepository accounts;
  @Mock private PlatformWebAuthnOperations operations;
  @Mock private PlatformPasskeyStateStore state;
  @Mock private AuditEventRecorder audit;

  private PlatformPasskeyEnrollmentCodeUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new PlatformPasskeyEnrollmentCodeUseCase(
            accounts, operations, state, audit, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void issueGrantsEnrollmentCodeRequiresEnrollReauthAndRecordsAudit() {
    final UUID accountId = UUID.randomUUID();
    final var principal = AuthTestData.platformPrincipal(accountId);
    final var account = enabledAccount(accountId);
    when(accounts.findWithLockById(accountId)).thenReturn(Optional.of(account));
    when(state.issueCode(PlatformPasskeyStateStore.CodeKind.ENROLLMENT, accountId))
        .thenReturn(new PlatformPasskeyStateStore.IssuedCode("WXYZ-2345", Duration.ofMinutes(10)));

    final var response = useCase.issue(principal);

    assertThat(response.code()).isEqualTo("WXYZ-2345");
    assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    verify(state).requireRecent(accountId, principal.sessionId(), "enroll", true);
    verify(audit)
        .recordCurrentActor(
            eq(null),
            eq(null),
            eq(AuthAuditAction.PLATFORM_PASSKEY_ENROLLMENT_CODE_ISSUED),
            eq(PlatformAccount.AUDIT_TYPE),
            eq(accountId),
            eq(AuditOrigin.SELF_SERVICE),
            anyMap());
  }

  @Test
  void issueRejectsWhenPasskeyLimitReached() {
    final UUID accountId = UUID.randomUUID();
    final var principal = AuthTestData.platformPrincipal(accountId);
    final var account = enabledAccount(accountId);
    when(accounts.findWithLockById(accountId)).thenReturn(Optional.of(account));
    doThrow(new PasskeyLimitExceededException()).when(operations).requireCapacity(account);

    assertThatThrownBy(() -> useCase.issue(principal))
        .isInstanceOf(PasskeyLimitExceededException.class);
    verifyNoInteractions(state, audit);
  }

  @Test
  void cancelClearsEnrollmentCode() {
    final UUID accountId = UUID.randomUUID();
    final var principal = AuthTestData.platformPrincipal(accountId);
    when(accounts.findById(accountId)).thenReturn(Optional.of(enabledAccount(accountId)));

    useCase.cancel(principal);

    verify(state).clearCode(PlatformPasskeyStateStore.CodeKind.ENROLLMENT, accountId);
  }

  private static PlatformAccount enabledAccount(final UUID id) {
    return PlatformAccount.builder().id(id).email("admin@example.test").enabled(true).build();
  }
}
