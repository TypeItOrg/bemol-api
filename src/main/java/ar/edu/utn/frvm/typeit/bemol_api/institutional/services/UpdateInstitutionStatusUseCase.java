package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.audit.InstitutionalAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
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

    final var before = institution.statusAuditSnapshot();
    if (!institution.updateStatus(active)) {
      return;
    }
    institutionRepository.save(institution);
    auditEventRecorder.recordCurrentActor(
        institution,
        null,
        InstitutionalAuditAction.INSTITUTION_STATUS_CHANGED,
        Institution.AUDIT_TYPE,
        id,
        AuditOrigin.administration(),
        AuditChangeDetails.change(
            institution.getName(), before, institution.statusAuditSnapshot()));

    if (!active) {
      sessionRevocationService.revokeInstitutionalSessionsForInstitution(id);
    }
  }
}
