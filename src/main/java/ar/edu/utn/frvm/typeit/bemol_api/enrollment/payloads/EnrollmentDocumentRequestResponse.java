package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentDocumentRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(requiredProperties = {"id", "reason", "createdAt", "actorId", "accountType", "actorName"})
public record EnrollmentDocumentRequestResponse(
    UUID id, String reason, Instant createdAt, UUID actorId, String accountType, String actorName) {
  public static EnrollmentDocumentRequestResponse from(final EnrollmentDocumentRequest value) {
    return new EnrollmentDocumentRequestResponse(
        value.getId(),
        value.getReason(),
        value.getCreatedAt(),
        value.getActorId(),
        value.getAccountType(),
        value.getActorName());
  }
}
