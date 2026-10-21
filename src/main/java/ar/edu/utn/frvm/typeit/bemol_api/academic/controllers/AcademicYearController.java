package ar.edu.utn.frvm.typeit.bemol_api.academic.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicYearResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicYearStatusRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateAcademicYearRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateAcademicYearRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CountCoursesForYearUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreateAcademicYearUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.GetAcademicYearUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListAcademicYearsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateAcademicYearStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateAcademicYearUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;
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
@RequestMapping("/institutions/{institutionId}/academic-years")
@RequiresInstitutionAccess
@Validated
@RequiredArgsConstructor
public class AcademicYearController {

  private final CreateAcademicYearUseCase createAcademicYearUseCase;
  private final ListAcademicYearsUseCase listAcademicYearsUseCase;
  private final GetAcademicYearUseCase getAcademicYearUseCase;
  private final UpdateAcademicYearUseCase updateAcademicYearUseCase;
  private final UpdateAcademicYearStatusUseCase updateAcademicYearStatusUseCase;
  private final CountCoursesForYearUseCase countCoursesForYearUseCase;

  @PostMapping(version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission(PermissionCode.ACADEMIC_YEAR_CREATE)
  public AcademicYearResponse create(
      @PathVariable final UUID institutionId,
      @Valid @RequestBody final CreateAcademicYearRequest request) {
    return createAcademicYearUseCase.execute(institutionId, request);
  }

  @GetMapping(version = Version.V1)
  @RequiresPermission(PermissionCode.ACADEMIC_YEAR_READ)
  public PaginatedResponse<AcademicYearResponse> list(
      @PathVariable final UUID institutionId,
      @RequestParam(required = false) @Size(max = 100) final String search,
      @RequestParam(required = false) final AcademicYearStatus status,
      @RequestParam(required = false) final Integer year,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          final LocalDate startDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          final LocalDate endDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          final LocalDate validOn,
      @RequestParam(defaultValue = "false") final boolean deleted,
      @PageableDefault(sort = "year", direction = Sort.Direction.ASC) final Pageable pageable) {
    if (deleted) {
      return listAcademicYearsUseCase.execute(
          institutionId, search, status, year, startDate, endDate, validOn, true, pageable);
    }
    if (validOn != null) {
      return listAcademicYearsUseCase.execute(
          institutionId, search, status, year, startDate, endDate, validOn, pageable);
    }
    return listAcademicYearsUseCase.execute(
        institutionId, search, status, year, startDate, endDate, pageable);
  }

  @GetMapping(value = "/{academicYearId}", version = Version.V1)
  @RequiresPermission(PermissionCode.ACADEMIC_YEAR_READ)
  public AcademicYearResponse get(
      @PathVariable final UUID institutionId, @PathVariable final UUID academicYearId) {
    return getAcademicYearUseCase.execute(institutionId, academicYearId);
  }

  @PutMapping(value = "/{academicYearId}", version = Version.V1)
  @RequiresPermission(PermissionCode.ACADEMIC_YEAR_UPDATE)
  public AcademicYearResponse update(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID academicYearId,
      @Valid @RequestBody final UpdateAcademicYearRequest request) {
    return updateAcademicYearUseCase.execute(institutionId, academicYearId, request);
  }

  @PatchMapping(value = "/{academicYearId}/status", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission(PermissionCode.ACADEMIC_YEAR_STATUS_UPDATE)
  public void updateStatus(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID academicYearId,
      @Valid @RequestBody final AcademicYearStatusRequest request) {
    updateAcademicYearStatusUseCase.execute(institutionId, academicYearId, request);
  }

  @GetMapping(value = "/{academicYearId}/courses/count", version = Version.V1)
  @RequiresPermission(PermissionCode.ACADEMIC_YEAR_READ)
  public Map<String, Long> countCoursesForYear(
      @PathVariable final UUID institutionId, @PathVariable final UUID academicYearId) {
    final long count = countCoursesForYearUseCase.execute(institutionId, academicYearId);
    return Map.of("count", count);
  }
}
