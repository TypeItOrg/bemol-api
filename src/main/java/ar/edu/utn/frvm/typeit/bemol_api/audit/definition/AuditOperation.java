package ar.edu.utn.frvm.typeit.bemol_api.audit.definition;

public interface AuditOperation {
  String name();

  String getLabel();

  AuditModuleDefinition getModule();

  default AuditFeed feed() {
    return AuditFeed.ACTIVITY;
  }

  default boolean platformOnly() {
    return false;
  }
}
