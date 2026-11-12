package ar.edu.utn.frvm.typeit.bemol_api.audit.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditFeed;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditModuleDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.AuditActionOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.GlobalAuditEventResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.InstitutionalActivityFilter;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditFilterOptionsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.InstitutionalActivityUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/institutions/{institutionId}/activity")
@RequiresInstitutionAccess
@Validated
@RequiredArgsConstructor
public class InstitutionalActivityController {
  private final InstitutionalActivityUseCase activity;
  private final AuditFilterOptionsUseCase options;

  @GetMapping(value = "/audit-events", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_AUDIT_READ)
  public PaginatedResponse<GlobalAuditEventResponse> auditEvents(
      @PathVariable final UUID institutionId,
      @Valid @ModelAttribute final InstitutionalActivityFilter filter,
      @PageableDefault(size = 20) final Pageable pageable) {
    return PaginatedResponse.from(
        activity.list(institutionId, AuditFeed.ACTIVITY, filter, pageable));
  }

  @GetMapping(value = "/audit-events/modules", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_AUDIT_READ)
  public List<AuditModuleDefinition> modules(@PathVariable final UUID institutionId) {
    return options.institutionalModules();
  }

  @GetMapping(value = "/audit-events/actions", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_AUDIT_READ)
  public PaginatedResponse<AuditActionOptionResponse> actions(
      @PathVariable final UUID institutionId,
      @RequestParam(required = false) @Nullable final String module,
      @RequestParam(required = false) @Nullable final String action,
      @RequestParam(required = false) @Size(max = 100) @Nullable final String search,
      @PageableDefault(size = 20) final Pageable pageable) {
    return PaginatedResponse.from(options.institutionalActions(module, action, search, pageable));
  }

  @GetMapping(value = "/audit-events/{eventId}", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_AUDIT_READ)
  public GlobalAuditEventResponse auditEvent(
      @PathVariable final UUID institutionId, @PathVariable final UUID eventId) {
    return activity.get(institutionId, AuditFeed.ACTIVITY, eventId);
  }

  @GetMapping(value = "/emails", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_EMAIL_READ)
  public PaginatedResponse<GlobalAuditEventResponse> emails(
      @PathVariable final UUID institutionId,
      @Valid @ModelAttribute final InstitutionalActivityFilter filter,
      @PageableDefault(size = 20) final Pageable pageable) {
    return PaginatedResponse.from(activity.list(institutionId, AuditFeed.EMAIL, filter, pageable));
  }

  @GetMapping(value = "/emails/{eventId}", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_EMAIL_READ)
  public GlobalAuditEventResponse email(
      @PathVariable final UUID institutionId, @PathVariable final UUID eventId) {
    return activity.get(institutionId, AuditFeed.EMAIL, eventId);
  }
}
