package ar.edu.utn.frvm.typeit.bemol_api.audit.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperations;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditView;
import ar.edu.utn.frvm.typeit.bemol_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.bemol_api.audit.exceptions.AuditEventNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces.AuditEventRepository;
import ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces.GlobalAuditEventSpecification;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.GlobalAuditEventFilter;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.GlobalAuditEventResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.GlobalAuditEventResponses;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GlobalAuditEventsUseCase {
  private final AuditEventRepository auditEventRepository;
  private final GlobalAuditEventResponses responses;
  private final AuditOperations operations;

  @Transactional(readOnly = true)
  public Page<GlobalAuditEventResponse> list(
      final GlobalAuditEventFilter filter, final Pageable pageable) {
    final var view = AuditView.platform(operations.feedOf(filter.module()));
    operations.validate(view, filter.action(), filter.module());
    operations.validateResource(filter.entityType());

    final Page<AuditEvent> page =
        auditEventRepository.findAll(
            GlobalAuditEventSpecification.from(filter, view, operations),
            AuditPages.recentFirst(pageable));

    return page.map(responses.enrichment(page.getContent()));
  }

  @Transactional(readOnly = true)
  public GlobalAuditEventResponse get(final UUID eventId) {
    final AuditEvent event =
        auditEventRepository.findById(eventId).orElseThrow(AuditEventNotFoundException::new);

    return responses.responseFor(event);
  }
}
