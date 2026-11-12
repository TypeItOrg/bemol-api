package ar.edu.utn.frvm.typeit.bemol_api.audit.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditFeed;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditModuleDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperation;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperations;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditView;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditActorKind;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditScope;
import ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces.AuditActorOptionProjection;
import ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces.AuditEntityOptionProjection;
import ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces.AuditEventRepository;
import ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces.AuditPersonOptionProjection;
import ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces.AuditSubjectOptionProjection;
import ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces.GlobalAuditEventSearch;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.AuditActionOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.AuditActorOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.AuditEntityOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.AuditPersonOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.AuditSubjectOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.GlobalAuditEventFilter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class AuditFilterOptionsUseCase {
  private static final AuditView INSTITUTION_ACTIVITY = AuditView.institution(AuditFeed.ACTIVITY);
  private static final Comparator<AuditActionOptionResponse> BY_MODULE_AND_LABEL =
      Comparator.comparing((AuditActionOptionResponse option) -> option.module())
          .thenComparing(option -> GlobalAuditEventSearch.normalize(option.label()));

  private final AuditEventRepository auditEventRepository;
  private final AuditOperations operations;
  private final JsonMapper json;

  public List<AuditModuleDefinition> platformModules() {
    return operations.modules(AuditView.platform(AuditFeed.ACTIVITY));
  }

  public List<AuditModuleDefinition> institutionalModules() {
    return operations.modules(INSTITUTION_ACTIVITY);
  }

  public Page<AuditActionOptionResponse> platformActions(
      @Nullable final String module,
      @Nullable final String action,
      @Nullable final String search,
      final Pageable pageable) {
    final var view = AuditView.platform(operations.feedOf(module));

    return actions(view, module, action, search, pageable);
  }

  public Page<AuditActionOptionResponse> institutionalActions(
      @Nullable final String module,
      @Nullable final String action,
      @Nullable final String search,
      final Pageable pageable) {
    return actions(INSTITUTION_ACTIVITY, module, action, search, pageable);
  }

  @Transactional(readOnly = true)
  public Page<AuditActorOptionResponse> actors(
      @Nullable final AuditActorKind actorKind,
      @Nullable final UUID institutionId,
      @Nullable final String search,
      final Pageable pageable) {
    final Page<AuditActorOptionProjection> page =
        auditEventRepository.searchActors(
            enumName(actorKind), institutionId, searchTerm(search), AuditPages.bounded(pageable));

    return page.map(AuditFilterOptionsUseCase::actorOption);
  }

  @Transactional(readOnly = true)
  public Page<AuditPersonOptionResponse> people(
      @Nullable final AuditScope scope,
      @Nullable final UUID institutionId,
      @Nullable final UUID personId,
      @Nullable final String search,
      final Pageable pageable) {
    GlobalAuditEventFilter.validateScope(scope, institutionId);

    final Page<AuditPersonOptionProjection> page =
        auditEventRepository.searchPeople(
            enumName(scope),
            institutionId,
            personId,
            searchTerm(search),
            AuditPages.bounded(pageable));

    return page.map(AuditFilterOptionsUseCase::personOption);
  }

  @Transactional(readOnly = true)
  public Page<AuditSubjectOptionResponse> subjects(
      @Nullable final AuditScope scope,
      @Nullable final UUID institutionId,
      @Nullable final UUID subjectPersonId,
      @Nullable final String search,
      final Pageable pageable) {
    GlobalAuditEventFilter.validateScope(scope, institutionId);

    final Page<AuditSubjectOptionProjection> page =
        auditEventRepository.searchSubjects(
            enumName(scope),
            institutionId,
            subjectPersonId,
            searchTerm(search),
            AuditPages.bounded(pageable));

    return page.map(AuditFilterOptionsUseCase::subjectOption);
  }

  @Transactional(readOnly = true)
  public Page<AuditEntityOptionResponse> entities(
      @Nullable final AuditScope scope,
      @Nullable final UUID institutionId,
      @Nullable final String entityType,
      @Nullable final UUID entityId,
      @Nullable final String search,
      final Pageable pageable) {
    GlobalAuditEventFilter.validateScope(scope, institutionId);
    operations.validateResource(entityType);

    final var resourceLabels = json.writeValueAsString(operations.resourceLabels());

    final Page<AuditEntityOptionProjection> page =
        auditEventRepository.searchEntities(
            enumName(scope),
            institutionId,
            entityType,
            entityId,
            searchTerm(search),
            resourceLabels,
            AuditPages.bounded(pageable));

    return page.map(AuditFilterOptionsUseCase::entityOption);
  }

  private Page<AuditActionOptionResponse> actions(
      final AuditView view,
      @Nullable final String module,
      @Nullable final String action,
      @Nullable final String search,
      final Pageable pageable) {
    operations.validate(view, action, module);

    final var words = GlobalAuditEventSearch.searchWords(search);
    final var options =
        operations.visible(view).stream()
            .filter(operation -> module == null || operation.getModule().code().equals(module))
            .filter(operation -> action == null || operation.name().equals(action))
            .filter(operation -> matchesWords(operation, words))
            .map(AuditFilterOptionsUseCase::actionOption)
            .sorted(BY_MODULE_AND_LABEL)
            .toList();

    return page(options, AuditPages.bounded(pageable));
  }

  private static boolean matchesWords(final AuditOperation operation, final List<String> words) {
    final String searchText = GlobalAuditEventSearch.actionSearchText(operation);

    return words.stream().allMatch(searchText::contains);
  }

  private static Page<AuditActionOptionResponse> page(
      final List<AuditActionOptionResponse> options, final PageRequest request) {
    final int from = (int) Math.min(request.getOffset(), options.size());
    final int to = Math.min(from + request.getPageSize(), options.size());
    return new PageImpl<>(options.subList(from, to), request, options.size());
  }

  private static @Nullable String enumName(@Nullable final Enum<?> value) {
    return value == null ? null : value.name();
  }

  private static @Nullable String searchTerm(@Nullable final String search) {
    return search == null || search.isBlank() ? null : search.strip();
  }

  private static AuditActionOptionResponse actionOption(final AuditOperation operation) {
    return new AuditActionOptionResponse(
        operation.name(),
        operation.getLabel(),
        operation.getModule().code(),
        operation.getModule().label());
  }

  private static AuditActorOptionResponse actorOption(final AuditActorOptionProjection actor) {
    return new AuditActorOptionResponse(
        AuditActorKind.valueOf(actor.getActorKind()), actor.getActorId(), actor.getLabel());
  }

  private static AuditPersonOptionResponse personOption(final AuditPersonOptionProjection person) {
    return new AuditPersonOptionResponse(
        person.getPersonId(), person.getLabel(), person.getInstitutionName());
  }

  private static AuditSubjectOptionResponse subjectOption(
      final AuditSubjectOptionProjection subject) {
    return new AuditSubjectOptionResponse(
        subject.getSubjectPersonId(), subject.getLabel(), subject.getInstitutionName());
  }

  private static AuditEntityOptionResponse entityOption(final AuditEntityOptionProjection entity) {
    return new AuditEntityOptionResponse(
        entity.getEntityType(),
        entity.getEntityId(),
        entity.getLabel(),
        entity.getInstitutionName());
  }
}
