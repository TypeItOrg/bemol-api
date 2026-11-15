package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.audit.AuthAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PlatformAccountConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PlatformAccountNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformPasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformAccountSetupCodeResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.PlatformPasskeyStateStore.CodeKind;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues and cancels admin-delegated passkey setup or rescue codes for peer platform accounts. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAccountSetupCodeUseCase {
  private final PlatformAccountRepository accounts;
  private final PlatformPasskeyCredentialRepository credentials;
  private final PlatformPasskeyStateStore state;
  private final AuditEventRecorder audit;
  private final Clock clock;

  @Transactional
  public PlatformAccountSetupCodeResponse issue(
      final UUID targetAccountId, final JwtAuthenticatedPlatformAccount principal) {
    if (targetAccountId.equals(principal.platformAccountId())) {
      throw PlatformAccountConflictException.selfSetupCode();
    }

    final var account =
        accounts
            .findWithLockById(targetAccountId)
            .orElseThrow(PlatformAccountNotFoundException::new);
    if (!account.isEnabled()) {
      throw PlatformAccountConflictException.disabledSetupCode();
    }

    state.requireRecent(
        principal.platformAccountId(),
        principal.sessionId(),
        "setup-code:" + account.getId(),
        true);

    final boolean recovery = credentials.existsActiveByPlatformAccountId(account.getId());
    final var issued = state.issueCode(CodeKind.SETUP, account.getId());
    final Instant expiresAt = clock.instant().plus(issued.ttl());

    audit.recordCurrentActor(
        null,
        null,
        AuthAuditAction.PLATFORM_PASSKEY_SETUP_CODE_ISSUED,
        PlatformAccount.AUDIT_TYPE,
        account.getId(),
        AuditOrigin.PLATFORM_ADMINISTRATION,
        Map.of("recovery", recovery, "expiresAt", expiresAt.toString()));
    log.info(
        "[Auth] Platform passkey setup code issued, platformAccountId: {}, actorAccountId: {}, recovery: {}",
        account.getId(),
        principal.platformAccountId(),
        recovery);

    return new PlatformAccountSetupCodeResponse(issued.code(), expiresAt, recovery);
  }

  @Transactional(readOnly = true)
  public void cancel(final UUID targetAccountId, final JwtAuthenticatedPlatformAccount principal) {
    if (targetAccountId.equals(principal.platformAccountId())) {
      throw PlatformAccountConflictException.selfSetupCode();
    }
    if (!accounts.existsById(targetAccountId)) {
      throw new PlatformAccountNotFoundException();
    }

    state.clearCode(CodeKind.SETUP, targetAccountId);
    log.info(
        "[Auth] Platform passkey setup code cancelled, platformAccountId: {}, actorAccountId: {}",
        targetAccountId,
        principal.platformAccountId());
  }
}
