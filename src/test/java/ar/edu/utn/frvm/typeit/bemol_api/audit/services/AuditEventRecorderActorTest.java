package ar.edu.utn.frvm.typeit.bemol_api.audit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ar.edu.utn.frvm.typeit.bemol_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditActorKind;
import ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces.AuditEventRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.audit.InstitutionalAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditEventRecorderActorTest {
  private final AuditEventRepository events = mock(AuditEventRepository.class);
  private final AuditEventRecorder recorder =
      new AuditEventRecorder(
          events, Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneOffset.UTC));

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void explicitInstitutionalActorFallbackKeepsTheSuppliedDetails() {
    final var actor = UUID.randomUUID();
    final var subject = UUID.randomUUID();
    final var details =
        Map.<String, Object>of("fieldLabels", Map.of("status", "Estado de vinculación"));
    recorder.record(
        new Institution(),
        actor,
        subject,
        InstitutionalAuditAction.GUARDIAN_DEPENDENT_UNLINKED,
        PersonGuardian.AUDIT_TYPE,
        UUID.randomUUID(),
        details);

    final var event = recordedEvent();
    assertThat(event.getActorKind()).isEqualTo(AuditActorKind.INSTITUTION);
    assertThat(event.getActorPersonId()).isEqualTo(actor);
    assertThat(event.getSubjectPersonId()).isEqualTo(subject);
    assertThat(event.isActedOnBehalf()).isTrue();
    assertThat(event.getDetails()).isEqualTo(details);
  }

  @Test
  void delegatedActorIsNotReplacedByTheSelectedIdentityOrCallerFallback() {
    final var actor = UUID.randomUUID();
    final var selected = UUID.randomUUID();
    final var principal =
        JwtAuthenticatedUser.builder().personId(selected).authenticatedPersonId(actor).build();
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken(principal, ""));
    final var details = Map.<String, Object>of("entityName", "Vinculación");

    recorder.record(
        new Institution(),
        selected,
        selected,
        InstitutionalAuditAction.GUARDIAN_DEPENDENT_UNLINKED,
        PersonGuardian.AUDIT_TYPE,
        UUID.randomUUID(),
        details);

    final var event = recordedEvent();
    assertThat(event.getActorPersonId()).isEqualTo(actor);
    assertThat(event.isActedOnBehalf()).isTrue();
    assertThat(event.getDetails())
        .containsEntry("activePersonId", selected)
        .containsEntry("entityName", "Vinculación");
    assertThat(details).doesNotContainKey("activePersonId");
  }

  private AuditEvent recordedEvent() {
    final var event = ArgumentCaptor.forClass(AuditEvent.class);
    verify(events).save(event.capture());
    return event.getValue();
  }
}
