package ar.edu.utn.frvm.typeit.bemol_api.academic.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicLevelResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateAcademicLevelRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateAcademicLevelRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreateAcademicLevelUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.DeleteAcademicLevelUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.GetAcademicLevelUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListAcademicLevelsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateAcademicLevelUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/institutions/{institutionId}")
@RequiresInstitutionAccess
@RequiredArgsConstructor
public class AcademicLevelController {

  private final CreateAcademicLevelUseCase createAcademicLevelUseCase;
  private final ListAcademicLevelsUseCase listAcademicLevelsUseCase;
  private final GetAcademicLevelUseCase getAcademicLevelUseCase;
  private final UpdateAcademicLevelUseCase updateAcademicLevelUseCase;
  private final DeleteAcademicLevelUseCase deleteAcademicLevelUseCase;

  @PostMapping(value = "/study-plans/{studyPlanId}/academic-levels", version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission(PermissionCode.STUDY_PLAN_CURRICULUM_UPDATE)
  public AcademicLevelResponse create(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID studyPlanId,
      @Valid @RequestBody final CreateAcademicLevelRequest request) {
    return createAcademicLevelUseCase.execute(institutionId, studyPlanId, request);
  }

  @GetMapping(value = "/study-plans/{studyPlanId}/academic-levels", version = Version.V1)
  @RequiresPermission(PermissionCode.STUDY_PLAN_READ)
  public List<AcademicLevelResponse> list(
      @PathVariable final UUID institutionId, @PathVariable final UUID studyPlanId) {
    return listAcademicLevelsUseCase.execute(institutionId, studyPlanId);
  }

  @GetMapping(value = "/academic-levels/{academicLevelId}", version = Version.V1)
  @RequiresPermission(PermissionCode.STUDY_PLAN_READ)
  public AcademicLevelResponse get(
      @PathVariable final UUID institutionId, @PathVariable final UUID academicLevelId) {
    return getAcademicLevelUseCase.execute(institutionId, academicLevelId);
  }

  @PutMapping(value = "/academic-levels/{academicLevelId}", version = Version.V1)
  @RequiresPermission(PermissionCode.STUDY_PLAN_CURRICULUM_UPDATE)
  public AcademicLevelResponse update(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID academicLevelId,
      @Valid @RequestBody final UpdateAcademicLevelRequest request) {
    return updateAcademicLevelUseCase.execute(institutionId, academicLevelId, request);
  }

  @DeleteMapping(value = "/academic-levels/{academicLevelId}", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission(PermissionCode.STUDY_PLAN_CURRICULUM_UPDATE)
  public void delete(
      @PathVariable final UUID institutionId, @PathVariable final UUID academicLevelId) {
    deleteAcademicLevelUseCase.execute(institutionId, academicLevelId);
  }
}
