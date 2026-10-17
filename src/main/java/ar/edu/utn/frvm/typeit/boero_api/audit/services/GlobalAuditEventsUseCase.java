package ar.edu.utn.frvm.typeit.boero_api.audit.services;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditActorKind;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditModule;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditScope;
import ar.edu.utn.frvm.typeit.boero_api.audit.exceptions.AuditEventNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.audit.exceptions.AuditMessages;
import ar.edu.utn.frvm.typeit.boero_api.audit.exceptions.AuditValidationException;
import ar.edu.utn.frvm.typeit.boero_api.audit.interfaces.AuditEventRepository;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.AuditActionOptionResponse;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.AuditActorOptionResponse;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.AuditEntityOptionResponse;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.AuditPersonOptionResponse;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.AuditSubjectOptionResponse;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.GlobalAuditEventFilter;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.GlobalAuditEventResponse;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GlobalAuditEventsUseCase {
  private final AuditEventRepository auditEventRepository;
  private final GlobalAuditEventResponses responses;

  @Transactional(readOnly = true)
  public Page<GlobalAuditEventResponse> list(
      final GlobalAuditEventFilter filter, final Pageable pageable) {
    final var page =
        auditEventRepository.findAll(
            GlobalAuditEventSpecification.from(filter),
            PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), 50),
                Sort.by(Sort.Direction.DESC, "occurredAt", "id")));
    return page.map(responses.enrichment(page.getContent()));
  }

  @Transactional(readOnly = true)
  public GlobalAuditEventResponse get(final UUID eventId) {
    final var event =
        auditEventRepository.findById(eventId).orElseThrow(AuditEventNotFoundException::new);
    return responses.enrichment(List.of(event)).apply(event);
  }

  @Transactional(readOnly = true)
  public Page<AuditActorOptionResponse> actors(
      @Nullable final AuditActorKind actorKind,
      @Nullable final UUID institutionId,
      @Nullable final String search,
      final Pageable pageable) {
    return auditEventRepository
        .searchActors(
            actorKind == null ? null : actorKind.name(),
            institutionId,
            search == null || search.isBlank() ? null : search.strip(),
            PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 50)))
        .map(
            actor ->
                new AuditActorOptionResponse(
                    AuditActorKind.valueOf(actor.getActorKind()),
                    actor.getActorId(),
                    actor.getLabel()));
  }

  @Transactional(readOnly = true)
  public Page<AuditPersonOptionResponse> people(
      @Nullable final AuditScope scope,
      @Nullable final UUID institutionId,
      @Nullable final UUID personId,
      @Nullable final String search,
      final Pageable pageable) {
    validateOptionScope(scope, institutionId);
    return auditEventRepository
        .searchPeople(
            scope == null ? null : scope.name(),
            institutionId,
            personId,
            search == null || search.isBlank() ? null : search.strip(),
            PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 50)))
        .map(
            person ->
                new AuditPersonOptionResponse(
                    person.getPersonId(), person.getLabel(), person.getInstitutionName()));
  }

  @Transactional(readOnly = true)
  public Page<AuditActionOptionResponse> actions(
      @Nullable final AuditModule module,
      @Nullable final AuditAction action,
      @Nullable final String search,
      final Pageable pageable) {
    final var words = GlobalAuditEventSpecification.searchWords(search);
    final var options =
        Arrays.stream(AuditAction.values())
            .filter(value -> module == null || value.getModule() == module)
            .filter(value -> action == null || value == action)
            .filter(
                value ->
                    words.stream()
                        .allMatch(
                            word ->
                                GlobalAuditEventSpecification.actionSearchText(value)
                                    .contains(word)))
            .sorted(
                Comparator.comparing((AuditAction value) -> value.getModule())
                    .thenComparing(
                        value -> GlobalAuditEventSpecification.normalize(value.getLabel())))
            .map(value -> new AuditActionOptionResponse(value, value.getLabel(), value.getModule()))
            .toList();
    final var bounded =
        PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 50));
    final int from = (int) Math.min(bounded.getOffset(), options.size());
    final int to = Math.min(from + bounded.getPageSize(), options.size());
    return new PageImpl<>(options.subList(from, to), bounded, options.size());
  }

  @Transactional(readOnly = true)
  public Page<AuditSubjectOptionResponse> subjects(
      @Nullable final AuditScope scope,
      @Nullable final UUID institutionId,
      @Nullable final UUID subjectPersonId,
      @Nullable final String search,
      final Pageable pageable) {
    validateOptionScope(scope, institutionId);
    return auditEventRepository
        .searchSubjects(
            scope == null ? null : scope.name(),
            institutionId,
            subjectPersonId,
            search == null || search.isBlank() ? null : search.strip(),
            PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 50)))
        .map(
            subject ->
                new AuditSubjectOptionResponse(
                    subject.getSubjectPersonId(),
                    subject.getLabel(),
                    subject.getInstitutionName()));
  }

  @Transactional(readOnly = true)
  public Page<AuditEntityOptionResponse> entities(
      @Nullable final AuditScope scope,
      @Nullable final UUID institutionId,
      @Nullable final AuditEntityType entityType,
      @Nullable final UUID entityId,
      @Nullable final String search,
      final Pageable pageable) {
    validateOptionScope(scope, institutionId);
    return auditEventRepository
        .searchEntities(
            scope == null ? null : scope.name(),
            institutionId,
            entityType == null ? null : entityType.name(),
            entityId,
            search == null || search.isBlank() ? null : search.strip(),
            PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 50)))
        .map(
            entity ->
                new AuditEntityOptionResponse(
                    AuditEntityType.valueOf(entity.getEntityType()),
                    entity.getEntityId(),
                    entity.getLabel(),
                    entity.getInstitutionName()));
  }

  private void validateOptionScope(
      @Nullable final AuditScope scope, @Nullable final UUID institutionId) {
    if (scope == AuditScope.PLATFORM && institutionId != null) {
      throw new AuditValidationException(AuditMessages.INVALID_SCOPE_FILTER);
    }
  }
}
