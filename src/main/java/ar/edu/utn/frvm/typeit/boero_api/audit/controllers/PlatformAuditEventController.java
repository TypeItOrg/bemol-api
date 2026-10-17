package ar.edu.utn.frvm.typeit.boero_api.audit.controllers;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditActorKind;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditModule;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditScope;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.AuditActionOptionResponse;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.AuditActorOptionResponse;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.AuditEntityOptionResponse;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.AuditPersonOptionResponse;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.AuditSubjectOptionResponse;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.GlobalAuditEventFilter;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.GlobalAuditEventResponse;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.GlobalAuditEventsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.common.mail.MailType;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
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

  @GetMapping(version = Version.V1)
  public PaginatedResponse<GlobalAuditEventResponse> list(
      @RequestParam(required = false) @Nullable final AuditScope scope,
      @RequestParam(required = false) @Nullable final UUID institutionId,
      @RequestParam(required = false) @Nullable final AuditActorKind actorKind,
      @RequestParam(required = false) @Nullable final UUID actorId,
      @RequestParam(required = false) @Nullable final UUID subjectPersonId,
      @RequestParam(required = false) @Nullable final AuditEntityType entityType,
      @RequestParam(required = false) @Nullable final UUID entityId,
      @RequestParam(required = false) @Nullable final AuditAction action,
      @RequestParam(required = false) @Size(max = 100) @Nullable final String requestId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Nullable
          final Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Nullable
          final Instant to,
      @RequestParam(required = false) @Nullable final AuditModule module,
      @RequestParam(required = false) @Size(max = 100) @Nullable final String search,
      @RequestParam(required = false) @Nullable final UUID personId,
      @RequestParam(required = false) @Nullable final MailType mailType,
      @PageableDefault(size = 20) final Pageable pageable) {
    return PaginatedResponse.from(
        globalAuditEventsUseCase.list(
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
                mailType),
            pageable));
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
    return PaginatedResponse.from(
        globalAuditEventsUseCase.actors(actorKind, institutionId, search, pageable));
  }

  @GetMapping(value = "/people", version = Version.V1)
  public PaginatedResponse<AuditPersonOptionResponse> people(
      @RequestParam(required = false) @Nullable final AuditScope scope,
      @RequestParam(required = false) @Nullable final UUID institutionId,
      @RequestParam(required = false) @Nullable final UUID personId,
      @RequestParam(required = false) @Size(max = 100) @Nullable final String search,
      @PageableDefault(size = 20) final Pageable pageable) {
    return PaginatedResponse.from(
        globalAuditEventsUseCase.people(scope, institutionId, personId, search, pageable));
  }

  @GetMapping(value = "/actions", version = Version.V1)
  public PaginatedResponse<AuditActionOptionResponse> actions(
      @RequestParam(required = false) @Nullable final AuditModule module,
      @RequestParam(required = false) @Nullable final AuditAction action,
      @RequestParam(required = false) @Size(max = 100) @Nullable final String search,
      @PageableDefault(size = 20) final Pageable pageable) {
    return PaginatedResponse.from(
        globalAuditEventsUseCase.actions(module, action, search, pageable));
  }

  @GetMapping(value = "/subjects", version = Version.V1)
  public PaginatedResponse<AuditSubjectOptionResponse> subjects(
      @RequestParam(required = false) @Nullable final AuditScope scope,
      @RequestParam(required = false) @Nullable final UUID institutionId,
      @RequestParam(required = false) @Nullable final UUID subjectPersonId,
      @RequestParam(required = false) @Size(max = 100) @Nullable final String search,
      @PageableDefault(size = 20) final Pageable pageable) {
    return PaginatedResponse.from(
        globalAuditEventsUseCase.subjects(scope, institutionId, subjectPersonId, search, pageable));
  }

  @GetMapping(value = "/entities", version = Version.V1)
  public PaginatedResponse<AuditEntityOptionResponse> entities(
      @RequestParam(required = false) @Nullable final AuditScope scope,
      @RequestParam(required = false) @Nullable final UUID institutionId,
      @RequestParam(required = false) @Nullable final AuditEntityType entityType,
      @RequestParam(required = false) @Nullable final UUID entityId,
      @RequestParam(required = false) @Size(max = 100) @Nullable final String search,
      @PageableDefault(size = 20) final Pageable pageable) {
    return PaginatedResponse.from(
        globalAuditEventsUseCase.entities(
            scope, institutionId, entityType, entityId, search, pageable));
  }
}
