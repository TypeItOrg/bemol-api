package ar.edu.utn.frvm.typeit.boero_api.audit.interfaces;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface AuditEntityOptionProjection {
  String getEntityType();

  UUID getEntityId();

  String getLabel();

  @Nullable String getInstitutionName();
}
