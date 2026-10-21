package ar.edu.utn.frvm.typeit.bemol_api.enrollment.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.DocumentReviewRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentAttachmentResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentDocumentRequirementResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentAttachmentService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentAttachmentService.AttachmentContentResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Access is resolved entirely from the authenticated principal ({@link
 * EnrollmentAttachmentService#uploadAttachment}) — there is no client-supplied identity header to
 * spoof.
 */
@RestController
@PreAuthorize(
    "@authorizationService.hasPermission(authentication, T(ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode).ENROLLMENT_APPLICATION_READ)")
@RequestMapping("/enrollment-applications/{applicationId}/attachments")
@RequiredArgsConstructor
@Tag(
    name = "Enrollment Attachments",
    description = "Gestión de adjuntos de solicitudes de inscripción")
public class EnrollmentAttachmentController {

  private final EnrollmentAttachmentService attachmentService;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, version = Version.V1)
  @Operation(summary = "Subir un archivo adjunto a la solicitud de inscripción")
  public ResponseEntity<EnrollmentAttachmentResponse> uploadAttachment(
      @PathVariable UUID applicationId,
      @RequestParam("file") MultipartFile file,
      @RequestParam("requirementId") UUID requirementId,
      Authentication authentication) {

    EnrollmentAttachmentResponse response =
        attachmentService.uploadAttachment(applicationId, file, requirementId, authentication);

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping(value = "/{attachmentId}/content", version = Version.V1)
  @Operation(summary = "Descargar o previsualizar el contenido de un archivo adjunto")
  public ResponseEntity<Resource> getAttachmentContent(
      @PathVariable UUID applicationId,
      @PathVariable UUID attachmentId,
      Authentication authentication) {

    AttachmentContentResult result =
        attachmentService.getAttachmentContent(applicationId, attachmentId, authentication);

    MediaType mediaType;

    try {
      mediaType = MediaType.parseMediaType(result.attachment().getContentType());
    } catch (Exception e) {
      mediaType = MediaType.APPLICATION_OCTET_STREAM;
    }

    String contentDisposition =
        ContentDisposition.inline()
            .filename(result.attachment().getOriginalFileName(), StandardCharsets.UTF_8)
            .build()
            .toString();

    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .header("X-Content-Type-Options", "nosniff")
        .contentType(mediaType)
        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(result.attachment().getFileSize()))
        .body(result.resource());
  }

  @DeleteMapping(value = "/{attachmentId}", version = Version.V1)
  @Operation(summary = "Eliminar un archivo adjunto de la solicitud de inscripción")
  public ResponseEntity<Void> deleteAttachment(
      @PathVariable UUID applicationId,
      @PathVariable UUID attachmentId,
      Authentication authentication) {

    attachmentService.deleteAttachment(applicationId, attachmentId, authentication);

    return ResponseEntity.noContent().build();
  }

  @GetMapping(version = Version.V1)
  @Operation(summary = "Listar los archivos adjuntos activos de la solicitud de inscripción")
  public ResponseEntity<List<EnrollmentAttachmentResponse>> listAttachments(
      @PathVariable UUID applicationId, Authentication authentication) {

    List<EnrollmentAttachmentResponse> response =
        attachmentService.listAttachments(applicationId, authentication);

    return ResponseEntity.ok(response);
  }

  @PostMapping(value = "/{attachmentId}/review", version = Version.V1)
  public EnrollmentAttachmentResponse review(
      @PathVariable UUID applicationId,
      @PathVariable UUID attachmentId,
      @Valid @RequestBody DocumentReviewRequest request,
      Authentication authentication) {
    return attachmentService.review(applicationId, attachmentId, request, authentication);
  }

  @GetMapping(value = "/requirements", version = Version.V1)
  public List<EnrollmentDocumentRequirementResponse> requirements(
      @PathVariable UUID applicationId, Authentication authentication) {
    return attachmentService.requirements(applicationId, authentication);
  }

  @GetMapping(value = "/history", version = Version.V1)
  public PaginatedResponse<EnrollmentAttachmentResponse> history(
      @PathVariable UUID applicationId,
      @RequestParam UUID requirementId,
      @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable,
      Authentication authentication) {
    return attachmentService.history(applicationId, requirementId, pageable, authentication);
  }
}
