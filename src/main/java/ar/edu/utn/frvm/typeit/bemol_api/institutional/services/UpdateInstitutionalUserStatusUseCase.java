package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.audit.AuthAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.CannotModifyOwnAccessException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateInstitutionalUserStatusUseCase {

  private final InstitutionRepository institutionRepository;
  private final UserRepository userRepository;
  private final SessionRevocationService sessionRevocationService;

  private final AuditEventRecorder auditEventRecorder;
  private final ar.edu.utn.frvm.typeit.bemol_api.auth.services.AccountAccessAuthorization access;

  @Transactional
  public void execute(
      final UUID institutionId,
      final UUID actorPersonId,
      final UUID personId,
      final boolean enabled) {
    if (actorPersonId.equals(personId)) {
      throw new CannotModifyOwnAccessException();
    }

    institutionRepository
        .findByIdForUpdate(institutionId)
        .orElseThrow(InstitutionNotFoundException::new);
    access.requirePermission(
        institutionId,
        actorPersonId,
        ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode
            .INSTITUTION_USER_STATUS_UPDATE);
    final var user =
        userRepository
            .findByPersonAndInstitutionForUpdate(personId, institutionId)
            .orElseThrow(PersonNotFoundException::new);
    final var before = user.auditSnapshot();
    if (!user.updateAccess(enabled)) {
      return;
    }

    userRepository.save(user);
    auditEventRecorder.recordCurrentActor(
        user.getInstitution(),
        personId,
        AuthAuditAction.INSTITUTIONAL_ACCESS_CHANGED,
        User.AUDIT_TYPE,
        user.getId(),
        AuditOrigin.administration(),
        AuditChangeDetails.change("Cuenta institucional", before, user.auditSnapshot()));
    if (!enabled) {
      sessionRevocationService.revokeInstitutionalSessionsForUser(user.getId());
    }
  }
}
