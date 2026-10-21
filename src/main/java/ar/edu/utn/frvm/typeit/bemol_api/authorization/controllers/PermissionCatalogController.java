package ar.edu.utn.frvm.typeit.bemol_api.authorization.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.PermissionCatalogItemResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/permissions")
public class PermissionCatalogController {

  @GetMapping(version = Version.V1)
  @RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
  public List<PermissionCatalogItemResponse> list() {
    return Arrays.stream(PermissionCode.values()).map(PermissionCatalogItemResponse::from).toList();
  }
}
