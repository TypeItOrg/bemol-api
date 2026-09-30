package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.DependentNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonGuardianRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnlinkGuardianDependentUseCase {

  private final PersonGuardianRepository personGuardianRepository;
  private final AuditEventRecorder auditEventRecorder;

  /** Removes only the guardianship link; the dependent's person and history are preserved. */
  @Transactional
  public void execute(
      final UUID institutionId, final UUID tutorPersonId, final UUID dependentPersonId) {
    final PersonGuardian link =
        personGuardianRepository
            .findByInstitution_IdAndTutorPerson_IdAndDependentPerson_Id(
                institutionId, tutorPersonId, dependentPersonId)
            .orElseThrow(DependentNotFoundException::new);

    personGuardianRepository.delete(link);
    auditEventRecorder.record(
        link.getInstitution(),
        tutorPersonId,
        dependentPersonId,
        AuditAction.GUARDIAN_DEPENDENT_UNLINKED,
        AuditEntityType.PERSON_GUARDIAN,
        link.getId());
  }
}
