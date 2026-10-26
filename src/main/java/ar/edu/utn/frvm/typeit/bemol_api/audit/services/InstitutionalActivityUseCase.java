package ar.edu.utn.frvm.typeit.bemol_api.audit.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditActorKind;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.envers.EnversEventHistory;
import ar.edu.utn.frvm.typeit.bemol_api.audit.exceptions.AuditEventNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces.AuditEventRepository;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.GlobalAuditEventResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.InstitutionalActivityFilter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstitutionalActivityUseCase {
  private final AuditEventRepository events;
  private final GlobalAuditEventResponses responses;
  private final EnversEventHistory history;

  @Transactional(readOnly = true)
  public Page<GlobalAuditEventResponse> list(
      final UUID institutionId,
      final InstitutionalActivityFilter filter,
      final boolean mailView,
      final Pageable pageable) {
    final var page =
        events.findAll(
            scoped(institutionId, filter, mailView),
            PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), 50),
                Sort.by(Sort.Direction.DESC, "occurredAt", "id")));
    return page.map(responses.enrichment(page.getContent())).map(this::institutionalResponse);
  }

  @Transactional(readOnly = true)
  public GlobalAuditEventResponse get(
      final UUID institutionId, final UUID eventId, final boolean mailView) {
    final var filter = new InstitutionalActivityFilter(null, null, null, null, null, null);
    final var event =
        events
            .findOne(
                scoped(institutionId, filter, mailView)
                    .and((root, query, cb) -> cb.equal(root.get("id"), eventId)))
            .orElseThrow(AuditEventNotFoundException::new);
    return institutionalResponse(
        responses.enrichment(List.of(event)).apply(event).withHistory(history.find(event)));
  }

  private Specification<AuditEvent> scoped(
      final UUID institutionId, final InstitutionalActivityFilter filter, final boolean mailView) {
    return GlobalAuditEventSpecification.from(filter.scoped(institutionId, mailView), false)
        .and(
            (root, query, cb) ->
                mailView
                    ? cb.equal(root.get("entityType"), AuditEntityType.EMAIL)
                    : cb.notEqual(root.get("entityType"), AuditEntityType.PLATFORM_ACCOUNT));
  }

  private GlobalAuditEventResponse institutionalResponse(final GlobalAuditEventResponse event) {
    final boolean platformActor = event.actorKind() == AuditActorKind.PLATFORM;
    final Map<String, Object> details = new HashMap<>();
    if (event.entityType() == AuditEntityType.EMAIL) {
      for (final String key : List.of("recipient", "mailType")) {
        if (event.details().get(key) instanceof String value) {
          details.put(key, value);
        }
      }
    } else {
      details.putAll(event.details());
    }
    return new GlobalAuditEventResponse(
        event.id(),
        event.actorKind(),
        platformActor ? null : event.actorId(),
        platformActor ? "Administración de plataforma" : event.actorName(),
        event.subjectPersonId(),
        event.subjectName(),
        event.institutionId(),
        event.institutionName(),
        event.action(),
        event.entityType(),
        event.entityId(),
        event.actedOnBehalf(),
        null,
        null,
        details,
        event.occurredAt(),
        event.history());
  }
}
