package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicLifecycleEvent;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicLifecycleAction;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicLifecycleResource;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.AcademicLifecycleEventRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.AcademicLifecycleRequest;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.common.logging.RequestLoggingFilter;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.SoftDeletable;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AcademicLifecycleJournal {
  private final AuditEventRecorder auditRecorder;
  private final Clock clock;
  private final AcademicLifecycleEventRepository events;
  private final AcademicLifecycleActorResolver actors;

  @Transactional(propagation = Propagation.MANDATORY)
  public void restore(
      final SoftDeletable resource,
      final Institution institution,
      final AcademicLifecycleResource type,
      final UUID id,
      final @Nullable AcademicLifecycleRequest request) {
    restore(resource, institution, type, id, request, Map.of());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void restore(
      final SoftDeletable resource,
      final Institution institution,
      final AcademicLifecycleResource type,
      final UUID id,
      final @Nullable AcademicLifecycleRequest request,
      final Map<String, Object> details) {
    if (resource.restore()) {
      record(institution, type, id, AcademicLifecycleAction.RESTORE, request, details);
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      final Institution institution,
      final AcademicLifecycleResource type,
      final UUID id,
      final AcademicLifecycleAction action,
      final @Nullable AcademicLifecycleRequest request) {
    record(institution, type, id, action, request, Map.of());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      final Institution institution,
      final AcademicLifecycleResource type,
      final UUID id,
      final AcademicLifecycleAction action,
      final @Nullable AcademicLifecycleRequest request,
      final Map<String, Object> details) {
    final var actor = actors.resolve();
    final var now = clock.instant();
    try {
      events.save(
          AcademicLifecycleEvent.create(
              institution,
              type,
              id,
              action,
              actor.type(),
              actor.id(),
              normalizeReason(request),
              MDC.get(RequestLoggingFilter.MDC_REQUEST_ID_KEY),
              now));
      events.flush();
      recordGlobal(institution, type, id, action, details);
      log.info(
          "[AcademicLifecycle] Transition recorded, resourceType: {}, resourceId: {}, action: {}, actorType: {}, actorId: {}",
          type,
          id,
          action,
          actor.type(),
          actor.id());
    } catch (DataIntegrityViolationException exception) {
      if (action == AcademicLifecycleAction.RESTORE) {
        throw new AcademicConflictException(AcademicMessages.RESTORE_CONFLICT);
      }
      throw AcademicIntegrityViolationTranslator.translate(exception);
    }
  }

  private void recordGlobal(
      final Institution institution,
      final AcademicLifecycleResource type,
      final UUID id,
      final AcademicLifecycleAction action,
      final Map<String, Object> details) {
    if (details.isEmpty()) {
      return;
    }
    final AuditAction auditAction =
        switch (type) {
          case COURSE ->
              switch (action) {
                case DELETE -> AuditAction.COURSE_DELETED;
                case RESTORE -> AuditAction.COURSE_RESTORED;
                default ->
                    throw new IllegalArgumentException("Unsupported course lifecycle action");
              };
          case STUDY_PLAN ->
              switch (action) {
                case DELETE -> AuditAction.STUDY_PLAN_DELETED;
                case RESTORE -> AuditAction.STUDY_PLAN_RESTORED;
                case CREATE_VERSION -> AuditAction.STUDY_PLAN_VERSION_CREATED;
              };
          case ACADEMIC_YEAR ->
              action == AcademicLifecycleAction.DELETE
                  ? AuditAction.ACADEMIC_YEAR_DELETED
                  : AuditAction.ACADEMIC_YEAR_RESTORED;
          case ACADEMIC_SPACE ->
              action == AcademicLifecycleAction.DELETE
                  ? AuditAction.ACADEMIC_SPACE_DELETED
                  : AuditAction.ACADEMIC_SPACE_RESTORED;
          case INSTRUMENT ->
              action == AcademicLifecycleAction.DELETE
                  ? AuditAction.INSTRUMENT_DELETED
                  : AuditAction.INSTRUMENT_RESTORED;
          case SHIFT ->
              action == AcademicLifecycleAction.DELETE
                  ? AuditAction.SHIFT_DELETED
                  : AuditAction.SHIFT_RESTORED;
          case TRAINING_PATH ->
              action == AcademicLifecycleAction.DELETE
                  ? AuditAction.TRAINING_PATH_DELETED
                  : AuditAction.TRAINING_PATH_RESTORED;
        };
    auditRecorder.recordCurrentActor(
        institution,
        null,
        auditAction,
        AuditEntityType.valueOf(type.name()),
        id,
        AuditOrigin.administration(),
        details);
  }

  private static @Nullable String normalizeReason(
      final @Nullable AcademicLifecycleRequest request) {
    if (request == null || request.reason() == null || request.reason().isBlank()) {
      return null;
    }
    return request.reason().trim();
  }
}
