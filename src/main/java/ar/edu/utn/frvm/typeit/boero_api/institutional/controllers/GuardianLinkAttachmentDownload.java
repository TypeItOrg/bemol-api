package ar.edu.utn.frvm.typeit.boero_api.institutional.controllers;

import ar.edu.utn.frvm.typeit.boero_api.institutional.services.GuardianLinkAttachmentService.Content;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Builds the inline response shared by the tutor's and the reviewer's document downloads. */
final class GuardianLinkAttachmentDownload {

  private GuardianLinkAttachmentDownload() {}

  static ResponseEntity<Resource> inline(final Content content) {
    final var attachment = content.attachment();

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(attachment.getContentType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline()
                .filename(attachment.getOriginalFileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(attachment.getFileSize()))
        .body(content.resource());
  }
}
