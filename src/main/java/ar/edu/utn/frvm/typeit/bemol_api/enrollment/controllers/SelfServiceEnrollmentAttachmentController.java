package ar.edu.utn.frvm.typeit.bemol_api.enrollment.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.common.storage.PresignedDownloadResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.ConfirmEnrollmentAttachmentUploadRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentAttachmentResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentDocumentRequirementResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.PresignEnrollmentAttachmentUploadRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.PresignedAttachmentUploadResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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

/** Academic attachments scoped to the URL profile, with the real authenticated actor retained. */
@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping(
    "/institutions/{institutionId}/self-service/{selectedPersonId}/enrollment-applications/{applicationId}/attachments")
@RequiredArgsConstructor
@Tag(
    name = "Enrollment Attachments",
    description = "Gestión de adjuntos de solicitudes de inscripción")
public class SelfServiceEnrollmentAttachmentController {

  private final EnrollmentAttachmentService attachmentService;

  @PostMapping(value = "/upload-url", version = Version.V1)
  @Operation(summary = "Solicitar una URL prefirmada para subir un archivo adjunto")
  public PresignedAttachmentUploadResponse presignUpload(
      @PathVariable final UUID applicationId,
      @Valid @RequestBody final PresignEnrollmentAttachmentUploadRequest request,
      final Authentication authentication) {
    return attachmentService.presignUpload(applicationId, request, authentication);
  }

  @PostMapping(version = Version.V1)
  @Operation(summary = "Confirmar la subida de un archivo adjunto a la solicitud de inscripción")
  public ResponseEntity<EnrollmentAttachmentResponse> confirmUpload(
      @PathVariable final UUID applicationId,
      @Valid @RequestBody final ConfirmEnrollmentAttachmentUploadRequest request,
      final Authentication authentication) {
    final EnrollmentAttachmentResponse response =
        attachmentService.confirmUpload(applicationId, request, authentication);

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping(value = "/{attachmentId}/content", version = Version.V1)
  @Operation(summary = "Obtener una URL prefirmada para descargar o previsualizar un adjunto")
  public PresignedDownloadResponse getAttachmentContent(
      @PathVariable final UUID applicationId,
      @PathVariable final UUID attachmentId,
      final Authentication authentication) {
    return attachmentService.getAttachmentContent(applicationId, attachmentId, authentication);
  }

  @DeleteMapping(value = "/{attachmentId}", version = Version.V1)
  @Operation(summary = "Eliminar un archivo adjunto de la solicitud de inscripción")
  public ResponseEntity<Void> deleteAttachment(
      @PathVariable final UUID applicationId,
      @PathVariable final UUID attachmentId,
      final Authentication authentication) {
    attachmentService.deleteAttachment(applicationId, attachmentId, authentication);

    return ResponseEntity.noContent().build();
  }

  @GetMapping(version = Version.V1)
  @Operation(summary = "Listar los archivos adjuntos activos de la solicitud de inscripción")
  public ResponseEntity<List<EnrollmentAttachmentResponse>> listAttachments(
      @PathVariable final UUID applicationId, final Authentication authentication) {
    final List<EnrollmentAttachmentResponse> response =
        attachmentService.listAttachments(applicationId, authentication);

    return ResponseEntity.ok(response);
  }

  @GetMapping(value = "/requirements", version = Version.V1)
  public List<EnrollmentDocumentRequirementResponse> requirements(
      @PathVariable final UUID applicationId, final Authentication authentication) {
    return attachmentService.requirements(applicationId, authentication);
  }

  @GetMapping(value = "/history", version = Version.V1)
  public PaginatedResponse<EnrollmentAttachmentResponse> history(
      @PathVariable final UUID applicationId,
      @RequestParam final UUID requirementId,
      @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
          final Pageable pageable,
      final Authentication authentication) {
    return attachmentService.history(applicationId, requirementId, pageable, authentication);
  }
}
