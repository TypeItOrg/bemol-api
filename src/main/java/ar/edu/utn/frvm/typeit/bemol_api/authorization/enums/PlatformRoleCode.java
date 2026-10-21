package ar.edu.utn.frvm.typeit.bemol_api.authorization.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlatformRoleCode {
  PLATFORM_ADMIN("Administrador de la Plataforma");

  private final String displayName;
}
