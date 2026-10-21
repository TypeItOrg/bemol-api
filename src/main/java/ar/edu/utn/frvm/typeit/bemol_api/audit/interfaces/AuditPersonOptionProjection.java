package ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface AuditPersonOptionProjection {
  UUID getPersonId();

  String getLabel();

  @Nullable String getInstitutionName();
}
