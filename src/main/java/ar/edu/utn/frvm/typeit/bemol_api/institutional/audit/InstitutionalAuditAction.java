package ar.edu.utn.frvm.typeit.bemol_api.institutional.audit;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditModuleDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperation;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperationsProvider;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditResource;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardianAttachment;
import java.util.List;
import org.springframework.stereotype.Component;

public enum InstitutionalAuditAction implements AuditOperation {
  PERSON_AUTONOMY_GRANTED(Modules.USERS, "Autonomía personal otorgada"),
  PERSON_CREATED(Modules.USERS, "Persona creada"),
  PERSON_OWN_ACCESS_INVITED(Modules.USERS, "Acceso propio invitado"),
  PERSON_OWN_ACCESS_INVITATION_CANCELLED(Modules.USERS, "Invitación de acceso cancelada"),
  PERSON_OWN_ACCESS_CHANGED(Modules.USERS, "Acceso propio modificado"),
  PERSON_OWN_ACCESS_ACTIVATED(Modules.USERS, "Acceso propio activado"),
  GUARDIAN_DEPENDENT_LINKED(Modules.GUARDIAN_LINKS, "Vinculación creada (histórico)"),
  GUARDIAN_LINK_REQUESTED(Modules.GUARDIAN_LINKS, "Vinculación solicitada"),
  GUARDIAN_LINK_APPROVED(Modules.GUARDIAN_LINKS, "Vinculación aprobada"),
  GUARDIAN_LINK_REJECTED(Modules.GUARDIAN_LINKS, "Vinculación rechazada"),
  GUARDIAN_DEPENDENT_UNLINKED(Modules.GUARDIAN_LINKS, "Vinculación eliminada"),
  INSTITUTION_CREATED(Modules.INSTITUTIONS, "Institución creada"),
  INSTITUTION_UPDATED(Modules.INSTITUTIONS, "Institución actualizada"),
  INSTITUTION_STATUS_CHANGED(Modules.INSTITUTIONS, "Estado de institución modificado"),
  INSTITUTION_PUBLIC_ACCESS_CHANGED(
      Modules.INSTITUTIONS, "Acceso público de institución modificado"),
  INSTITUTION_LOGO_CHANGED(Modules.INSTITUTIONS, "Logo de institución modificado"),
  PERSON_UPDATED(Modules.USERS, "Persona actualizada"),
  PERSON_DELETED(Modules.USERS, "Persona eliminada"),
  GUARDIAN_ATTACHMENT_UPLOADED(Modules.GUARDIAN_LINKS, "Adjunto de vinculación cargado"),
  GUARDIAN_ATTACHMENT_DELETED(Modules.GUARDIAN_LINKS, "Adjunto de vinculación eliminado");

  private final AuditModuleDefinition module;
  private final String label;

  InstitutionalAuditAction(final AuditModuleDefinition module, final String label) {
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

  private static final class Modules {
    static final AuditModuleDefinition USERS = new AuditModuleDefinition("USERS", "Usuarios");
    static final AuditModuleDefinition GUARDIAN_LINKS =
        new AuditModuleDefinition("GUARDIAN_LINKS", "Vinculaciones");
    static final AuditModuleDefinition INSTITUTIONS =
        new AuditModuleDefinition("INSTITUTIONS", "Instituciones");
  }

  @Component
  public static class Provider implements AuditOperationsProvider {
    @Override
    public List<InstitutionalAuditAction> operations() {
      return List.of(values());
    }

    @Override
    public List<AuditResource> resources() {
      return List.of(
          PersonGuardian.AUDIT_TYPE,
          PersonGuardianAttachment.AUDIT_TYPE,
          Person.AUDIT_TYPE,
          Institution.AUDIT_TYPE);
    }
  }
}
