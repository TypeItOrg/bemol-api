package ar.edu.utn.frvm.typeit.bemol_api.authorization.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SystemRoleCode {
  INSTITUTIONAL_AUTHORITY("Administrador Institucional"),
  ADMINISTRATIVE("Administrativo"),
  TEACHER("Profesor"),
  APPLICANT("Postulante"),
  STUDENT("Estudiante");

  private final String displayName;
}
