package ar.edu.utn.frvm.typeit.boero_api.audit.services;

import ar.edu.utn.frvm.typeit.boero_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditActorKind;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.GlobalAuditEventResponse;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class GlobalAuditEventResponses {
  private final PersonRepository personRepository;
  private final PlatformAccountRepository platformAccountRepository;
  private final InstitutionRepository institutionRepository;

  Function<AuditEvent, GlobalAuditEventResponse> enrichment(final List<AuditEvent> events) {
    final Set<UUID> people = new HashSet<>();
    final Set<UUID> accounts = new HashSet<>();
    final Set<UUID> institutions = new HashSet<>();
    for (final var event : events) {
      if (event.getActorPersonId() != null) {
        people.add(event.getActorPersonId());
      }
      if (event.getSubjectPersonId() != null) {
        people.add(event.getSubjectPersonId());
      }
      if (event.getActorPlatformAccountId() != null) {
        accounts.add(event.getActorPlatformAccountId());
      }
      if (event.getInstitution() != null) {
        institutions.add(event.getInstitution().getId());
      }
    }
    final Map<UUID, String> personNames =
        personRepository.findAllById(people).stream()
            .collect(
                Collectors.toMap(
                    person -> person.getId(),
                    person -> person.getFirstName() + " " + person.getLastName()));
    final Map<UUID, String> accountNames =
        platformAccountRepository.findAllById(accounts).stream()
            .collect(
                Collectors.toMap(
                    account -> account.getId(),
                    account -> account.getName() + " " + account.getLastName()));
    final Map<UUID, String> institutionNames =
        institutionRepository.findAllById(institutions).stream()
            .collect(
                Collectors.toMap(
                    institution -> institution.getId(), institution -> institution.getName()));
    return event -> {
      final UUID institutionId =
          event.getInstitution() == null ? null : event.getInstitution().getId();
      final UUID actorId = event.getActorId();
      final String actorName =
          actorId == null
              ? null
              : (event.getActorKind() == AuditActorKind.PLATFORM ? accountNames : personNames)
                  .get(actorId);
      return new GlobalAuditEventResponse(
          event.getId(),
          event.getActorKind(),
          actorId,
          actorName,
          event.getSubjectPersonId(),
          event.getSubjectPersonId() == null ? null : personNames.get(event.getSubjectPersonId()),
          institutionId,
          institutionId == null ? null : institutionNames.get(institutionId),
          event.getAction(),
          event.getEntityType(),
          event.getEntityId(),
          event.isActedOnBehalf(),
          event.getRequestId(),
          event.getOrigin(),
          event.getDetails(),
          event.getOccurredAt(),
          null);
    };
  }
}
