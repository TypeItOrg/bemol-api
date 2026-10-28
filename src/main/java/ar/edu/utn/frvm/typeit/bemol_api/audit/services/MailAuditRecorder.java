package ar.edu.utn.frvm.typeit.bemol_api.audit.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces.AuditEventRepository;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailAuditContext;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailType;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
      final AuditAction outcome) {
    events.save(
        AuditEvent.record(
            institutions.getReferenceById(context.institutionId()),
            context.actorKind(),
            context.actorPersonId(),
            context.actorPlatformAccountId(),
            context.personId(),
            outcome,
            AuditEntityType.EMAIL,
            UUID.randomUUID(),
            false,
            context.requestId(),
            AuditOrigin.MAIL_DELIVERY,
            context.invitationId() == null
                ? Map.of("recipient", recipient, "mailType", type.getLabel())
                : Map.of(
                    "recipient",
                    recipient,
                    "mailType",
                    type.getLabel(),
                    "invitationId",
                    context.invitationId().toString()),
            clock.instant(),
            null));
  }
}
