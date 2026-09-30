package ar.edu.utn.frvm.typeit.boero_api.audit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import ar.edu.utn.frvm.typeit.boero_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.interfaces.AuditEventRepository;
import ar.edu.utn.frvm.typeit.boero_api.common.logging.RequestLoggingFilter;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class AuditEventRecorderTest {

  private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");

  @Mock private AuditEventRepository auditEventRepository;

  private final Institution institution = Institution.builder().id(UUID.randomUUID()).build();
  private final UUID tutorId = UUID.randomUUID();
  private final UUID dependentId = UUID.randomUUID();
  private final UUID entityId = UUID.randomUUID();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  @DisplayName("Should flag the event as acted on behalf when actor and subject differ")
  void record_marksActedOnBehalf() {
    MDC.put(RequestLoggingFilter.MDC_REQUEST_ID_KEY, "req-1");

    recorder()
        .record(
            institution,
            tutorId,
            dependentId,
            AuditAction.ENROLLMENT_APPLICATION_SUBMITTED,
            AuditEntityType.ENROLLMENT_APPLICATION,
            entityId);

    final AuditEvent event = savedEvent();
    assertThat(event.getActorPersonId()).isEqualTo(tutorId);
    assertThat(event.getSubjectPersonId()).isEqualTo(dependentId);
    assertThat(event.isActedOnBehalf()).isTrue();
    assertThat(event.getAction()).isEqualTo(AuditAction.ENROLLMENT_APPLICATION_SUBMITTED);
    assertThat(event.getEntityType()).isEqualTo(AuditEntityType.ENROLLMENT_APPLICATION);
    assertThat(event.getEntityId()).isEqualTo(entityId);
    assertThat(event.getInstitution()).isSameAs(institution);
    assertThat(event.getRequestId()).isEqualTo("req-1");
    assertThat(event.getOccurredAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("Should not flag the event when a person acts on themself")
  void record_notOnBehalfWhenSamePerson() {
    recorder()
        .record(
            institution,
            dependentId,
            dependentId,
            AuditAction.ENROLLMENT_APPLICATION_CANCELLED,
            AuditEntityType.ENROLLMENT_APPLICATION,
            entityId);

    final AuditEvent event = savedEvent();
    assertThat(event.isActedOnBehalf()).isFalse();
    assertThat(event.getRequestId()).isNull();
  }

  private AuditEventRecorder recorder() {
    return new AuditEventRecorder(auditEventRepository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private AuditEvent savedEvent() {
    final var captor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(auditEventRepository).save(captor.capture());

    return captor.getValue();
  }
}
