package ar.edu.utn.frvm.typeit.bemol_api.institutional.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.InstitutionDetailResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.InstitutionListItemResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.InstitutionPublicAccessResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.GetInstitutionUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.InstitutionLogoUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.ListInstitutionsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.ResolveInstitutionPublicAccessUseCase;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/institutions")
@RequiredArgsConstructor
public class InstitutionController {

  private final ListInstitutionsUseCase listInstitutionsUseCase;
  private final GetInstitutionUseCase getInstitutionUseCase;
  private final ResolveInstitutionPublicAccessUseCase resolvePublicAccess;
  private final InstitutionLogoUseCase logos;

  @GetMapping(value = "/by-subdomain/{name}", version = Version.V1)
  public InstitutionPublicAccessResponse resolve(@PathVariable final String name) {
    return resolvePublicAccess.execute(name);
  }

  @GetMapping(value = "/{id}/logo", version = Version.V1)
  public ResponseEntity<Resource> logo(@PathVariable final UUID id) {
    final var logo = logos.get(id);
    return ResponseEntity.ok()
        .contentType(org.springframework.http.MediaType.parseMediaType(logo.contentType()))
        .contentLength(logo.size())
        .eTag(logo.version())
        .cacheControl(org.springframework.http.CacheControl.noCache())
        .header("X-Content-Type-Options", "nosniff")
        .body(logo.resource());
  }

  @GetMapping(version = Version.V1)
  public PaginatedResponse<InstitutionListItemResponse> list(
      @RequestParam(required = false) @Size(max = 100) final String search,
      @RequestParam(required = false) final Boolean active,
      @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
    return listInstitutionsUseCase.execute(search, active, pageable);
  }

  @GetMapping(value = "/{id}", version = Version.V1)
  public InstitutionDetailResponse get(@PathVariable UUID id) {
    return getInstitutionUseCase.execute(id);
  }
}
