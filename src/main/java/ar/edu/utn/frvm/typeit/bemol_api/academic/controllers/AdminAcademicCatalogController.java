package ar.edu.utn.frvm.typeit.bemol_api.academic.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicSpaceResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicSpaceUsageResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.ActiveStatusRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateAcademicSpaceRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateInstrumentRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateShiftRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.InstrumentResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.ShiftResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateAcademicSpaceRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateInstrumentRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateShiftRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreateAcademicSpaceUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreateInstrumentUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreateShiftUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.GetAcademicSpaceUsageUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.GetAcademicSpaceUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.GetInstrumentUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.GetShiftUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListAcademicSpacesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListInstrumentsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListShiftsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateAcademicSpaceStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateAcademicSpaceUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateInstrumentStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateInstrumentUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateShiftStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateShiftUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
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
@RequestMapping("/admin/institutions/{institutionId}")
@RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
@Validated
@RequiredArgsConstructor
public class AdminAcademicCatalogController {

  private final CreateAcademicSpaceUseCase createAcademicSpaceUseCase;
  private final ListAcademicSpacesUseCase listAcademicSpacesUseCase;
  private final GetAcademicSpaceUseCase getAcademicSpaceUseCase;
  private final GetAcademicSpaceUsageUseCase getAcademicSpaceUsageUseCase;
  private final UpdateAcademicSpaceUseCase updateAcademicSpaceUseCase;
  private final UpdateAcademicSpaceStatusUseCase updateAcademicSpaceStatusUseCase;
  private final CreateInstrumentUseCase createInstrumentUseCase;
  private final ListInstrumentsUseCase listInstrumentsUseCase;
  private final GetInstrumentUseCase getInstrumentUseCase;
  private final UpdateInstrumentUseCase updateInstrumentUseCase;
  private final UpdateInstrumentStatusUseCase updateInstrumentStatusUseCase;
  private final CreateShiftUseCase createShiftUseCase;
  private final ListShiftsUseCase listShiftsUseCase;
  private final GetShiftUseCase getShiftUseCase;
  private final UpdateShiftUseCase updateShiftUseCase;
  private final UpdateShiftStatusUseCase updateShiftStatusUseCase;

  @PostMapping(value = "/academic-spaces", version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  public AcademicSpaceResponse createSpace(
      @PathVariable final UUID institutionId,
      @Valid @RequestBody final CreateAcademicSpaceRequest request) {
    return createAcademicSpaceUseCase.execute(institutionId, request);
  }

  @GetMapping(value = "/academic-spaces", version = Version.V1)
  public PaginatedResponse<AcademicSpaceResponse> listSpaces(
      @PathVariable final UUID institutionId,
      @RequestParam(required = false) @Size(max = 100) final String search,
      @RequestParam(required = false) final Boolean active,
      @RequestParam(required = false) final AcademicSpaceType type,
      @RequestParam(required = false) final AcademicSpaceFormat format,
      @RequestParam(defaultValue = "false") final boolean deleted,
      @PageableDefault(sort = "name", direction = Sort.Direction.ASC) final Pageable pageable) {
    return listAcademicSpacesUseCase.execute(
        institutionId, search, active, type, format, deleted, pageable);
  }

  @GetMapping(value = "/academic-spaces/{academicSpaceId}", version = Version.V1)
  public AcademicSpaceResponse getSpace(
      @PathVariable final UUID institutionId, @PathVariable final UUID academicSpaceId) {
    return getAcademicSpaceUseCase.execute(institutionId, academicSpaceId);
  }

  @GetMapping(value = "/academic-spaces/{academicSpaceId}/usage", version = Version.V1)
  public AcademicSpaceUsageResponse getSpaceUsage(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID academicSpaceId,
      @PageableDefault(sort = "name", direction = Sort.Direction.ASC) final Pageable pageable) {
    return getAcademicSpaceUsageUseCase.execute(institutionId, academicSpaceId, pageable);
  }

  @PutMapping(value = "/academic-spaces/{academicSpaceId}", version = Version.V1)
  public AcademicSpaceResponse updateSpace(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID academicSpaceId,
      @Valid @RequestBody final UpdateAcademicSpaceRequest request) {
    return updateAcademicSpaceUseCase.execute(institutionId, academicSpaceId, request);
  }

  @PatchMapping(value = "/academic-spaces/{academicSpaceId}/status", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateSpaceStatus(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID academicSpaceId,
      @Valid @RequestBody final ActiveStatusRequest request) {
    updateAcademicSpaceStatusUseCase.execute(institutionId, academicSpaceId, request);
  }

  @PostMapping(value = "/instruments", version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  public InstrumentResponse createInstrument(
      @PathVariable final UUID institutionId,
      @Valid @RequestBody final CreateInstrumentRequest request) {
    return createInstrumentUseCase.execute(institutionId, request);
  }

  @GetMapping(value = "/instruments", version = Version.V1)
  public PaginatedResponse<InstrumentResponse> listInstruments(
      @PathVariable final UUID institutionId,
      @RequestParam(required = false) @Size(max = 100) final String search,
      @RequestParam(required = false) final Boolean active,
      @RequestParam(defaultValue = "false") final boolean deleted,
      @PageableDefault(sort = "name", direction = Sort.Direction.ASC) final Pageable pageable) {
    return listInstrumentsUseCase.execute(institutionId, search, active, deleted, pageable);
  }

  @GetMapping(value = "/instruments/{instrumentId}", version = Version.V1)
  public InstrumentResponse getInstrument(
      @PathVariable final UUID institutionId, @PathVariable final UUID instrumentId) {
    return getInstrumentUseCase.execute(institutionId, instrumentId);
  }

  @PutMapping(value = "/instruments/{instrumentId}", version = Version.V1)
  public InstrumentResponse updateInstrument(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID instrumentId,
      @Valid @RequestBody final UpdateInstrumentRequest request) {
    return updateInstrumentUseCase.execute(institutionId, instrumentId, request);
  }

  @PatchMapping(value = "/instruments/{instrumentId}/status", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateInstrumentStatus(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID instrumentId,
      @Valid @RequestBody final ActiveStatusRequest request) {
    updateInstrumentStatusUseCase.execute(institutionId, instrumentId, request);
  }

  @PostMapping(value = "/shifts", version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  public ShiftResponse createShift(
      @PathVariable final UUID institutionId,
      @Valid @RequestBody final CreateShiftRequest request) {
    return createShiftUseCase.execute(institutionId, request);
  }

  @GetMapping(value = "/shifts", version = Version.V1)
  public PaginatedResponse<ShiftResponse> listShifts(
      @PathVariable final UUID institutionId,
      @RequestParam(required = false) @Size(max = 100) final String search,
      @RequestParam(required = false) final Boolean active,
      @RequestParam(defaultValue = "false") final boolean deleted,
      @PageableDefault(sort = "name", direction = Sort.Direction.ASC) final Pageable pageable) {
    return listShiftsUseCase.execute(institutionId, search, active, deleted, pageable);
  }

  @GetMapping(value = "/shifts/{shiftId}", version = Version.V1)
  public ShiftResponse getShift(
      @PathVariable final UUID institutionId, @PathVariable final UUID shiftId) {
    return getShiftUseCase.execute(institutionId, shiftId);
  }

  @PutMapping(value = "/shifts/{shiftId}", version = Version.V1)
  public ShiftResponse updateShift(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID shiftId,
      @Valid @RequestBody final UpdateShiftRequest request) {
    return updateShiftUseCase.execute(institutionId, shiftId, request);
  }

  @PatchMapping(value = "/shifts/{shiftId}/status", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateShiftStatus(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID shiftId,
      @Valid @RequestBody final ActiveStatusRequest request) {
    updateShiftStatusUseCase.execute(institutionId, shiftId, request);
  }
}
