package ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface AuditSubjectOptionProjection {
  UUID getSubjectPersonId();

  String getLabel();

  @Nullable String getInstitutionName();
}
