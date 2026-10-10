package ar.edu.utn.frvm.typeit.boero_api.audit.services;

import ar.edu.utn.frvm.typeit.boero_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.boero_api.audit.interfaces.AuditEventRepository;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.AuditEventFilter;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.AuditEventResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListAuditEventsUseCase {

  private final AuditEventRepository auditEventRepository;
  private final PersonRepository personRepository;

  @Transactional(readOnly = true)
  public Page<AuditEventResponse> execute(
      final UUID institutionId, final AuditEventFilter filter, final Pageable pageable) {
    final Page<AuditEvent> page =
        auditEventRepository.search(
            institutionId,
            filter.subjectPersonId(),
            filter.actorPersonId(),
            filter.entityId(),
            filter.action(),
            filter.actedOnBehalf(),
            filter.from(),
            filter.to(),
            pageable);

    final Set<UUID> personIds = new HashSet<>();
    for (final AuditEvent event : page.getContent()) {
      personIds.add(event.getActorPersonId());
      personIds.add(event.getSubjectPersonId());
    }

    // A person may no longer exist; the event is kept and its name left empty.
    final Map<UUID, String> names =
        personRepository.findAllById(personIds).stream()
            .collect(
                Collectors.toMap(
                    person -> person.getId(), this::fullName, (first, second) -> first));

    return page.map(
        event ->
            AuditEventResponse.from(
                event, names.get(event.getActorPersonId()), names.get(event.getSubjectPersonId())));
  }

  private String fullName(final Person person) {
    return person.getFirstName() + " " + person.getLastName();
  }
}
