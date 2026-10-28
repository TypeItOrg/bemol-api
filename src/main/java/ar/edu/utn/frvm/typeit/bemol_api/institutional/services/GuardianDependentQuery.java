package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

final class GuardianDependentQuery {
  private GuardianDependentQuery() {}

  static Specification<PersonGuardian> matching(
      final UUID institutionId,
      final UUID tutorId,
      final String search,
      final @Nullable GuardianLinkStatus status,
      final @Nullable GuardianRelationship relationship,
      final @Nullable Boolean primaryContact,
      final String sortField,
      final String sortDirection) {
    return (root, query, cb) -> {
      final var person = root.join("dependentPerson", JoinType.LEFT);
      final var active = cb.equal(root.get("status"), GuardianLinkStatus.ACTIVE);
      // Search and ordering must not leak names before institutional approval.
      final Expression<String> firstName =
          cb.<String>selectCase()
              .when(active, person.get("firstName"))
              .otherwise(root.get("declaredFirstName"));
      final Expression<String> lastName =
          cb.<String>selectCase()
              .when(active, person.get("lastName"))
              .otherwise(root.get("declaredLastName"));
      final Expression<String> documentNumber =
          cb.<String>selectCase()
              .when(active, person.get("documentNumber"))
              .otherwise(root.get("declaredDocumentNumber"));
      final List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("institution").get("id"), institutionId));
      predicates.add(cb.equal(root.get("tutorPerson").get("id"), tutorId));
      predicates.add(
          root.get("status")
              .in(
                  GuardianLinkStatus.PENDING,
                  GuardianLinkStatus.ACTIVE,
                  GuardianLinkStatus.REJECTED,
                  GuardianLinkStatus.ENDED));

      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      if (relationship != null) {
        predicates.add(cb.equal(root.get("relationship"), relationship));
      }
      if (primaryContact != null) {
        predicates.add(cb.equal(root.get("primaryContact"), primaryContact));
      }
      if (!search.isBlank()) {
        final String pattern =
            "%"
                + Normalizer.normalize(search.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_")
                + "%";
        predicates.add(
            cb.or(
                cb.like(
                    cb.function(
                        "translate",
                        String.class,
                        cb.lower(
                            cb.concat(
                                cb.concat(cb.coalesce(firstName, ""), " "),
                                cb.coalesce(lastName, ""))),
                        cb.literal("áéíóúüñ"),
                        cb.literal("aeiouun")),
                    pattern,
                    '!'),
                cb.like(documentNumber, pattern, '!')));
      }

      if (query != null && query.getResultType() != Long.class) {
        final Expression<?> order =
            switch (sortField) {
              case "firstName" -> cb.lower(cb.coalesce(firstName, ""));
              case "lastName" -> cb.lower(cb.coalesce(lastName, ""));
              case "documentNumber" -> documentNumber;
              case "status" -> root.get("status");
              case "relationship" -> root.get("relationship");
              default -> root.get("createdAt");
            };
        query.orderBy(
            "desc".equals(sortDirection) ? cb.desc(order) : cb.asc(order), cb.asc(root.get("id")));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
