package ar.edu.utn.frvm.typeit.bemol_api.auth.audit;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditModuleDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperation;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperationsProvider;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditResource;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PasskeyCredential;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PersonAccessInvitation;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

public enum AuthAuditAction implements AuditOperation {
  ACCOUNT_EMAIL_CHANGE_REQUESTED(Modules.USERS, "Cambio de correo solicitado"),
  INSTITUTIONAL_ACCOUNT_CREATED(Modules.USERS, "Cuenta institucional creada"),
  PLATFORM_ACCOUNT_CREATED(Modules.USERS, "Administrador de plataforma creado"),
  INSTITUTIONAL_EMAIL_VERIFIED(Modules.USERS, "Correo institucional verificado"),
  INSTITUTIONAL_ACCESS_CHANGED(Modules.USERS, "Acceso institucional modificado"),
  PLATFORM_ACCESS_CHANGED(Modules.USERS, "Acceso de plataforma modificado"),
  PLATFORM_ACCOUNT_UPDATED(Modules.USERS, "Administrador de plataforma actualizado"),
  PASSWORD_CHANGED(Modules.SECURITY, "Contraseña modificada"),
  INSTITUTIONAL_EMAIL_CHANGED(Modules.USERS, "Correo institucional modificado"),
  PASSKEY_CREATED(Modules.SECURITY, "Llave de acceso registrada"),
  PASSKEY_UPDATED(Modules.SECURITY, "Llave de acceso renombrada"),
  PASSKEY_DELETED(Modules.SECURITY, "Llave de acceso revocada");

  private static final Set<AuthAuditAction> PLATFORM_ONLY =
      EnumSet.of(PLATFORM_ACCOUNT_CREATED, PLATFORM_ACCOUNT_UPDATED, PLATFORM_ACCESS_CHANGED);

  private final AuditModuleDefinition module;
  private final String label;

  AuthAuditAction(final AuditModuleDefinition module, final String label) {
    this.module = module;
    this.label = label;
  }

  @Override
  public AuditModuleDefinition getModule() {
    return module;
  }

  @Override
  public String getLabel() {
    return label;
  }

  @Override
  public boolean platformOnly() {
    return PLATFORM_ONLY.contains(this);
  }

  private static final class Modules {
    static final AuditModuleDefinition USERS = new AuditModuleDefinition("USERS", "Usuarios");
    static final AuditModuleDefinition SECURITY =
        new AuditModuleDefinition("SECURITY", "Seguridad");
  }

  @Component
  public static class Provider implements AuditOperationsProvider {
    @Override
    public List<AuthAuditAction> operations() {
      return List.of(values());
    }

    @Override
    public List<AuditResource> resources() {
      return List.of(
          User.AUDIT_TYPE,
          PersonAccessInvitation.AUDIT_TYPE,
          PasskeyCredential.AUDIT_TYPE,
          PlatformAccount.AUDIT_TYPE);
    }
  }
}
