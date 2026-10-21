package ar.edu.utn.frvm.typeit.bemol_api.academic.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicSpaceResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicYearResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.InstrumentResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.ShiftResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.StudyPlanResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.TrainingPathResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListAcademicSpacesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListAcademicYearsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListInstrumentsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListShiftsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListStudyPlansUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListTrainingPathsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
@Validated
@RequiredArgsConstructor
public class PlatformAcademicCollectionController {

  private final ListAcademicYearsUseCase listAcademicYearsUseCase;
  private final ListTrainingPathsUseCase listTrainingPathsUseCase;
  private final ListStudyPlansUseCase listStudyPlansUseCase;
  private final ListAcademicSpacesUseCase listAcademicSpacesUseCase;
  private final ListInstrumentsUseCase listInstrumentsUseCase;
  private final ListShiftsUseCase listShiftsUseCase;

  @GetMapping(value = "/shifts", version = Version.V1)
  public PaginatedResponse<ShiftResponse> listShifts(
      @RequestParam(required = false) final UUID institutionId,
      @RequestParam(required = false) @Size(max = 100) final String search,
      @RequestParam(required = false) final Boolean active,
      @RequestParam(defaultValue = "false") final boolean deleted,
      @PageableDefault(
              sort = {"institution.name", "name"},
              direction = Sort.Direction.ASC)
          final Pageable pageable) {
    return listShiftsUseCase.execute(institutionId, search, active, deleted, pageable);
  }

  @GetMapping(value = "/academic-years", version = Version.V1)
  public PaginatedResponse<AcademicYearResponse> listAcademicYears(
      @RequestParam(required = false) final UUID institutionId,
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
      @PageableDefault(
              sort = {"institution.name", "year"},
              direction = Sort.Direction.ASC)
          final Pageable pageable) {
    return listAcademicYearsUseCase.execute(
        institutionId, search, status, year, startDate, endDate, validOn, deleted, pageable);
  }

  @GetMapping(value = "/training-paths", version = Version.V1)
  public PaginatedResponse<TrainingPathResponse> listTrainingPaths(
      @RequestParam(required = false) final UUID institutionId,
      @RequestParam(required = false) @Size(max = 100) final String search,
      @RequestParam(required = false) final Boolean active,
      @RequestParam(defaultValue = "false") final boolean deleted,
      @PageableDefault(
              sort = {"institution.name", "name"},
              direction = Sort.Direction.ASC)
          final Pageable pageable) {
    return listTrainingPathsUseCase.execute(institutionId, search, active, deleted, pageable);
  }

  @GetMapping(value = "/study-plans", version = Version.V1)
  public PaginatedResponse<StudyPlanResponse> listStudyPlans(
      @RequestParam(required = false) final UUID institutionId,
      @RequestParam(required = false) @Size(max = 100) final String search,
      @RequestParam(required = false) final StudyPlanStatus status,
      @RequestParam(required = false) final UUID trainingPathId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          final LocalDate validOn,
      @RequestParam(defaultValue = "false") final boolean deleted,
      @PageableDefault(
              sort = {"institution.name", "name"},
              direction = Sort.Direction.ASC)
          final Pageable pageable) {
    return listStudyPlansUseCase.execute(
        institutionId, search, status, trainingPathId, validOn, deleted, pageable);
  }

  @GetMapping(value = "/academic-spaces", version = Version.V1)
  public PaginatedResponse<AcademicSpaceResponse> listAcademicSpaces(
      @RequestParam(required = false) final UUID institutionId,
      @RequestParam(required = false) @Size(max = 100) final String search,
      @RequestParam(required = false) final Boolean active,
      @RequestParam(required = false) final AcademicSpaceType type,
      @RequestParam(required = false) final AcademicSpaceFormat format,
      @RequestParam(defaultValue = "false") final boolean deleted,
      @PageableDefault(
              sort = {"institution.name", "name"},
              direction = Sort.Direction.ASC)
          final Pageable pageable) {
    return listAcademicSpacesUseCase.execute(
        institutionId, search, active, type, format, deleted, pageable);
  }

  @GetMapping(value = "/instruments", version = Version.V1)
  public PaginatedResponse<InstrumentResponse> listInstruments(
      @RequestParam(required = false) final UUID institutionId,
      @RequestParam(required = false) @Size(max = 100) final String search,
      @RequestParam(required = false) final Boolean active,
      @RequestParam(defaultValue = "false") final boolean deleted,
      @PageableDefault(
              sort = {"institution.name", "name"},
              direction = Sort.Direction.ASC)
          final Pageable pageable) {
    return listInstrumentsUseCase.execute(institutionId, search, active, deleted, pageable);
  }
}
