package ar.edu.utn.frvm.typeit.boero_api.authorization.services;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import java.util.List;
import java.util.Map;
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
      final AuditAction action,
      final Map<String, Object> before,
      final Map<String, Object> after) {
    if (!before.equals(after)) {
      auditEventRecorder.recordCurrentActor(
          role.getInstitution(),
          null,
          action,
          AuditEntityType.ROLE,
          role.getId(),
          AuditOrigin.administration(),
          Map.of("before", before, "after", after));
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void assignments(
      final Person person,
      final List<Map<String, Object>> before,
      final List<Map<String, Object>> after) {
    if (!before.equals(after)) {
      auditEventRecorder.recordCurrentActor(
          person.getInstitution(),
          person.getId(),
          AuditAction.PERSON_ROLES_CHANGED,
          AuditEntityType.PERSON,
          person.getId(),
          AuditOrigin.administration(),
          Map.of("before", before, "after", after));
    }
  }
}
