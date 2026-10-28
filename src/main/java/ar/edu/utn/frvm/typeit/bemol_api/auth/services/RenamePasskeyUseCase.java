package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PasskeyNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyResponse;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RenamePasskeyUseCase {
  private final AuditEventRecorder auditRecorder;

  private final PasskeyCredentialRepository passkeyCredentialRepository;

  @Transactional
  public PasskeyResponse execute(
      final JwtAuthenticatedUser principal, final UUID passkeyId, final String label) {
    if (principal.delegated() || principal.accountUserId().isEmpty()) {
      throw new ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException();
    }

    final var credential =
        passkeyCredentialRepository
            .findWithLockByIdAndUserId(passkeyId, principal.userId())
            .filter(c -> c.isActive())
            .orElseThrow(PasskeyNotFoundException::new);
    final var previousLabel = credential.getLabel();
    credential.rename(label);
    final var saved = passkeyCredentialRepository.save(credential);
    if (!java.util.Objects.equals(previousLabel, credential.getLabel())) {
      auditRecorder.recordCurrentActor(
          credential.getUser().getInstitution(),
          principal.personId(),
          AuditAction.PASSKEY_UPDATED,
          AuditEntityType.PASSKEY,
          credential.getId(),
          AuditOrigin.SELF_SERVICE,
          Map.of("changedFields", java.util.List.of("label")));
    }
    log.info("[Auth] Passkey renamed, userId: {}", principal.userId());
    return PasskeyResponse.from(saved);
  }
}
