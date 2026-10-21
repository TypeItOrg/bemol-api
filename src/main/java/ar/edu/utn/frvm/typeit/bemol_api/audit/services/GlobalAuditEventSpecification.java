package ar.edu.utn.frvm.typeit.bemol_api.audit.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditActorKind;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditModule;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditScope;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.GlobalAuditEventFilter;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.query.criteria.JpaExpression;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

final class GlobalAuditEventSpecification {
  private GlobalAuditEventSpecification() {}

  static Specification<AuditEvent> from(final GlobalAuditEventFilter filter) {
    return (root, query, cb) -> {
      final List<Predicate> predicates = new ArrayList<>();
      if (filter.module() != AuditModule.EMAILS) {
        predicates.add(cb.notEqual(root.get("entityType"), AuditEntityType.EMAIL));
      }
      if (filter.mailType() != null) {
        predicates.add(cb.equal(detailText(root, cb, "mailType"), filter.mailType().getLabel()));
      }
      if (filter.scope() == AuditScope.PLATFORM) {
        predicates.add(cb.isNull(root.get("institution")));
      } else if (filter.scope() == AuditScope.INSTITUTION) {
        predicates.add(cb.isNotNull(root.get("institution")));
      }
      if (filter.institutionId() != null) {
        predicates.add(cb.equal(root.get("institution").get("id"), filter.institutionId()));
      }
      if (filter.actorKind() != null) {
        predicates.add(cb.equal(root.get("actorKind"), filter.actorKind()));
      }
      if (filter.actorId() != null) {
        predicates.add(
            cb.equal(
                root.get(
                    filter.actorKind() == AuditActorKind.PLATFORM
                        ? "actorPlatformAccountId"
                        : "actorPersonId"),
                filter.actorId()));
      }
      if (filter.subjectPersonId() != null) {
        predicates.add(cb.equal(root.get("subjectPersonId"), filter.subjectPersonId()));
      }
      if (filter.personId() != null) {
        predicates.add(relatedPerson(root, query, cb, cb.literal(filter.personId())));
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
        predicates.add(
            root.get("action")
                .in(
                    Arrays.stream(AuditAction.values())
                        .filter(action -> action.getModule() == filter.module())
                        .toList()));
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
      final var words = searchWords(filter.search());
      if (!words.isEmpty()) {
        final Expression<String> institutionName =
            root.join("institution", JoinType.LEFT).get("name");
        for (final String word : words) {
          predicates.add(searchWord(root, query, cb, institutionName, word));
        }
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  private static Predicate relatedPerson(
      final Root<AuditEvent> event,
      final CommonAbstractCriteria query,
      final CriteriaBuilder cb,
      final Expression<UUID> personId) {
    final var linkQuery = query.subquery(Integer.class);
    final var link = linkQuery.from(PersonGuardian.class);
    linkQuery
        .select(cb.literal(1))
        .where(
            cb.equal(event.get("entityType"), AuditEntityType.PERSON_GUARDIAN),
            cb.equal(link.get("id"), event.get("entityId")),
            cb.or(
                cb.equal(link.get("tutorPerson").get("id"), personId),
                cb.equal(link.get("dependentPerson").get("id"), personId)));
    return cb.or(
        cb.equal(event.get("actorPersonId"), personId),
        cb.equal(event.get("subjectPersonId"), personId),
        cb.isTrue(
            cb.function(
                "jsonb_exists",
                Boolean.class,
                cb.function(
                    "jsonb_extract_path",
                    Object.class,
                    event.get("details"),
                    cb.literal("relatedPersonIds")),
                ((JpaExpression<UUID>) personId).cast(String.class))),
        cb.exists(linkQuery));
  }

  private static Predicate searchWord(
      final Root<AuditEvent> event,
      final CriteriaQuery<?> query,
      final CriteriaBuilder cb,
      final Expression<String> institutionName,
      final String word) {
    final var personQuery = query.subquery(Integer.class);
    final var person = personQuery.from(Person.class);
    personQuery
        .select(cb.literal(1))
        .where(
            relatedPerson(event, personQuery, cb, person.get("id")),
            containsWord(
                cb,
                cb.concat(cb.concat(person.get("firstName"), " "), person.get("lastName")),
                word));

    final var accountQuery = query.subquery(Integer.class);
    final var account = accountQuery.from(PlatformAccount.class);
    accountQuery
        .select(cb.literal(1))
        .where(
            cb.or(
                cb.equal(account.get("id"), event.get("actorPlatformAccountId")),
                cb.and(
                    cb.equal(event.get("entityType"), AuditEntityType.PLATFORM_ACCOUNT),
                    cb.equal(account.get("id"), event.get("entityId")))),
            containsWord(
                cb, cb.concat(cb.concat(account.get("name"), " "), account.get("lastName")), word));

    final var roleQuery = query.subquery(Integer.class);
    final var role = roleQuery.from(Role.class);
    roleQuery
        .select(cb.literal(1))
        .where(
            cb.equal(event.get("entityType"), AuditEntityType.ROLE),
            cb.equal(role.get("id"), event.get("entityId")),
            containsWord(cb, role.get("name"), word));

    final var matchingActions =
        Arrays.stream(AuditAction.values())
            .filter(action -> actionSearchText(action).contains(word))
            .toList();
    final Expression<String> assignmentNames =
        cb.concat(
            "",
            cb.function(
                "jsonb_path_query_array",
                String.class,
                event.get("details"),
                cb.literal("$.**.roleName")));
    return cb.or(
        matchingActions.isEmpty() ? cb.disjunction() : event.get("action").in(matchingActions),
        cb.exists(personQuery),
        cb.exists(accountQuery),
        cb.exists(roleQuery),
        containsWord(cb, institutionName, word),
        containsWord(cb, detailText(event, cb, "entityName"), word),
        containsWord(cb, assignmentNames, word),
        containsWord(cb, detailText(event, cb, "recipient"), word),
        containsWord(cb, detailText(event, cb, "mailType"), word),
        containsWord(cb, detailText(event, cb, "before", "name"), word),
        containsWord(cb, detailText(event, cb, "after", "name"), word));
  }

  private static Expression<String> detailText(
      final Root<AuditEvent> event, final CriteriaBuilder cb, final String... path) {
    final List<Expression<?>> arguments = new ArrayList<>();
    arguments.add(event.get("details"));
    for (final String key : path) {
      arguments.add(cb.literal(key));
    }
    return cb.function(
        "jsonb_extract_path_text", String.class, arguments.toArray(Expression<?>[]::new));
  }

  private static Predicate containsWord(
      final CriteriaBuilder cb, final Expression<String> value, final String word) {
    return cb.greaterThan(
        cb.function(
            "strpos",
            Integer.class,
            cb.function("bemol_normalize_text", String.class, value),
            cb.literal(word)),
        0);
  }

  static List<String> searchWords(@Nullable final String search) {
    return search == null || search.isBlank()
        ? List.of()
        : Arrays.asList(normalize(search).strip().split("\\s+"));
  }

  static String actionSearchText(final AuditAction action) {
    return normalize(action.getLabel() + " " + action.getModule().getLabel());
  }

  static String normalize(final String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT);
  }
}
