package ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces;

import java.util.UUID;

public interface AuditActorOptionProjection {
  String getActorKind();

  UUID getActorId();

  String getLabel();
}
