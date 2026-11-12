package ar.edu.utn.frvm.typeit.bemol_api.common.mail;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditFeed;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditModuleDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperation;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperationsProvider;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditResource;
import java.util.List;
import org.springframework.stereotype.Component;

public enum MailAuditAction implements AuditOperation {
  EMAIL_SENT("Correo enviado"),
  EMAIL_FAILED("Envío de correo fallido");

  public static final AuditResource AUDIT_TYPE = new AuditResource("EMAIL", "Correo");
  private static final AuditModuleDefinition MODULE =
      new AuditModuleDefinition("EMAILS", "Correos");

  private final String label;

  MailAuditAction(final String label) {
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

  @Override
  public AuditFeed feed() {
    return AuditFeed.EMAIL;
  }

  @Component
  public static class Provider implements AuditOperationsProvider {
    @Override
    public List<MailAuditAction> operations() {
      return List.of(values());
    }

    @Override
    public List<AuditResource> resources() {
      return List.of(MailAuditAction.AUDIT_TYPE);
    }
  }
}
