package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentRequirementChange;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(requiredProperties = {"action", "occurredAt", "name"})
public record EnrollmentRequirementChangeResponse(String action, Instant occurredAt, String name) {
  public static EnrollmentRequirementChangeResponse from(final EnrollmentRequirementChange value) {
    return new EnrollmentRequirementChangeResponse(
        value.getAction(), value.getOccurredAt(), value.getName());
  }
}
