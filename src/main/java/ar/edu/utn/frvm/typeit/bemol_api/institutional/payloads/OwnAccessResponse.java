package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PersonAccessInvitation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(requiredProperties = {"status", "invitation"})
public record OwnAccessResponse(
    String status, @Nullable @Schema(nullable = true) Invitation invitation) {
  public OwnAccessResponse(final String status) {
    this(status, null);
  }

  @Schema(
      requiredProperties = {
        "id",
        "status",
        "purpose",
        "issuerKind",
        "issuerId",
        "issuerName",
        "issuedAt",
        "expiresAt",
        "usedAt",
        "cancelledAt",
        "mailOutcome"
      })
  public record Invitation(
      UUID id,
      String status,
      String purpose,
      String issuerKind,
      UUID issuerId,
      String issuerName,
      Instant issuedAt,
      Instant expiresAt,
      @Nullable @Schema(nullable = true) Instant usedAt,
      @Nullable @Schema(nullable = true) Instant cancelledAt,
      @Nullable @Schema(nullable = true) String mailOutcome) {
    public static Invitation from(final PersonAccessInvitation value, final Instant now) {
      final String state =
          value.getConsumedAt() != null
              ? "USED"
              : value.getCancelledAt() != null
                  ? "CANCELLED"
                  : !value.getExpiresAt().isAfter(now) ? "EXPIRED" : "PENDING";
      final var platform = value.getPlatformIssuer();
      final var issuer = value.getInstitutionalIssuer();
      final String issuerName;
      if (platform != null) {
        issuerName = platform.getName() + " " + platform.getLastName();
      } else {
        final var person =
            issuer != null
                ? issuer
                : java.util.Objects.requireNonNull(value.getRepresentation()).getTutorPerson();
        issuerName = person.getFirstName() + " " + person.getLastName();
      }
      return new Invitation(
          value.getId(),
          state,
          value.getPurpose().name(),
          platform != null ? "PLATFORM" : issuer != null ? "INSTITUTION" : "GUARDIAN",
          platform != null
              ? platform.getId()
              : issuer != null
                  ? issuer.getId()
                  : java.util.Objects.requireNonNull(value.getRepresentation())
                      .getTutorPerson()
                      .getId(),
          issuerName,
          value.getIssuedAt(),
          value.getExpiresAt(),
          value.getConsumedAt(),
          value.getCancelledAt(),
          value.getMailOutcome());
    }
  }
}
