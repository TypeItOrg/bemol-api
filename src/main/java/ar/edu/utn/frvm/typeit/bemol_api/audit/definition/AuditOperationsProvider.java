package ar.edu.utn.frvm.typeit.bemol_api.audit.definition;

import java.util.List;

public interface AuditOperationsProvider {
  List<? extends AuditOperation> operations();

  default List<AuditResource> resources() {
    return List.of();
  }
}
