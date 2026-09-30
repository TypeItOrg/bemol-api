package ar.edu.utn.frvm.typeit.boero_api.audit.controllers;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.AuditEventFilter;
import ar.edu.utn.frvm.typeit.boero_api.audit.payloads.AuditEventResponse;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.ListAuditEventsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/institutions/{institutionId}/audit-events")
@RequiresInstitutionAccess
@Validated
@RequiredArgsConstructor
public class AuditEventController {

  private final ListAuditEventsUseCase listAuditEventsUseCase;

  @GetMapping(version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_AUDIT_READ)
  public PaginatedResponse<AuditEventResponse> list(
      @PathVariable final UUID institutionId,
      @RequestParam(required = false) @Nullable final UUID subjectPersonId,
      @RequestParam(required = false) @Nullable final UUID actorPersonId,
      @RequestParam(required = false) @Nullable final UUID entityId,
      @RequestParam(required = false) @Nullable final AuditAction action,
      @RequestParam(required = false) @Nullable final Boolean actedOnBehalf,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Nullable
          final Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Nullable
          final Instant to,
      @PageableDefault(sort = "occurredAt", direction = Sort.Direction.DESC)
          final Pageable pageable) {
    return PaginatedResponse.from(
        listAuditEventsUseCase.execute(
            institutionId,
            new AuditEventFilter(
                subjectPersonId, actorPersonId, entityId, action, actedOnBehalf, from, to),
            pageable));
  }
}
