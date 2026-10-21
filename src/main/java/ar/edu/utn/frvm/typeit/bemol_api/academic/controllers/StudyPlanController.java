package ar.edu.utn.frvm.typeit.bemol_api.academic.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateStudyPlanRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateStudyPlanVersionRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.StudyPlanCurriculumResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.StudyPlanResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.StudyPlanStatusRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateStudyPlanRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreateStudyPlanUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreateStudyPlanVersionUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.GetStudyPlanCurriculumUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.GetStudyPlanUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListStudyPlansUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListTrainingPathStudyPlansUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateStudyPlanStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateStudyPlanUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/institutions/{institutionId}")
@RequiresInstitutionAccess
@Validated
@RequiredArgsConstructor
public class StudyPlanController {

  private final CreateStudyPlanUseCase createStudyPlanUseCase;
  private final CreateStudyPlanVersionUseCase createStudyPlanVersionUseCase;
  private final ListTrainingPathStudyPlansUseCase listTrainingPathStudyPlansUseCase;
  private final ListStudyPlansUseCase listStudyPlansUseCase;
  private final GetStudyPlanUseCase getStudyPlanUseCase;
  private final UpdateStudyPlanUseCase updateStudyPlanUseCase;
  private final UpdateStudyPlanStatusUseCase updateStudyPlanStatusUseCase;
  private final GetStudyPlanCurriculumUseCase getStudyPlanCurriculumUseCase;

  @PostMapping(value = "/training-paths/{trainingPathId}/study-plans", version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission(PermissionCode.STUDY_PLAN_CREATE)
  public StudyPlanResponse create(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID trainingPathId,
      @Valid @RequestBody final CreateStudyPlanRequest request) {
    return createStudyPlanUseCase.execute(institutionId, trainingPathId, request);
  }

  @PostMapping(value = "/study-plans/{studyPlanId}/versions", version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission(PermissionCode.STUDY_PLAN_CREATE)
  public StudyPlanResponse createVersion(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID studyPlanId,
      @Valid @RequestBody final CreateStudyPlanVersionRequest request) {
    return createStudyPlanVersionUseCase.execute(institutionId, studyPlanId, request);
  }

  @GetMapping(value = "/training-paths/{trainingPathId}/study-plans", version = Version.V1)
  @RequiresPermission(PermissionCode.STUDY_PLAN_READ)
  public PaginatedResponse<StudyPlanResponse> listByTrainingPath(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID trainingPathId,
      @PageableDefault(sort = "name", direction = Sort.Direction.ASC) final Pageable pageable) {
    return listTrainingPathStudyPlansUseCase.execute(institutionId, trainingPathId, pageable);
  }

  @GetMapping(value = "/study-plans", version = Version.V1)
  @RequiresPermission(PermissionCode.STUDY_PLAN_READ)
  public PaginatedResponse<StudyPlanResponse> list(
      @PathVariable final UUID institutionId,
      @RequestParam(required = false) @Size(max = 100) final String search,
      @RequestParam(required = false) final StudyPlanStatus status,
      @RequestParam(required = false) final UUID trainingPathId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          final LocalDate validOn,
      @RequestParam(defaultValue = "false") final boolean deleted,
      @RequestParam(defaultValue = "false") final boolean published,
      @PageableDefault(sort = "name", direction = Sort.Direction.ASC) final Pageable pageable) {
    if (published) {
      return listStudyPlansUseCase.published(institutionId, trainingPathId, search, pageable);
    }

    if (trainingPathId == null && validOn == null) {
      return listStudyPlansUseCase.execute(
          institutionId, search, status, null, null, deleted, pageable);
    }
    return listStudyPlansUseCase.execute(
        institutionId, search, status, trainingPathId, validOn, deleted, pageable);
  }

  @GetMapping(value = "/study-plans/{studyPlanId}", version = Version.V1)
  @RequiresPermission(PermissionCode.STUDY_PLAN_READ)
  public StudyPlanResponse get(
      @PathVariable final UUID institutionId, @PathVariable final UUID studyPlanId) {
    return getStudyPlanUseCase.execute(institutionId, studyPlanId);
  }

  @PutMapping(value = "/study-plans/{studyPlanId}", version = Version.V1)
  @RequiresPermission(PermissionCode.STUDY_PLAN_UPDATE)
  public StudyPlanResponse update(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID studyPlanId,
      @Valid @RequestBody final UpdateStudyPlanRequest request) {
    return updateStudyPlanUseCase.execute(institutionId, studyPlanId, request);
  }

  @PatchMapping(value = "/study-plans/{studyPlanId}/status", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission(PermissionCode.STUDY_PLAN_STATUS_UPDATE)
  public void updateStatus(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID studyPlanId,
      @Valid @RequestBody final StudyPlanStatusRequest request) {
    updateStudyPlanStatusUseCase.execute(institutionId, studyPlanId, request);
  }

  @GetMapping(value = "/study-plans/{studyPlanId}/curriculum", version = Version.V1)
  @RequiresPermission(PermissionCode.STUDY_PLAN_READ)
  public StudyPlanCurriculumResponse curriculum(
      @PathVariable final UUID institutionId, @PathVariable final UUID studyPlanId) {
    return getStudyPlanCurriculumUseCase.execute(institutionId, studyPlanId);
  }
}
