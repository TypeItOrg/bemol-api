package ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.query.criteria.JpaExpression;

final class AuditEventCriteria {
  private AuditEventCriteria() {}

  static Predicate relatedPerson(
      final Root<AuditEvent> event,
      final CommonAbstractCriteria query,
      final CriteriaBuilder cb,
      final Expression<UUID> personId) {
    return cb.or(
        directPersonMatches(event, cb, personId),
        relatedPersonIdsMatch(event, cb, personId),
        guardianLinkExists(event, query, cb, personId));
  }

  private static Predicate directPersonMatches(
      final Root<AuditEvent> event, final CriteriaBuilder cb, final Expression<UUID> personId) {
    return cb.or(
        cb.equal(event.get("actorPersonId"), personId),
        cb.equal(event.get("subjectPersonId"), personId));
  }

  private static Predicate relatedPersonIdsMatch(
      final Root<AuditEvent> event, final CriteriaBuilder cb, final Expression<UUID> personId) {
    final var relatedPersonIds =
        cb.function(
            "jsonb_extract_path",
            Object.class,
            event.get("details"),
            cb.literal("relatedPersonIds"));
    final var personIdText = ((JpaExpression<UUID>) personId).cast(String.class);

    return cb.isTrue(cb.function("jsonb_exists", Boolean.class, relatedPersonIds, personIdText));
  }

  private static Predicate guardianLinkExists(
      final Root<AuditEvent> event,
      final CommonAbstractCriteria query,
      final CriteriaBuilder cb,
      final Expression<UUID> personId) {
    final var linkQuery = query.subquery(Integer.class);
    final var link = linkQuery.from(PersonGuardian.class);

    linkQuery
        .select(cb.literal(1))
        .where(
            cb.equal(event.get("entityType"), PersonGuardian.AUDIT_TYPE.name()),
            cb.equal(link.get("id"), event.get("entityId")),
            guardianParticipantMatches(link, cb, personId));

    return cb.exists(linkQuery);
  }

  private static Predicate guardianParticipantMatches(
      final Root<PersonGuardian> link, final CriteriaBuilder cb, final Expression<UUID> personId) {
    return cb.or(
        cb.equal(link.get("tutorPerson").get("id"), personId),
        cb.equal(link.get("dependentPerson").get("id"), personId));
  }

  static Expression<String> detailText(
      final Root<AuditEvent> event, final CriteriaBuilder cb, final String... path) {
    final List<Expression<?>> pathArguments = new ArrayList<>();
    pathArguments.add(event.get("details"));
    for (final String key : path) {
      pathArguments.add(cb.literal(key));
    }

    return cb.function(
        "jsonb_extract_path_text", String.class, pathArguments.toArray(Expression<?>[]::new));
  }

  static Predicate containsWord(
      final CriteriaBuilder cb, final Expression<String> value, final String word) {
    final var normalizedValue = cb.function("bemol_normalize_text", String.class, value);
    final var wordPosition =
        cb.function("strpos", Integer.class, normalizedValue, cb.literal(word));

    return cb.greaterThan(wordPosition, 0);
  }
}
