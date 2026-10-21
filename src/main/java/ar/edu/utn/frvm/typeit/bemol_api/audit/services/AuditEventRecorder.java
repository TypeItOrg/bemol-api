package ar.edu.utn.frvm.typeit.bemol_api.audit.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditActorKind;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.envers.EnversAuditTransaction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces.AuditEventRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.common.logging.RequestLoggingFilter;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends to the audit log inside the caller's transaction, so an event exists if and only if the
 * audited change was committed.
 */
@Component
@RequiredArgsConstructor
public class AuditEventRecorder {

  private final AuditEventRepository auditEventRepository;
  private final Clock clock;

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      final Institution institution,
      final UUID actorPersonId,
      final UUID subjectPersonId,
      final AuditAction action,
      final AuditEntityType entityType,
      final UUID entityId) {
    final var actor = currentInstitutionalActor();
    final UUID realActor = actor == null ? actorPersonId : actor.actorPersonId();
    auditEventRepository.save(
        AuditEvent.record(
            institution,
            AuditActorKind.INSTITUTION,
            realActor,
            null,
            subjectPersonId,
            action,
            entityType,
            entityId,
            (actor != null && actor.delegated()) || !realActor.equals(subjectPersonId),
            MDC.get(RequestLoggingFilter.MDC_REQUEST_ID_KEY),
            null,
            identityDetails(Map.of()),
            clock.instant(),
            EnversAuditTransaction.currentId()));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordChange(
      @Nullable final Institution institution,
      @Nullable final UUID subjectPersonId,
      final AuditAction action,
      final AuditEntityType entityType,
      final UUID entityId,
      final String name,
      final Map<String, Object> before,
      final Map<String, Object> after) {
    if (!before.equals(after)) {
      recordCurrentActor(
          institution,
          subjectPersonId,
          action,
          entityType,
          entityId,
          AuditOrigin.administration(),
          AuditChangeDetails.change(name, before, after));
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordCurrentActor(
      @Nullable final Institution institution,
      @Nullable final UUID subjectPersonId,
      final AuditAction action,
      final AuditEntityType entityType,
      final UUID entityId,
      final AuditOrigin origin,
      final Map<String, Object> details) {
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    final Object principal = authentication == null ? null : authentication.getPrincipal();
    final AuditActorKind actorKind;
    final UUID actorPersonId;
    final UUID platformAccountId;
    if (principal instanceof JwtAuthenticatedUser user) {
      actorKind = AuditActorKind.INSTITUTION;
      actorPersonId = user.actorPersonId();
      platformAccountId = null;
    } else if (principal instanceof JwtAuthenticatedPlatformAccount account) {
      actorKind = AuditActorKind.PLATFORM;
      actorPersonId = null;
      platformAccountId = account.platformAccountId();
    } else {
      actorKind = AuditActorKind.ANONYMOUS;
      actorPersonId = null;
      platformAccountId = null;
    }
    auditEventRepository.save(
        AuditEvent.record(
            institution,
            actorKind,
            actorPersonId,
            platformAccountId,
            subjectPersonId,
            action,
            entityType,
            entityId,
            ((principal instanceof JwtAuthenticatedUser user && user.delegated())
                    || origin == AuditOrigin.SELF_SERVICE)
                && actorPersonId != null
                && subjectPersonId != null
                && !actorPersonId.equals(subjectPersonId),
            MDC.get(RequestLoggingFilter.MDC_REQUEST_ID_KEY),
            origin,
            identityDetails(details),
            clock.instant(),
            EnversAuditTransaction.currentId()));
  }

  private static @Nullable JwtAuthenticatedUser currentInstitutionalActor() {
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
            && authentication.getPrincipal() instanceof JwtAuthenticatedUser user
        ? user
        : null;
  }

  private static Map<String, Object> identityDetails(final Map<String, Object> details) {
    final var actor = currentInstitutionalActor();
    if (actor == null || !actor.delegated()) {
      return details;
    }
    final var result = new HashMap<String, Object>(details);
    result.put("activePersonId", actor.personId());
    return result;
  }
}
