package ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian;

import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardianAttachment;
import java.time.Instant;
import java.util.UUID;

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
