package ar.edu.utn.frvm.typeit.bemol_api.institutional.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.PlatformPersonSummaryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.ListPlatformPeopleUseCase;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/people")
@RequiredArgsConstructor
public class PlatformPeopleController {

  private final ListPlatformPeopleUseCase listPlatformPeopleUseCase;

  @GetMapping(version = Version.V1)
  @RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
  public PaginatedResponse<PlatformPersonSummaryResponse> list(
      @RequestParam(required = false) @Size(max = 100) final String search,
      @RequestParam(required = false) final UUID institutionId,
      @RequestParam(required = false) final SystemRoleCode roleCode,
      @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC)
          final Pageable pageable) {
    return listPlatformPeopleUseCase.execute(search, institutionId, roleCode, pageable);
  }
}
