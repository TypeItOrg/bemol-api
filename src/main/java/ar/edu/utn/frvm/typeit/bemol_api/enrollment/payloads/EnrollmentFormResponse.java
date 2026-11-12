package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentForm;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(requiredProperties = {"id", "institutionId", "name", "revision"})
public record EnrollmentFormResponse(UUID id, UUID institutionId, String name, long revision) {
  public static EnrollmentFormResponse from(final EnrollmentForm value) {
    return new EnrollmentFormResponse(
        value.getId(), value.getInstitution().getId(), value.getName(), value.getRevision());
  }
}
