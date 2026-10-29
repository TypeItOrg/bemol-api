package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCapabilities;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(
    requiredProperties = {
      "actorPersonId",
      "personId",
      "firstName",
      "lastName",
      "documentNumber",
      "self",
      "canEnroll",
      "capabilities",
      "roles"
    })
public record OperatingProfileResponse(
    UUID actorPersonId,
    UUID personId,
    String firstName,
    String lastName,
    String documentNumber,
    boolean self,
    boolean canEnroll,
    InstitutionalCapabilities capabilities,
    List<String> roles) {}
