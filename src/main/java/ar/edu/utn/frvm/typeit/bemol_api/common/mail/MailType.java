package ar.edu.utn.frvm.typeit.bemol_api.common.mail;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MailType {
  ACCOUNT_EMAIL_CHANGE("Cambio de correo de acceso"),
  EMAIL_VERIFICATION("Verificación de correo"),
  PASSWORD_RECOVERY("Recuperación de contraseña"),
  PERSON_ACCESS_INVITATION("Invitación de acceso propio");

  private final String label;
}
