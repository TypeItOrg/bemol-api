package ar.edu.utn.frvm.typeit.bemol_api.audit.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperation;
import ar.edu.utn.frvm.typeit.bemol_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces.AuditEventRepository;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailAuditContext;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailType;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MailAuditRecorder {
  private final AuditEventRepository events;
  private final InstitutionRepository institutions;
  private final Clock clock;

  // SMTP is already an external side effect; its result must survive unrelated rollbacks.
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      final MailAuditContext context,
      final MailType type,
      final String recipient,
      final AuditOperation outcome) {
    final @Nullable Institution institution = resolveInstitution(context);
    final Map<String, Object> details = mailDetails(context, type, recipient);

    final AuditEvent event = mailEvent(context, outcome, institution, details);

    events.save(event);
  }

  private @Nullable Institution resolveInstitution(final MailAuditContext context) {
    if (context.institutionId() == null) {
      return null;
    }
    return institutions.getReferenceById(context.institutionId());
  }

  private static Map<String, Object> mailDetails(
      final MailAuditContext context, final MailType type, final String recipient) {
    final Map<String, Object> details = new LinkedHashMap<>();
    details.put("recipient", recipient);
    details.put("mailType", type.getLabel());
    if (context.invitationId() != null) {
      details.put("invitationId", context.invitationId().toString());
    }
    return details;
  }

  private AuditEvent mailEvent(
      final MailAuditContext context,
      final AuditOperation outcome,
      final @Nullable Institution institution,
      final Map<String, Object> details) {
    final UUID entityId = UUID.randomUUID();

    return AuditEvent.record(
        institution,
        context.actorKind(),
        context.actorPersonId(),
        context.actorPlatformAccountId(),
        context.personId(),
        outcome,
        MailAuditAction.AUDIT_TYPE,
        entityId,
        false,
        context.requestId(),
        AuditOrigin.MAIL_DELIVERY,
        details,
        clock.instant());
  }
}
