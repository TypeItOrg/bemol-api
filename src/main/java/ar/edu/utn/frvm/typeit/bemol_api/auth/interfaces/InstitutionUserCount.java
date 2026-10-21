package ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces;

import java.util.UUID;

public interface InstitutionUserCount {
  UUID getInstitutionId();

  long getUserCount();
}
