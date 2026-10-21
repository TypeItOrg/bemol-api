package ar.edu.utn.frvm.typeit.bemol_api.enrollment.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentPeriodStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CreateEnrollmentPeriodRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPeriodResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPeriodStatusRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.UpdateEnrollmentPeriodRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.CreateEnrollmentPeriodUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.DeleteEnrollmentPeriodUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.GetEnrollmentPeriodUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.ListEnrollmentPeriodsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.UpdateEnrollmentPeriodStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.UpdateEnrollmentPeriodUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/institutions/{institutionId}/enrollment-periods")
@RequiresInstitutionAccess
@Validated
@RequiredArgsConstructor
public class EnrollmentPeriodController {

  private final CreateEnrollmentPeriodUseCase createEnrollmentPeriodUseCase;
  private final ListEnrollmentPeriodsUseCase listEnrollmentPeriodsUseCase;
  private final GetEnrollmentPeriodUseCase getEnrollmentPeriodUseCase;
  private final UpdateEnrollmentPeriodUseCase updateEnrollmentPeriodUseCase;
  private final UpdateEnrollmentPeriodStatusUseCase updateEnrollmentPeriodStatusUseCase;
  private final DeleteEnrollmentPeriodUseCase deleteEnrollmentPeriodUseCase;

  @PostMapping(version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission(PermissionCode.ENROLLMENT_PERIOD_CREATE)
  public EnrollmentPeriodResponse create(
      @PathVariable final UUID institutionId,
      @Valid @RequestBody final CreateEnrollmentPeriodRequest request) {
    return createEnrollmentPeriodUseCase.execute(institutionId, request);
  }

  @GetMapping(version = Version.V1)
  @RequiresPermission(PermissionCode.ENROLLMENT_PERIOD_READ)
  public PaginatedResponse<EnrollmentPeriodResponse> list(
      @PathVariable final UUID institutionId,
      @RequestParam(required = false) final UUID academicYearId,
      @RequestParam(required = false) final EnrollmentPeriodStatus status,
      @RequestParam(required = false) @Size(max = 100) final String search,
      @RequestParam(defaultValue = "false") final boolean deleted,
      @PageableDefault(sort = "startDate", direction = Sort.Direction.DESC)
          final Pageable pageable) {
    return listEnrollmentPeriodsUseCase.execute(
        institutionId, academicYearId, status, search, deleted, pageable);
  }

  @GetMapping(value = "/{periodId}", version = Version.V1)
  @RequiresPermission(PermissionCode.ENROLLMENT_PERIOD_READ)
  public EnrollmentPeriodResponse get(
      @PathVariable final UUID institutionId, @PathVariable final UUID periodId) {
    return getEnrollmentPeriodUseCase.execute(institutionId, periodId);
  }

  @PutMapping(value = "/{periodId}", version = Version.V1)
  @RequiresPermission(PermissionCode.ENROLLMENT_PERIOD_UPDATE)
  public EnrollmentPeriodResponse update(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID periodId,
      @Valid @RequestBody final UpdateEnrollmentPeriodRequest request) {
    return updateEnrollmentPeriodUseCase.execute(institutionId, periodId, request);
  }

  @PatchMapping(value = "/{periodId}/status", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission(PermissionCode.ENROLLMENT_PERIOD_STATUS_UPDATE)
  public void updateStatus(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID periodId,
      @Valid @RequestBody final EnrollmentPeriodStatusRequest request) {
    updateEnrollmentPeriodStatusUseCase.execute(institutionId, periodId, request);
  }

  @DeleteMapping(value = "/{periodId}", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission(PermissionCode.ENROLLMENT_PERIOD_DELETE)
  public void delete(@PathVariable final UUID institutionId, @PathVariable final UUID periodId) {
    deleteEnrollmentPeriodUseCase.execute(institutionId, periodId);
  }
}
