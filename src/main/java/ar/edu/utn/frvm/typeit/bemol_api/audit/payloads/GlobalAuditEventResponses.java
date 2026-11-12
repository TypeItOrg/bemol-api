package ar.edu.utn.frvm.typeit.bemol_api.audit.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperations;
import ar.edu.utn.frvm.typeit.bemol_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditActorKind;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GlobalAuditEventResponses {
  private final PersonRepository personRepository;
  private final PlatformAccountRepository platformAccountRepository;
  private final InstitutionRepository institutionRepository;
  private final AuditOperations operations;

  public GlobalAuditEventResponse responseFor(final AuditEvent event) {
    return toResponse(event, nameMaps(List.of(event)));
  }

  public Function<AuditEvent, GlobalAuditEventResponse> enrichment(final List<AuditEvent> events) {
    final var names = nameMaps(events);

    return event -> toResponse(event, names);
  }

  private NameMaps nameMaps(final List<AuditEvent> events) {
    final var references = collectReferences(events);
    return new NameMaps(
        personNames(references.people()),
        accountNames(references.accounts()),
        institutionNames(references.institutions()));
  }

  private static AuditReferences collectReferences(final List<AuditEvent> events) {
    final Set<UUID> people = new HashSet<>();
    final Set<UUID> accounts = new HashSet<>();
    final Set<UUID> institutions = new HashSet<>();
    for (final var event : events) {
      addIfPresent(people, event.getActorPersonId());
      addIfPresent(people, event.getSubjectPersonId());
      addIfPresent(accounts, event.getActorPlatformAccountId());
      if (event.getInstitution() != null) {
        institutions.add(event.getInstitution().getId());
      }
    }
    return new AuditReferences(people, accounts, institutions);
  }

  private static void addIfPresent(final Set<UUID> ids, final @Nullable UUID id) {
    if (id != null) {
      ids.add(id);
    }
  }

  private Map<UUID, String> personNames(final Set<UUID> ids) {
    return personRepository.findAllById(ids).stream()
        .collect(
            Collectors.toMap(
                person -> person.getId(),
                person -> person.getFirstName() + " " + person.getLastName()));
  }

  private Map<UUID, String> accountNames(final Set<UUID> ids) {
    return platformAccountRepository.findAllById(ids).stream()
        .collect(
            Collectors.toMap(
                account -> account.getId(),
                account -> account.getName() + " " + account.getLastName()));
  }

  private Map<UUID, String> institutionNames(final Set<UUID> ids) {
    return institutionRepository.findAllById(ids).stream()
        .collect(
            Collectors.toMap(
                institution -> institution.getId(), institution -> institution.getName()));
  }

  private GlobalAuditEventResponse toResponse(AuditEvent event, NameMaps names) {
    final var operation = operations.find(event.getAction());
    final var actor = actor(event, names);
    final var subject = subject(event, names);
    final var institution = institution(event, names);

    return new GlobalAuditEventResponse(
        event.getId(),
        event.getActorKind(),
        actor.id(),
        actor.name(),
        subject.id(),
        subject.name(),
        institution.id(),
        institution.name(),
        event.getAction(),
        event.getEntityType(),
        event.getEntityId(),
        event.isActedOnBehalf(),
        event.getRequestId(),
        event.getOrigin(),
        event.getDetails(),
        event.getOccurredAt(),
        operation == null ? event.getAction() : operation.getLabel(),
        operations.resourceLabel(event.getEntityType()),
        operation == null ? null : operation.getModule().code(),
        operation == null ? null : operation.getModule().label());
  }

  private static Actor actor(final AuditEvent event, final NameMaps names) {
    final UUID actorId = event.getActorId();
    if (actorId == null) {
      return new Actor(null, null);
    }

    final Map<UUID, String> source =
        event.getActorKind() == AuditActorKind.PLATFORM ? names.accounts() : names.people();
    return new Actor(actorId, source.get(actorId));
  }

  private static Subject subject(final AuditEvent event, final NameMaps names) {
    final UUID subjectId = event.getSubjectPersonId();
    if (subjectId == null) {
      return new Subject(null, null);
    }
    return new Subject(subjectId, names.people().get(subjectId));
  }

  private static Institution institution(final AuditEvent event, final NameMaps names) {
    if (event.getInstitution() == null) {
      return new Institution(null, null);
    }

    final UUID institutionId = event.getInstitution().getId();
    return new Institution(institutionId, names.institutions().get(institutionId));
  }

  private record AuditReferences(Set<UUID> people, Set<UUID> accounts, Set<UUID> institutions) {}

  private record NameMaps(
      Map<UUID, String> people, Map<UUID, String> accounts, Map<UUID, String> institutions) {}

  private record Actor(@Nullable UUID id, @Nullable String name) {}

  private record Subject(@Nullable UUID id, @Nullable String name) {}

  private record Institution(@Nullable UUID id, @Nullable String name) {}
}
