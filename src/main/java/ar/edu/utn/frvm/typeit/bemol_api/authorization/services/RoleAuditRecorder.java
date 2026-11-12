package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperation;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.audit.AuthorizationAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RoleAuditRecorder {
  private final AuditEventRecorder auditEventRecorder;

  @Transactional(propagation = Propagation.MANDATORY)
  public void role(
      final Role role,
      final AuditOperation action,
      final AuditSnapshot.Snapshot before,
      final AuditSnapshot.Snapshot after) {
    auditEventRecorder.recordSnapshotChange(
        role.getInstitution(),
        null,
        action,
        Role.AUDIT_TYPE,
        role.getId(),
        role.getName(),
        before,
        after);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void assignments(
      final Person person,
      final AuditSnapshot.Snapshot before,
      final AuditSnapshot.Snapshot after) {
    if (!before.values().equals(after.values())) {
      auditEventRecorder.recordCurrentActor(
          person.getInstitution(),
          person.getId(),
          AuthorizationAuditAction.PERSON_ROLES_CHANGED,
          Person.AUDIT_TYPE,
          person.getId(),
          AuditOrigin.administration(),
          AuditChangeDetails.change("Roles y alcances", before, after));
    }
  }
}
