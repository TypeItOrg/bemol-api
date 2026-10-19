package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.PasskeyNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.PasskeyCredentialRepository;
import java.time.Clock;
import java.util.Map;
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
    recentAuthService.requireRecent(principal.sessionId(), principal.userId());
    final var credential =
        passkeyCredentialRepository
            .findWithLockByIdAndUserId(passkeyId, principal.userId())
            .filter(c -> c.isActive())
            .orElseThrow(PasskeyNotFoundException::new);
    credential.revoke(clock.instant());
    passkeyCredentialRepository.save(credential);
    auditRecorder.recordCurrentActor(
        credential.getUser().getInstitution(),
        principal.personId(),
        AuditAction.PASSKEY_DELETED,
        AuditEntityType.PASSKEY,
        credential.getId(),
        AuditOrigin.SELF_SERVICE,
        Map.of());
    log.info("[Auth] Passkey revoked, userId: {}", principal.userId());
  }
}
