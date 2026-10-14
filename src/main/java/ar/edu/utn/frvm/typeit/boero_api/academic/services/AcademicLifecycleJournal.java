package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicLifecycleEvent;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicLifecycleAction;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicLifecycleResource;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.AcademicLifecycleEventRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.AcademicLifecycleRequest;
import ar.edu.utn.frvm.typeit.boero_api.common.logging.RequestLoggingFilter;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.SoftDeletable;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import java.time.Clock;
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
    if (resource.restore()) {
      record(institution, type, id, AcademicLifecycleAction.RESTORE, request);
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      final Institution institution,
      final AcademicLifecycleResource type,
      final UUID id,
      final AcademicLifecycleAction action,
      final @Nullable AcademicLifecycleRequest request) {
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

  private static @Nullable String normalizeReason(
      final @Nullable AcademicLifecycleRequest request) {
    if (request == null || request.reason() == null || request.reason().isBlank()) {
      return null;
    }
    return request.reason().trim();
  }
}
