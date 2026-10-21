package ar.edu.utn.frvm.typeit.bemol_api.academic.controllers;

import static ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode.*;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicSelectionOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListAcademicSelectionOptionsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.*;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.*;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AcademicSelectionOptionsController {
  private final ListAcademicSelectionOptionsUseCase useCase;

  @GetMapping(
      value = "/institutions/{institutionId}/academic-options/{resource}",
      version = Version.V1)
  @RequiresInstitutionAccess
  @RequiresAnyPermission({
    TRAINING_PATH_READ,
    STUDY_PLAN_READ,
    STUDY_PLAN_CREATE,
    STUDY_PLAN_UPDATE,
    STUDY_PLAN_CURRICULUM_UPDATE,
    COURSE_READ,
    COURSE_CREATE,
    COURSE_UPDATE,
    ENROLLMENT_PERIOD_READ,
    ENROLLMENT_PERIOD_CREATE,
    ENROLLMENT_PERIOD_UPDATE,
    ENROLLMENT_APPLICATION_READ,
    COURSE_ENROLLMENT_READ,
    ACADEMIC_YEAR_READ,
    ACADEMIC_SPACE_READ,
    INSTRUMENT_READ
  })
  public PaginatedResponse<AcademicSelectionOptionResponse> institutional(
      @PathVariable UUID institutionId,
      @PathVariable String resource,
      @RequestParam(required = false) @Size(max = 100) String search,
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) Boolean published,
      @RequestParam(required = false) UUID trainingPathId,
      @RequestParam(required = false) StudyPlanStatus status,
      @RequestParam(required = false) PermissionCode operation,
      @RequestParam(required = false) UUID id,
      Pageable pageable) {
    return useCase.execute(
        institutionId,
        resource,
        search,
        active,
        published,
        trainingPathId,
        status,
        pageable,
        operation,
        id);
  }

  @GetMapping(
      value = "/admin/institutions/{institutionId}/academic-options/{resource}",
      version = Version.V1)
  @RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
  public PaginatedResponse<AcademicSelectionOptionResponse> platform(
      @PathVariable UUID institutionId,
      @PathVariable String resource,
      @RequestParam(required = false) @Size(max = 100) String search,
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) Boolean published,
      @RequestParam(required = false) UUID trainingPathId,
      @RequestParam(required = false) StudyPlanStatus status,
      @RequestParam(required = false) PermissionCode operation,
      @RequestParam(required = false) UUID id,
      Pageable pageable) {
    return useCase.execute(
        institutionId,
        resource,
        search,
        active,
        published,
        trainingPathId,
        status,
        pageable,
        operation,
        id);
  }
}
