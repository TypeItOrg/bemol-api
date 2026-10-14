package ar.edu.utn.frvm.typeit.boero_api.academic.controllers;

import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.PlatformDocumentDefinitionResponse;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.DocumentCatalogUseCase;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
@RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
@RequestMapping("/admin/document-definitions")
public class PlatformDocumentCatalogController {
  private final DocumentCatalogUseCase catalog;

  @GetMapping(version = Version.V1)
  public PaginatedResponse<PlatformDocumentDefinitionResponse> list(
      @RequestParam(required = false) @Nullable UUID institutionId,
      @RequestParam(defaultValue = "") @Size(max = 150) String search,
      @RequestParam(required = false) @Nullable Boolean active,
      @PageableDefault(sort = {"name", "id"}) Pageable pageable) {
    return PaginatedResponse.from(catalog.listPlatform(institutionId, search, active, pageable));
  }
}
