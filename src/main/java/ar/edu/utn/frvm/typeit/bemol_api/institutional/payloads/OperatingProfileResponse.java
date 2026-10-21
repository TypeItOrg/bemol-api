package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads;

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
    List<String> roles) {}
