package ar.edu.utn.frvm.typeit.boero_api.audit.services;

import ar.edu.utn.frvm.typeit.boero_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.interfaces.AuditEventRepository;
import ar.edu.utn.frvm.typeit.boero_api.common.logging.RequestLoggingFilter;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends to the audit log inside the caller's transaction, so an event exists if and only if the
 * audited change was committed.
 */
@Component
@RequiredArgsConstructor
public class AuditEventRecorder {

  private final AuditEventRepository auditEventRepository;
  private final Clock clock;

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      final Institution institution,
      final UUID actorPersonId,
      final UUID subjectPersonId,
      final AuditAction action,
      final AuditEntityType entityType,
      final UUID entityId) {
    auditEventRepository.save(
        AuditEvent.record(
            institution,
            actorPersonId,
            subjectPersonId,
            action,
            entityType,
            entityId,
            MDC.get(RequestLoggingFilter.MDC_REQUEST_ID_KEY),
            clock.instant()));
  }
}
