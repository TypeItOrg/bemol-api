package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardianAttachment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(requiredProperties = {"id", "originalFileName", "contentType", "size", "createdAt"})
public record GuardianLinkAttachmentResponse(
    UUID id, String originalFileName, String contentType, long size, Instant createdAt) {

  public static GuardianLinkAttachmentResponse from(final PersonGuardianAttachment attachment) {
    return new GuardianLinkAttachmentResponse(
        attachment.getId(),
        attachment.getOriginalFileName(),
        attachment.getContentType(),
        attachment.getFileSize(),
        attachment.getCreatedAt());
  }
}
