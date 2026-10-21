package ar.edu.utn.frvm.typeit.bemol_api.enrollment.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicLevelResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.*;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.*;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.ListEnrollmentPeriodLevelsUseCase;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class EnrollmentPeriodOptionsController {
  private final ListEnrollmentPeriodLevelsUseCase useCase;

  @GetMapping(
      value = "/institutions/{institutionId}/enrollment-period-options/{studyPlanId}/levels",
      version = Version.V1)
  @RequiresInstitutionAccess
  @RequiresAnyPermission({
    PermissionCode.ENROLLMENT_PERIOD_CREATE,
    PermissionCode.ENROLLMENT_PERIOD_UPDATE
  })
  public List<AcademicLevelResponse> institutional(
      @PathVariable UUID institutionId,
      @PathVariable UUID studyPlanId,
      @RequestParam PermissionCode operation) {
    return useCase.execute(institutionId, studyPlanId, operation);
  }

  @GetMapping(
      value = "/admin/institutions/{institutionId}/enrollment-period-options/{studyPlanId}/levels",
      version = Version.V1)
  @RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
  public List<AcademicLevelResponse> platform(
      @PathVariable UUID institutionId,
      @PathVariable UUID studyPlanId,
      @RequestParam PermissionCode operation) {
    return useCase.execute(institutionId, studyPlanId, operation);
  }
}
