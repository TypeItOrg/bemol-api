package ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditFeed;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperations;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditView;
import ar.edu.utn.frvm.typeit.bemol_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditActorKind;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditScope;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.GlobalAuditEventFilter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import org.springframework.data.jpa.domain.Specification;

public final class GlobalAuditEventSpecification {
  private GlobalAuditEventSpecification() {}

  public static Specification<AuditEvent> from(
      final GlobalAuditEventFilter filter, final AuditView view, final AuditOperations operations) {
    return (root, query, cb) -> {
      final var predicates = new ArrayList<Predicate>();
      predicates.add(visibleIn(view, root, cb, operations));
      if (filter.scope() == AuditScope.PLATFORM) {
        predicates.add(cb.isNull(root.get("institution")));
      }
      if (filter.scope() == AuditScope.INSTITUTION) {
        predicates.add(cb.isNotNull(root.get("institution")));
      }
      if (filter.institutionId() != null) {
        predicates.add(cb.equal(root.get("institution").get("id"), filter.institutionId()));
      }
      if (filter.actorKind() != null) {
        predicates.add(cb.equal(root.get("actorKind"), filter.actorKind()));
      }
      if (filter.actorId() != null) {
        final String actorAttribute =
            filter.actorKind() == AuditActorKind.PLATFORM
                ? "actorPlatformAccountId"
                : "actorPersonId";
        predicates.add(cb.equal(root.get(actorAttribute), filter.actorId()));
      }
      if (filter.subjectPersonId() != null) {
        predicates.add(cb.equal(root.get("subjectPersonId"), filter.subjectPersonId()));
      }
      if (filter.personId() != null) {
        predicates.add(
            AuditEventCriteria.relatedPerson(root, query, cb, cb.literal(filter.personId())));
      }
      if (filter.entityType() != null) {
        predicates.add(cb.equal(root.get("entityType"), filter.entityType()));
      }
      if (filter.entityId() != null) {
        predicates.add(cb.equal(root.get("entityId"), filter.entityId()));
      }
      if (filter.action() != null) {
        predicates.add(cb.equal(root.get("action"), filter.action()));
      }
      if (filter.module() != null) {
        predicates.add(root.get("action").in(operations.moduleCodes(filter.module())));
      }
      if (filter.mailType() != null) {
        predicates.add(
            cb.equal(
                AuditEventCriteria.detailText(root, cb, "mailType"), filter.mailType().getLabel()));
      }
      if (filter.requestId() != null && !filter.requestId().isBlank()) {
        predicates.add(cb.equal(root.get("requestId"), filter.requestId().strip()));
      }
      if (filter.from() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), filter.from()));
      }
      if (filter.to() != null) {
        predicates.add(cb.lessThan(root.get("occurredAt"), filter.to()));
      }
      predicates.addAll(
          GlobalAuditEventSearch.predicates(
              root,
              query,
              cb,
              GlobalAuditEventSearch.searchWords(filter.search()),
              view,
              operations.visible(view)));
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  private static Predicate visibleIn(
      final AuditView view,
      final Root<AuditEvent> root,
      final CriteriaBuilder cb,
      final AuditOperations operations) {
    if (view.feed() == AuditFeed.EMAIL) {
      return root.get("action").in(operations.visibleCodes(view));
    }

    // Historical actions without a current definition stay in the activity feed.
    return cb.not(root.get("action").in(operations.hiddenCodes(view)));
  }
}
