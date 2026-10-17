package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateInstitutionStatusUseCase {

  private final InstitutionRepository institutionRepository;
  private final SessionRevocationService sessionRevocationService;
  private final AuditEventRecorder auditEventRecorder;

  @Transactional
  public void execute(final UUID id, final boolean active) {
    final var institution =
        institutionRepository.findById(id).orElseThrow(InstitutionNotFoundException::new);

    final boolean before = institution.isActive();
    if (!institution.updateStatus(active)) {
      return;
    }
    institutionRepository.save(institution);
    auditEventRecorder.recordCurrentActor(
        institution,
        null,
        AuditAction.INSTITUTION_STATUS_CHANGED,
        AuditEntityType.INSTITUTION,
        id,
        AuditOrigin.administration(),
        Map.of(
            "entityName", institution.getName(),
            "before", Map.of("active", before),
            "after", Map.of("active", active)));

    if (!active) {
      sessionRevocationService.revokeInstitutionalSessionsForInstitution(id);
    }
  }
}
