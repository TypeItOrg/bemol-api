package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.audit.AuthAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PasskeyCredential;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PasskeyNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PasskeyCredentialRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevokePasskeyUseCase {
  private final AuditEventRecorder auditRecorder;

  private final Clock clock;

  private final PasskeyCredentialRepository passkeyCredentialRepository;
  private final RecentAuthService recentAuthService;

  @Transactional
  public void execute(final JwtAuthenticatedUser principal, final UUID passkeyId) {
    if (principal.delegated() || principal.accountUserId().isEmpty()) {
      throw new ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException();
    }

    recentAuthService.requireRecent(principal.sessionId(), principal.userId());
    final var credential =
        passkeyCredentialRepository
            .findWithLockByIdAndUserId(passkeyId, principal.userId())
            .filter(c -> c.isActive())
            .orElseThrow(PasskeyNotFoundException::new);
    final var before = credential.auditSnapshot();
    credential.revoke(clock.instant());
    passkeyCredentialRepository.save(credential);
    auditRecorder.recordCurrentActor(
        credential.getUser().getInstitution(),
        principal.personId(),
        AuthAuditAction.PASSKEY_DELETED,
        PasskeyCredential.AUDIT_TYPE,
        credential.getId(),
        AuditOrigin.SELF_SERVICE,
        AuditChangeDetails.change(credential.getLabel(), before, credential.auditSnapshot()));
    log.info("[Auth] Passkey revoked, userId: {}", principal.userId());
  }
}
