package ar.edu.utn.frvm.typeit.bemol_api.academic.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.ActiveStatusRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateTrainingPathRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.TrainingPathResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateTrainingPathRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreateTrainingPathUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.GetTrainingPathUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListTrainingPathsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateTrainingPathStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateTrainingPathUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/institutions/{institutionId}/training-paths")
@RequiresInstitutionAccess
@Validated
@RequiredArgsConstructor
public class TrainingPathController {

  private final CreateTrainingPathUseCase createTrainingPathUseCase;
  private final ListTrainingPathsUseCase listTrainingPathsUseCase;
  private final GetTrainingPathUseCase getTrainingPathUseCase;
  private final UpdateTrainingPathUseCase updateTrainingPathUseCase;
  private final UpdateTrainingPathStatusUseCase updateTrainingPathStatusUseCase;

  @PostMapping(version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission(PermissionCode.TRAINING_PATH_CREATE)
  public TrainingPathResponse create(
      @PathVariable final UUID institutionId,
      @Valid @RequestBody final CreateTrainingPathRequest request) {
    return createTrainingPathUseCase.execute(institutionId, request);
  }

  @GetMapping(version = Version.V1)
  @RequiresPermission(PermissionCode.TRAINING_PATH_READ)
  public PaginatedResponse<TrainingPathResponse> list(
      @PathVariable final UUID institutionId,
      @RequestParam(required = false) @Size(max = 100) final String search,
      @RequestParam(required = false) final Boolean active,
      @RequestParam(defaultValue = "false") final boolean deleted,
      @PageableDefault(sort = "name", direction = Sort.Direction.ASC) final Pageable pageable) {
    return listTrainingPathsUseCase.execute(institutionId, search, active, deleted, pageable);
  }

  @GetMapping(value = "/{trainingPathId}", version = Version.V1)
  @RequiresPermission(PermissionCode.TRAINING_PATH_READ)
  public TrainingPathResponse get(
      @PathVariable final UUID institutionId, @PathVariable final UUID trainingPathId) {
    return getTrainingPathUseCase.execute(institutionId, trainingPathId);
  }

  @PutMapping(value = "/{trainingPathId}", version = Version.V1)
  @RequiresPermission(PermissionCode.TRAINING_PATH_UPDATE)
  public TrainingPathResponse update(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID trainingPathId,
      @Valid @RequestBody final UpdateTrainingPathRequest request) {
    return updateTrainingPathUseCase.execute(institutionId, trainingPathId, request);
  }

  @PatchMapping(value = "/{trainingPathId}/status", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission(PermissionCode.TRAINING_PATH_STATUS_UPDATE)
  public void updateStatus(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID trainingPathId,
      @Valid @RequestBody final ActiveStatusRequest request) {
    updateTrainingPathStatusUseCase.execute(institutionId, trainingPathId, request);
  }
}
