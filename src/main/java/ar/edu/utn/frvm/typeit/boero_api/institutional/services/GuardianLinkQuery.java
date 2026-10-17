package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import ar.edu.utn.frvm.typeit.boero_api.common.search.SearchNormalization;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardianAttachment;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

final class GuardianLinkQuery {
  private GuardianLinkQuery() {}

  static Specification<PersonGuardian> matching(
      final @Nullable UUID institutionId,
      final String search,
      final @Nullable GuardianLinkStatus status,
      final @Nullable Boolean hasDocumentation) {
    return (root, query, cb) -> {
      final List<Predicate> predicates = new ArrayList<>();

      if (institutionId != null) {
        predicates.add(cb.equal(root.get("institution").get("id"), institutionId));
      }
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      } else {
        predicates.add(
            root.get("status")
                .in(
                    GuardianLinkStatus.PENDING,
                    GuardianLinkStatus.ACTIVE,
                    GuardianLinkStatus.REJECTED));
      }

      if (hasDocumentation != null) {
        final var attachmentQuery = query.subquery(Integer.class);
        final var attachment = attachmentQuery.from(PersonGuardianAttachment.class);
        attachmentQuery
            .select(cb.literal(1))
            .where(
                cb.equal(attachment.get("personGuardian"), root),
                cb.equal(attachment.get("institution"), root.get("institution")));
        final var hasAttachment = cb.exists(attachmentQuery);
        predicates.add(hasDocumentation ? hasAttachment : cb.not(hasAttachment));
      }

      if (!search.isBlank()) {
        final String pattern = SearchNormalization.likeContainsPattern(search.trim());
        final List<Predicate> people = new ArrayList<>();

        for (final String association : List.of("tutorPerson", "dependentPerson")) {
          final var person = root.join(association);
          people.add(
              cb.like(
                  SearchNormalization.unaccentLower(
                      cb,
                      cb.concat(
                          cb.concat(cb.coalesce(person.get("firstName"), ""), " "),
                          cb.coalesce(person.get("lastName"), ""))),
                  pattern,
                  '\\'));
          people.add(cb.like(person.get("documentNumber"), pattern, '\\'));
        }
        predicates.add(cb.or(people.toArray(Predicate[]::new)));
      }

      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
