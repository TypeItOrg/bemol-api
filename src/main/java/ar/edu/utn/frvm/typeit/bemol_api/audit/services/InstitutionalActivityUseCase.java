package ar.edu.utn.frvm.typeit.bemol_api.audit.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditFeed;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperations;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditView;
import ar.edu.utn.frvm.typeit.bemol_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditActorKind;
import ar.edu.utn.frvm.typeit.bemol_api.audit.exceptions.AuditEventNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces.AuditEventRepository;
import ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces.GlobalAuditEventSpecification;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.GlobalAuditEventResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.GlobalAuditEventResponses;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.InstitutionalActivityFilter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstitutionalActivityUseCase {
  private static final String PLATFORM_ACTOR_NAME = "Administración de plataforma";
  private static final List<String> EMAIL_DETAIL_KEYS =
      List.of("recipient", "mailType", "invitationId");

  private final AuditEventRepository events;
  private final GlobalAuditEventResponses responses;
  private final AuditOperations operations;

  @Transactional(readOnly = true)
  public Page<GlobalAuditEventResponse> list(
      final UUID institutionId,
      final AuditFeed feed,
      final InstitutionalActivityFilter filter,
      final Pageable pageable) {
    final var view = AuditView.institution(feed);
    operations.validate(view, filter.action(), filter.module());

    final var specification = scoped(institutionId, view, filter);
    final var page = events.findAll(specification, AuditPages.recentFirst(pageable));
    final var enricher = responses.enrichment(page.getContent());

    return page.map(event -> institutionalResponse(enricher.apply(event), feed));
  }

  @Transactional(readOnly = true)
  public GlobalAuditEventResponse get(
      final UUID institutionId, final AuditFeed feed, final UUID eventId) {
    final var view = AuditView.institution(feed);
    final var specification =
        scoped(institutionId, view, InstitutionalActivityFilter.empty()).and(withId(eventId));

    final var event = events.findOne(specification).orElseThrow(AuditEventNotFoundException::new);

    return institutionalResponse(responses.responseFor(event), feed);
  }

  private Specification<AuditEvent> scoped(
      final UUID institutionId, final AuditView view, final InstitutionalActivityFilter filter) {
    return GlobalAuditEventSpecification.from(filter.scoped(institutionId), view, operations);
  }

  private static Specification<AuditEvent> withId(final UUID eventId) {
    return (root, query, cb) -> cb.equal(root.get("id"), eventId);
  }

  private static GlobalAuditEventResponse institutionalResponse(
      final GlobalAuditEventResponse event, final AuditFeed feed) {
    return event
        .withActor(actorId(event), actorName(event))
        .withDetails(detailsFor(event, feed))
        .withoutRequestContext();
  }

  private static @Nullable UUID actorId(final GlobalAuditEventResponse event) {
    if (event.actorKind() == AuditActorKind.PLATFORM) {
      return null;
    }
    return event.actorId();
  }

  private static @Nullable String actorName(final GlobalAuditEventResponse event) {
    if (event.actorKind() == AuditActorKind.PLATFORM) {
      return PLATFORM_ACTOR_NAME;
    }
    return event.actorName();
  }

  private static Map<String, Object> detailsFor(
      final GlobalAuditEventResponse event, final AuditFeed feed) {
    if (feed != AuditFeed.EMAIL) {
      return new HashMap<>(event.details());
    }

    final Map<String, Object> details = new HashMap<>();
    for (final String key : EMAIL_DETAIL_KEYS) {
      if (event.details().get(key) instanceof String value) {
        details.put(key, value);
      }
    }
    return details;
  }
}
