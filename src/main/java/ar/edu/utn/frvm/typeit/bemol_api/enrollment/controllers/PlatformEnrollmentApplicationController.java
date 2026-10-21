package ar.edu.utn.frvm.typeit.bemol_api.enrollment.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentApplicationResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.RejectEnrollmentApplicationRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.ApproveEnrollmentApplicationUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.GetEnrollmentApplicationUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.ListEnrollmentApplicationsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.RejectEnrollmentApplicationUseCase;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/enrollment-applications")
@RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
@Validated
@RequiredArgsConstructor
public class PlatformEnrollmentApplicationController {

  private final ar.edu.utn.frvm.typeit.bemol_api.enrollment.services
          .RequestEnrollmentDocumentsUseCase
      documentRequests;
  private final ListEnrollmentApplicationsUseCase listEnrollmentApplicationsUseCase;
  private final GetEnrollmentApplicationUseCase getEnrollmentApplicationUseCase;
  private final ApproveEnrollmentApplicationUseCase approveEnrollmentApplicationUseCase;
  private final RejectEnrollmentApplicationUseCase rejectEnrollmentApplicationUseCase;

  @PostMapping(value = "/{institutionId}/{applicationId}/document-requests", version = Version.V1)
  @ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission(
      PermissionCode.ENROLLMENT_DOCUMENT_REQUEST_CREATE)
  public ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentDocumentRequestResponse
      requestDocuments(
          @PathVariable UUID institutionId,
          @PathVariable UUID applicationId,
          @Valid @RequestBody
              ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CreateEnrollmentDocumentRequest
                  input) {
    return documentRequests.execute(institutionId, applicationId, input);
  }

  @GetMapping(version = Version.V1)
  public PaginatedResponse<EnrollmentApplicationResponse> list(
      @RequestParam(required = false) final UUID institutionId,
      @RequestParam(required = false) final EnrollmentApplicationStatus status,
      @RequestParam(required = false) final UUID trainingPathId,
      @RequestParam(defaultValue = "false") final boolean open,
      @RequestParam(defaultValue = "false") final boolean pendingDocuments,
      @RequestParam(required = false) final Boolean pendingCourses,
      @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC)
          final Pageable pageable) {
    return PaginatedResponse.from(
        listEnrollmentApplicationsUseCase.executeForPlatform(
            institutionId,
            status,
            trainingPathId,
            open,
            pendingDocuments,
            pendingCourses,
            pageable));
  }

  @GetMapping(value = "/{institutionId}/{applicationId}", version = Version.V1)
  public EnrollmentApplicationResponse get(
      @PathVariable final UUID institutionId, @PathVariable final UUID applicationId) {
    return getEnrollmentApplicationUseCase.execute(institutionId, applicationId);
  }

  @PostMapping(
      value = "/{institutionId}/{applicationId}/approve-provisionally",
      version = Version.V1)
  public EnrollmentApplicationResponse approveProvisionally(
      @PathVariable UUID institutionId, @PathVariable UUID applicationId) {
    return approveEnrollmentApplicationUseCase.executeProvisionally(
        institutionId, applicationId, null);
  }

  @PostMapping(value = "/{institutionId}/{applicationId}/approve", version = Version.V1)
  public EnrollmentApplicationResponse approve(
      @PathVariable final UUID institutionId, @PathVariable final UUID applicationId) {
    return approveEnrollmentApplicationUseCase.execute(institutionId, applicationId, null);
  }

  @PostMapping(value = "/{institutionId}/{applicationId}/reject", version = Version.V1)
  public EnrollmentApplicationResponse reject(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID applicationId,
      @Valid @RequestBody final RejectEnrollmentApplicationRequest request) {
    return rejectEnrollmentApplicationUseCase.execute(institutionId, applicationId, request, null);
  }
}
