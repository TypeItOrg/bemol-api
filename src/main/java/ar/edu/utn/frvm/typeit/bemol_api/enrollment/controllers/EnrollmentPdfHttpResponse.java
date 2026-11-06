package ar.edu.utn.frvm.typeit.bemol_api.enrollment.controllers;

import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

final class EnrollmentPdfHttpResponse {
  private EnrollmentPdfHttpResponse() {}

  static ResponseEntity<byte[]> download(final byte[] bytes, final String reference) {
    return response(
        bytes,
        ContentDisposition.inline()
            .filename("solicitud-" + reference + ".pdf", StandardCharsets.UTF_8)
            .build());
  }

  static ResponseEntity<byte[]> preview(final byte[] bytes) {
    return response(
        bytes,
        ContentDisposition.inline()
            .filename("ejemplo-planilla.pdf", StandardCharsets.UTF_8)
            .build());
  }

  private static ResponseEntity<byte[]> response(
      final byte[] bytes, final ContentDisposition disposition) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .header("X-Content-Type-Options", "nosniff")
        .body(bytes);
  }
}
