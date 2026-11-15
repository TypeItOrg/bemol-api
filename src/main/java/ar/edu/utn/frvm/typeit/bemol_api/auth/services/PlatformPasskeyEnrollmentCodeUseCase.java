package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.audit.AuthAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidLoginAttemptException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformPasskeyCodeResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.PlatformPasskeyStateStore.CodeKind;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues and cancels single-use codes to add another device's passkey to the caller account. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformPasskeyEnrollmentCodeUseCase {
  private final PlatformAccountRepository accounts;
  private final PlatformWebAuthnOperations operations;
  private final PlatformPasskeyStateStore state;
  private final AuditEventRecorder audit;
  private final Clock clock;

  @Transactional
  public PlatformPasskeyCodeResponse issue(final JwtAuthenticatedPlatformAccount principal) {
    final var account =
        accounts
            .findWithLockById(principal.platformAccountId())
            .filter(value -> value.isEnabled())
            .orElseThrow(InvalidLoginAttemptException::new);
    operations.requireCapacity(account);

    state.requireRecent(account.getId(), principal.sessionId(), "enroll", true);

    final var issued = state.issueCode(CodeKind.ENROLLMENT, account.getId());
    final Instant expiresAt = clock.instant().plus(issued.ttl());

    audit.recordCurrentActor(
        null,
        null,
        AuthAuditAction.PLATFORM_PASSKEY_ENROLLMENT_CODE_ISSUED,
        PlatformAccount.AUDIT_TYPE,
        account.getId(),
        AuditOrigin.SELF_SERVICE,
        Map.of("expiresAt", expiresAt.toString()));
    log.info(
        "[Auth] Platform passkey enrollment code issued, platformAccountId: {}", account.getId());

    return new PlatformPasskeyCodeResponse(issued.code(), expiresAt);
  }

  @Transactional(readOnly = true)
  public void cancel(final JwtAuthenticatedPlatformAccount principal) {
    accounts
        .findById(principal.platformAccountId())
        .filter(value -> value.isEnabled())
        .orElseThrow(InvalidLoginAttemptException::new);

    state.clearCode(CodeKind.ENROLLMENT, principal.platformAccountId());
    log.info(
        "[Auth] Platform passkey enrollment code cancelled, platformAccountId: {}",
        principal.platformAccountId());
  }
}
