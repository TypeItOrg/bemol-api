package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.CannotModifyOwnAccessException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import java.util.Map;
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
    final var user =
        userRepository
            .findByPerson_IdAndInstitution_Id(personId, institutionId)
            .orElseThrow(PersonNotFoundException::new);
    final boolean previousEnabled = user.isAccessEnabled();
    if (!user.updateAccess(enabled)) {
      return;
    }

    userRepository.save(user);
    auditEventRecorder.recordCurrentActor(
        user.getInstitution(),
        personId,
        AuditAction.INSTITUTIONAL_ACCESS_CHANGED,
        AuditEntityType.USER,
        user.getId(),
        AuditOrigin.administration(),
        Map.of("previousEnabled", previousEnabled, "enabled", enabled));
    if (!enabled) {
      sessionRevocationService.revokeInstitutionalSessionsForUser(user.getId());
    }
  }
}
