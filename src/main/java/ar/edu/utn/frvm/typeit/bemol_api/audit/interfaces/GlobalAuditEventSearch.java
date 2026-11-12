package ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperation;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditView;
import ar.edu.utn.frvm.typeit.bemol_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public final class GlobalAuditEventSearch {
  private static final List<List<String>> DETAIL_SEARCH_PATHS =
      List.of(
          List.of("entityName"),
          List.of("recipient"),
          List.of("mailType"),
          List.of("before", "name"),
          List.of("after", "name"));

  private GlobalAuditEventSearch() {}

  static List<Predicate> predicates(
      final Root<AuditEvent> root,
      final CriteriaQuery<?> query,
      final CriteriaBuilder cb,
      final List<String> words,
      final AuditView view,
      final List<AuditOperation> operations) {
    if (words.isEmpty()) {
      return List.of();
    }

    final Expression<String> institutionName = root.join("institution", JoinType.LEFT).get("name");
    return words.stream()
        .map(word -> searchWord(root, query, cb, institutionName, word, view, operations))
        .toList();
  }

  public static List<String> searchWords(@Nullable final String search) {
    if (search == null || search.isBlank()) {
      return List.of();
    }
    return Arrays.asList(normalize(search).strip().split("\\s+"));
  }

  public static String actionSearchText(final AuditOperation action) {
    return normalize(action.getLabel() + " " + action.getModule().label());
  }

  public static String normalize(final String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT);
  }

  private static Predicate searchWord(
      final Root<AuditEvent> event,
      final CriteriaQuery<?> query,
      final CriteriaBuilder cb,
      final Expression<String> institutionName,
      final String word,
      final AuditView view,
      final List<AuditOperation> operations) {
    final var personQuery = personSubquery(event, query, cb, word);
    final var accountQuery = accountSubquery(event, query, cb, word);
    final var roleQuery = roleSubquery(event, query, cb, word);

    return cb.or(
        actionMatches(event, cb, word, operations),
        cb.exists(personQuery),
        accountMatches(cb, accountQuery, view),
        cb.exists(roleQuery),
        AuditEventCriteria.containsWord(cb, institutionName, word),
        detailMatches(event, cb, word));
  }

  private static Predicate actionMatches(
      final Root<AuditEvent> event,
      final CriteriaBuilder cb,
      final String word,
      final List<AuditOperation> operations) {
    final var actions = actionsMatching(word, operations);
    return actions.isEmpty() ? cb.disjunction() : event.get("action").in(actions);
  }

  private static Predicate accountMatches(
      final CriteriaBuilder cb, final Subquery<Integer> accountQuery, final AuditView view) {
    if (view.institutional()) {
      return cb.disjunction();
    }
    return cb.exists(accountQuery);
  }

  private static Predicate detailMatches(
      final Root<AuditEvent> event, final CriteriaBuilder cb, final String word) {
    final List<Predicate> predicates = new ArrayList<>();
    for (final List<String> path : DETAIL_SEARCH_PATHS) {
      predicates.add(detailPathMatches(event, cb, path, word));
    }
    predicates.add(assignmentRoleNamesMatch(event, cb, word));

    return cb.or(predicates.toArray(Predicate[]::new));
  }

  private static Predicate detailPathMatches(
      final Root<AuditEvent> event,
      final CriteriaBuilder cb,
      final List<String> path,
      final String word) {
    final var detailText = AuditEventCriteria.detailText(event, cb, path.toArray(String[]::new));
    return AuditEventCriteria.containsWord(cb, detailText, word);
  }

  private static Predicate assignmentRoleNamesMatch(
      final Root<AuditEvent> event, final CriteriaBuilder cb, final String word) {
    return AuditEventCriteria.containsWord(cb, assignmentRoleNames(event, cb), word);
  }

  private static Subquery<Integer> personSubquery(
      final Root<AuditEvent> event,
      final CriteriaQuery<?> query,
      final CriteriaBuilder cb,
      final String word) {
    final var personQuery = query.subquery(Integer.class);
    final var person = personQuery.from(Person.class);
    personQuery
        .select(cb.literal(1))
        .where(
            AuditEventCriteria.relatedPerson(event, personQuery, cb, person.get("id")),
            AuditEventCriteria.containsWord(
                cb,
                cb.concat(cb.concat(person.get("firstName"), " "), person.get("lastName")),
                word));
    return personQuery;
  }

  private static Subquery<Integer> accountSubquery(
      final Root<AuditEvent> event,
      final CriteriaQuery<?> query,
      final CriteriaBuilder cb,
      final String word) {
    final var accountQuery = query.subquery(Integer.class);
    final var account = accountQuery.from(PlatformAccount.class);
    accountQuery
        .select(cb.literal(1))
        .where(
            cb.or(
                cb.equal(account.get("id"), event.get("actorPlatformAccountId")),
                cb.and(
                    cb.equal(event.get("entityType"), PlatformAccount.AUDIT_TYPE.name()),
                    cb.equal(account.get("id"), event.get("entityId")))),
            AuditEventCriteria.containsWord(
                cb, cb.concat(cb.concat(account.get("name"), " "), account.get("lastName")), word));
    return accountQuery;
  }

  private static Subquery<Integer> roleSubquery(
      final Root<AuditEvent> event,
      final CriteriaQuery<?> query,
      final CriteriaBuilder cb,
      final String word) {
    final var roleQuery = query.subquery(Integer.class);
    final var role = roleQuery.from(Role.class);
    roleQuery
        .select(cb.literal(1))
        .where(
            cb.equal(event.get("entityType"), Role.AUDIT_TYPE.name()),
            cb.equal(role.get("id"), event.get("entityId")),
            AuditEventCriteria.containsWord(cb, role.get("name"), word));
    return roleQuery;
  }

  private static List<String> actionsMatching(
      final String word, final List<AuditOperation> operations) {
    return operations.stream()
        .filter(action -> actionSearchText(action).contains(word))
        .map(entry -> entry.name())
        .toList();
  }

  private static Expression<String> assignmentRoleNames(
      final Root<AuditEvent> event, final CriteriaBuilder cb) {
    return cb.concat(
        "",
        cb.function(
            "jsonb_path_query_array",
            String.class,
            event.get("details"),
            cb.literal("$.**.roleName")));
  }
}
