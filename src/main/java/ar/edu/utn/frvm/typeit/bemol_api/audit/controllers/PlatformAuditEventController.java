package ar.edu.utn.frvm.typeit.bemol_api.audit.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditModuleDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditActorKind;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditScope;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.AuditActionOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.AuditActorOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.AuditEntityOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.AuditPersonOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.AuditSubjectOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.GlobalAuditEventFilter;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.GlobalAuditEventResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditFilterOptionsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.GlobalAuditEventsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailType;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/audit-events")
@RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
@Validated
@RequiredArgsConstructor
public class PlatformAuditEventController {
  private final GlobalAuditEventsUseCase globalAuditEventsUseCase;
  private final AuditFilterOptionsUseCase options;

  @GetMapping(version = Version.V1)
  public PaginatedResponse<GlobalAuditEventResponse> list(
      @RequestParam(required = false) @Nullable final AuditScope scope,
      @RequestParam(required = false) @Nullable final UUID institutionId,
      @RequestParam(required = false) @Nullable final AuditActorKind actorKind,
      @RequestParam(required = false) @Nullable final UUID actorId,
      @RequestParam(required = false) @Nullable final UUID subjectPersonId,
      @RequestParam(required = false) @Nullable final String entityType,
      @RequestParam(required = false) @Nullable final UUID entityId,
      @RequestParam(required = false) @Nullable final String action,
      @RequestParam(required = false) @Size(max = 100) @Nullable final String requestId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Nullable
          final Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Nullable
          final Instant to,
      @RequestParam(required = false) @Nullable final String module,
      @RequestParam(required = false) @Size(max = 100) @Nullable final String search,
      @RequestParam(required = false) @Nullable final UUID personId,
      @RequestParam(required = false) @Nullable final MailType mailType,
      @PageableDefault(size = 20) final Pageable pageable) {
    final GlobalAuditEventFilter filter =
        new GlobalAuditEventFilter(
            scope,
            institutionId,
            actorKind,
            actorId,
            subjectPersonId,
            entityType,
            entityId,
            action,
            requestId,
            from,
            to,
            module,
            search,
            personId,
            mailType);

    final Page<GlobalAuditEventResponse> page = globalAuditEventsUseCase.list(filter, pageable);

    return PaginatedResponse.from(page);
  }

  @GetMapping(value = "/{eventId}", version = Version.V1)
  public GlobalAuditEventResponse get(@PathVariable final UUID eventId) {
    return globalAuditEventsUseCase.get(eventId);
  }

  @GetMapping(value = "/actors", version = Version.V1)
  public PaginatedResponse<AuditActorOptionResponse> actors(
      @RequestParam(required = false) @Nullable final AuditActorKind actorKind,
      @RequestParam(required = false) @Nullable final UUID institutionId,
      @RequestParam(required = false) @Size(max = 100) @Nullable final String search,
      @PageableDefault(size = 20) final Pageable pageable) {
    return PaginatedResponse.from(options.actors(actorKind, institutionId, search, pageable));
  }

  @GetMapping(value = "/people", version = Version.V1)
  public PaginatedResponse<AuditPersonOptionResponse> people(
      @RequestParam(required = false) @Nullable final AuditScope scope,
      @RequestParam(required = false) @Nullable final UUID institutionId,
      @RequestParam(required = false) @Nullable final UUID personId,
      @RequestParam(required = false) @Size(max = 100) @Nullable final String search,
      @PageableDefault(size = 20) final Pageable pageable) {
    return PaginatedResponse.from(options.people(scope, institutionId, personId, search, pageable));
  }

  @GetMapping(value = "/actions", version = Version.V1)
  public PaginatedResponse<AuditActionOptionResponse> actions(
      @RequestParam(required = false) @Nullable final String module,
      @RequestParam(required = false) @Nullable final String action,
      @RequestParam(required = false) @Size(max = 100) @Nullable final String search,
      @PageableDefault(size = 20) final Pageable pageable) {
    return PaginatedResponse.from(options.platformActions(module, action, search, pageable));
  }

  @GetMapping(value = "/modules", version = Version.V1)
  public List<AuditModuleDefinition> modules() {
    return options.platformModules();
  }

  @GetMapping(value = "/subjects", version = Version.V1)
  public PaginatedResponse<AuditSubjectOptionResponse> subjects(
      @RequestParam(required = false) @Nullable final AuditScope scope,
      @RequestParam(required = false) @Nullable final UUID institutionId,
      @RequestParam(required = false) @Nullable final UUID subjectPersonId,
      @RequestParam(required = false) @Size(max = 100) @Nullable final String search,
      @PageableDefault(size = 20) final Pageable pageable) {
    return PaginatedResponse.from(
        options.subjects(scope, institutionId, subjectPersonId, search, pageable));
  }

  @GetMapping(value = "/entities", version = Version.V1)
  public PaginatedResponse<AuditEntityOptionResponse> entities(
      @RequestParam(required = false) @Nullable final AuditScope scope,
      @RequestParam(required = false) @Nullable final UUID institutionId,
      @RequestParam(required = false) @Nullable final String entityType,
      @RequestParam(required = false) @Nullable final UUID entityId,
      @RequestParam(required = false) @Size(max = 100) @Nullable final String search,
      @PageableDefault(size = 20) final Pageable pageable) {
    return PaginatedResponse.from(
        options.entities(scope, institutionId, entityType, entityId, search, pageable));
  }
}
