package ar.edu.utn.frvm.typeit.boero_api.institutional.controllers;

import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.InstitutionAdminDetailResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.InstitutionAdminListItemResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.InstitutionDetailResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.requests.CreateInstitutionRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.requests.UpdateInstitutionStatusRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.requests.UpdateInstitutionWithBrandingRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.CreateInstitutionUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.GetInstitutionAdminUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.ListInstitutionsAdminUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.UpdateInstitutionStatusUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.UpdateInstitutionWithBrandingUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/admin/institutions")
@RequiredArgsConstructor
@RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
public class PlatformInstitutionController {

  private final ListInstitutionsAdminUseCase listInstitutionsAdminUseCase;
  private final GetInstitutionAdminUseCase getInstitutionAdminUseCase;
  private final CreateInstitutionUseCase createInstitutionUseCase;
  private final UpdateInstitutionStatusUseCase updateInstitutionStatusUseCase;
  private final UpdateInstitutionWithBrandingUseCase updateWithBranding;

  @GetMapping(version = Version.V1)
  public PaginatedResponse<InstitutionAdminListItemResponse> list(
      @RequestParam(required = false) @Size(max = 100) final String search,
      @RequestParam(required = false) final Boolean active,
      @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
    return listInstitutionsAdminUseCase.execute(search, active, pageable);
  }

  @GetMapping(value = "/{id}", version = Version.V1)
  public InstitutionAdminDetailResponse get(@PathVariable final UUID id) {
    return getInstitutionAdminUseCase.execute(id);
  }

  @PostMapping(version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  public InstitutionDetailResponse create(
      @Valid @RequestBody final CreateInstitutionRequest request) {
    return createInstitutionUseCase.execute(request);
  }

  @PutMapping(value = "/{id}", version = Version.V1, consumes = "multipart/form-data")
  public InstitutionDetailResponse updateWithBranding(
      @PathVariable final UUID id,
      @Valid @RequestPart("data") final UpdateInstitutionWithBrandingRequest request,
      @RequestPart(value = "file", required = false) final @Nullable MultipartFile file) {
    return updateWithBranding.execute(id, request, file);
  }

  @PatchMapping(value = "/{id}/status", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateStatus(
      @PathVariable final UUID id,
      @Valid @RequestBody final UpdateInstitutionStatusRequest request) {
    updateInstitutionStatusUseCase.execute(id, request.active());
  }
}
