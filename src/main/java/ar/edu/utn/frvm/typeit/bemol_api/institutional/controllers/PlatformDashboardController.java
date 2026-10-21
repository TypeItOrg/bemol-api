package ar.edu.utn.frvm.typeit.bemol_api.institutional.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.PlatformDashboardResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.GetPlatformDashboardUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class PlatformDashboardController {

  private final GetPlatformDashboardUseCase getPlatformDashboardUseCase;

  @GetMapping(version = Version.V1)
  @RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
  public PlatformDashboardResponse get() {
    return getPlatformDashboardUseCase.execute();
  }
}
