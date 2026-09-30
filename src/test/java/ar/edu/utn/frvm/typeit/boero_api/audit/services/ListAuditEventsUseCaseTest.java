package ar.edu.utn.frvm.typeit.boero_api.audit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.interfaces.AuditEventRepository;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.AuditEventFilter;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.AuditEventResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ListAuditEventsUseCaseTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-30T12:00:00Z");

  @Mock private AuditEventRepository auditEventRepository;
  @Mock private PersonRepository personRepository;

  private final Institution institution = Institution.builder().id(UUID.randomUUID()).build();
  private final Person tutor = person("Tutor", "Testing");
  private final Person dependent = person("Martin", "Crossetin");
  private final Pageable pageable = PageRequest.of(0, 20);

  @Test
  @DisplayName("Should return events with the names of actor and subject")
  void execute_resolvesNames() {
    final UUID entityId = UUID.randomUUID();
    final AuditEvent event =
        AuditEvent.record(
            institution,
            tutor.getId(),
            dependent.getId(),
            AuditAction.ENROLLMENT_APPLICATION_SUBMITTED,
            AuditEntityType.ENROLLMENT_APPLICATION,
            entityId,
            "req-1",
            OCCURRED_AT);
    final var filter = new AuditEventFilter(dependent.getId(), null, null, null, null, null, null);
    when(auditEventRepository.search(
            institution.getId(), dependent.getId(), null, null, null, null, null, null, pageable))
        .thenReturn(new PageImpl<>(List.of(event), pageable, 1));
    when(personRepository.findAllById(Set.of(tutor.getId(), dependent.getId())))
        .thenReturn(List.of(tutor, dependent));

    final Page<AuditEventResponse> result =
        new ListAuditEventsUseCase(auditEventRepository, personRepository)
            .execute(institution.getId(), filter, pageable);

    assertThat(result.getContent()).hasSize(1);
    final AuditEventResponse response = result.getContent().getFirst();
    assertThat(response.actorName()).isEqualTo("Tutor Testing");
    assertThat(response.subjectName()).isEqualTo("Martin Crossetin");
    assertThat(response.actedOnBehalf()).isTrue();
    assertThat(response.action()).isEqualTo(AuditAction.ENROLLMENT_APPLICATION_SUBMITTED);
    assertThat(response.entityId()).isEqualTo(entityId);
    assertThat(response.requestId()).isEqualTo("req-1");
    assertThat(response.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  @DisplayName("Should keep the event when a person can no longer be resolved")
  void execute_toleratesMissingPerson() {
    final AuditEvent event =
        AuditEvent.record(
            institution,
            tutor.getId(),
            dependent.getId(),
            AuditAction.GUARDIAN_DEPENDENT_UNLINKED,
            AuditEntityType.PERSON_GUARDIAN,
            UUID.randomUUID(),
            null,
            OCCURRED_AT);
    final var filter = new AuditEventFilter(null, null, null, null, null, null, null);
    when(auditEventRepository.search(
            institution.getId(), null, null, null, null, null, null, null, pageable))
        .thenReturn(new PageImpl<>(List.of(event), pageable, 1));
    when(personRepository.findAllById(Set.of(tutor.getId(), dependent.getId())))
        .thenReturn(List.of(tutor));

    final AuditEventResponse response =
        new ListAuditEventsUseCase(auditEventRepository, personRepository)
            .execute(institution.getId(), filter, pageable)
            .getContent()
            .getFirst();

    assertThat(response.actorName()).isEqualTo("Tutor Testing");
    assertThat(response.subjectName()).isNull();
  }

  private Person person(final String firstName, final String lastName) {
    return Person.builder()
        .id(UUID.randomUUID())
        .institution(institution)
        .firstName(firstName)
        .lastName(lastName)
        .build();
  }
}
