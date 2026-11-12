package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.audit.InstitutionalAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.DependentNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonGuardianRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnlinkGuardianDependentUseCase {

  private final InstitutionRepository institutions;
  private final PersonGuardianRepository personGuardianRepository;
  private final AuditEventRecorder auditEventRecorder;

  /** Ends the link so the tutor stops representing the person; the row stays as history. */
  @Transactional
  public void execute(
      final UUID institutionId, final UUID tutorPersonId, final UUID dependentPersonId) {
    institutions.findByIdForUpdate(institutionId).orElseThrow(InstitutionNotFoundException::new);
    final PersonGuardian link =
        personGuardianRepository
            .findByInstitution_IdAndTutorPerson_IdAndDependentPerson_IdAndStatus(
                institutionId, tutorPersonId, dependentPersonId, GuardianLinkStatus.PENDING)
            .or(
                () ->
                    personGuardianRepository
                        .findByInstitution_IdAndTutorPerson_IdAndDependentPerson_IdAndStatus(
                            institutionId,
                            tutorPersonId,
                            dependentPersonId,
                            GuardianLinkStatus.ACTIVE))
            .orElseThrow(DependentNotFoundException::new);

    final var before = link.auditSnapshot();
    link.end();
    auditEventRecorder.record(
        link.getInstitution(),
        tutorPersonId,
        dependentPersonId,
        InstitutionalAuditAction.GUARDIAN_DEPENDENT_UNLINKED,
        PersonGuardian.AUDIT_TYPE,
        link.getId(),
        AuditChangeDetails.change("Vinculación", before, link.auditSnapshot()));
  }

  @Transactional
  public void cancelRequest(final UUID institutionId, final UUID tutorPersonId, final UUID linkId) {
    institutions.findByIdForUpdate(institutionId).orElseThrow(InstitutionNotFoundException::new);
    final var link =
        personGuardianRepository
            .findForUpdate(linkId, institutionId)
            .filter(value -> value.getTutorPerson().getId().equals(tutorPersonId))
            .filter(value -> value.getStatus() == GuardianLinkStatus.PENDING)
            .orElseThrow(DependentNotFoundException::new);
    final var before = link.auditSnapshot();
    link.end();
    auditEventRecorder.recordCurrentActor(
        link.getInstitution(),
        link.getDependentPersonId(),
        InstitutionalAuditAction.GUARDIAN_DEPENDENT_UNLINKED,
        PersonGuardian.AUDIT_TYPE,
        link.getId(),
        ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin.SELF_SERVICE,
        AuditChangeDetails.change("Vinculación", before, link.auditSnapshot()));
  }
}
