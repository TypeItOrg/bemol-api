package ar.edu.utn.frvm.typeit.bemol_api.audit.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.ActivityLabelsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.GlobalAuditEventResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.InstitutionalActivityFilter;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.InstitutionalActivityUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/institutions/{institutionId}/activity")
@RequiresInstitutionAccess
@Validated
@RequiredArgsConstructor
public class InstitutionalActivityController {
  private final InstitutionalActivityUseCase activity;

  @GetMapping(value = "/audit-events", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_AUDIT_READ)
  public PaginatedResponse<GlobalAuditEventResponse> auditEvents(
      @PathVariable final UUID institutionId,
      @Valid @ModelAttribute final InstitutionalActivityFilter filter,
      @PageableDefault(size = 20) final Pageable pageable) {
    return PaginatedResponse.from(activity.list(institutionId, filter, false, pageable));
  }

  @GetMapping(value = "/audit-events/labels", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_AUDIT_READ)
  public ActivityLabelsResponse labels(@PathVariable final UUID institutionId) {
    return ActivityLabelsResponse.catalog();
  }

  @GetMapping(value = "/audit-events/{eventId}", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_AUDIT_READ)
  public GlobalAuditEventResponse auditEvent(
      @PathVariable final UUID institutionId, @PathVariable final UUID eventId) {
    return activity.get(institutionId, eventId, false);
  }

  @GetMapping(value = "/emails", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_EMAIL_READ)
  public PaginatedResponse<GlobalAuditEventResponse> emails(
      @PathVariable final UUID institutionId,
      @Valid @ModelAttribute final InstitutionalActivityFilter filter,
      @PageableDefault(size = 20) final Pageable pageable) {
    return PaginatedResponse.from(activity.list(institutionId, filter, true, pageable));
  }

  @GetMapping(value = "/emails/{eventId}", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_EMAIL_READ)
  public GlobalAuditEventResponse email(
      @PathVariable final UUID institutionId, @PathVariable final UUID eventId) {
    return activity.get(institutionId, eventId, true);
  }
}
