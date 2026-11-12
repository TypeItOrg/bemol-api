package ar.edu.utn.frvm.typeit.bemol_api.audit.enums;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditActorContext;

public enum AuditOrigin {
  SELF_REGISTRATION,
  EMAIL_VERIFICATION,
  INSTITUTIONAL_ADMINISTRATION,
  PLATFORM_ADMINISTRATION,
  SELF_SERVICE,
  MAIL_DELIVERY;

  public static AuditOrigin administration() {
    if (AuditActorContext.current().isPlatformAccount()) {
      return PLATFORM_ADMINISTRATION;
    }

    return INSTITUTIONAL_ADMINISTRATION;
  }
}
