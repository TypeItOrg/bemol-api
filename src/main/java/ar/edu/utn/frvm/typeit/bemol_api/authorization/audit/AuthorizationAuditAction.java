package ar.edu.utn.frvm.typeit.bemol_api.authorization.audit;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditModuleDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperation;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperationsProvider;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import java.util.List;
import org.springframework.stereotype.Component;

public enum AuthorizationAuditAction implements AuditOperation {
  PERSON_ROLES_CHANGED("Roles de persona modificados"),
  ROLE_CREATED("Rol creado"),
  ROLE_UPDATED("Rol actualizado"),
  ROLE_DELETED("Rol eliminado");

  private static final AuditModuleDefinition MODULE = new AuditModuleDefinition("ROLES", "Roles");

  private final String label;

  AuthorizationAuditAction(final String label) {
    this.label = label;
  }

  @Override
  public AuditModuleDefinition getModule() {
    return MODULE;
  }

  @Override
  public String getLabel() {
    return label;
  }

  @Component
  public static class Provider implements AuditOperationsProvider {
    @Override
    public List<AuthorizationAuditAction> operations() {
      return List.of(values());
    }

    @Override
    public List<AuditResource> resources() {
      return List.of(Role.AUDIT_TYPE);
    }
  }
}
