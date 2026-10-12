package ar.edu.utn.frvm.typeit.boero_api.enrollment.controllers;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollApplicationCourseRequest;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollmentApplicationCourseResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollmentApplicationResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.RejectEnrollmentApplicationCourseRequest;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.RejectEnrollmentApplicationRequest;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.ApproveEnrollmentApplicationUseCase;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.CourseEnrollmentService;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.GetEnrollmentApplicationUseCase;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.ListEnrollmentApplicationsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.RejectEnrollmentApplicationCourseUseCase;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.RejectEnrollmentApplicationUseCase;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/institutions/{institutionId}/enrollment-applications")
@RequiresInstitutionAccess
@Validated
@RequiredArgsConstructor
public class InstitutionalEnrollmentApplicationController {

  private final ar.edu.utn.frvm.typeit.boero_api.enrollment.services
          .RequestEnrollmentDocumentsUseCase
      documentRequests;
  private final ListEnrollmentApplicationsUseCase listEnrollmentApplicationsUseCase;
  private final GetEnrollmentApplicationUseCase getEnrollmentApplicationUseCase;
  private final ApproveEnrollmentApplicationUseCase approveEnrollmentApplicationUseCase;
  private final RejectEnrollmentApplicationUseCase rejectEnrollmentApplicationUseCase;

  @Autowired(required = false)
  private CourseEnrollmentService courseEnrollmentService;

  @Autowired(required = false)
  private RejectEnrollmentApplicationCourseUseCase rejectEnrollmentApplicationCourseUseCase;

  @PostMapping(value = "/{applicationId}/document-requests", version = Version.V1)
  @ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresPermission(
      PermissionCode.ENROLLMENT_DOCUMENT_REQUEST_CREATE)
  public ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollmentDocumentRequestResponse
      requestDocuments(
          @PathVariable UUID institutionId,
          @PathVariable UUID applicationId,
          @Valid @RequestBody
              ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CreateEnrollmentDocumentRequest
                  input) {
    return documentRequests.execute(institutionId, applicationId, input);
  }

  @GetMapping(version = Version.V1)
  @RequiresPermission(PermissionCode.ENROLLMENT_APPLICATION_READ)
  public PaginatedResponse<EnrollmentApplicationResponse> list(
      @PathVariable final UUID institutionId,
      @RequestParam(required = false) final EnrollmentApplicationStatus status,
      @RequestParam(required = false) final UUID trainingPathId,
      @RequestParam(defaultValue = "false") final boolean open,
      @RequestParam(defaultValue = "false") final boolean pendingDocuments,
      @RequestParam(required = false) final Boolean pendingCourses,
      @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC)
          final Pageable pageable) {
    return PaginatedResponse.from(
        listEnrollmentApplicationsUseCase.execute(
            institutionId,
            status,
            trainingPathId,
            open,
            pendingDocuments,
            pendingCourses,
            pageable));
  }

  @GetMapping(value = "/{applicationId}", version = Version.V1)
  @RequiresPermission(PermissionCode.ENROLLMENT_APPLICATION_READ)
  public EnrollmentApplicationResponse get(
      @PathVariable final UUID institutionId, @PathVariable final UUID applicationId) {
    return getEnrollmentApplicationUseCase.execute(institutionId, applicationId);
  }

  @PostMapping(value = "/{applicationId}/approve-provisionally", version = Version.V1)
  @RequiresPermission(PermissionCode.ENROLLMENT_APPLICATION_APPROVE)
  public EnrollmentApplicationResponse approveProvisionally(
      @PathVariable UUID institutionId,
      @PathVariable UUID applicationId,
      Authentication authentication) {
    return approveEnrollmentApplicationUseCase.executeProvisionally(
        institutionId, applicationId, currentPersonId(authentication));
  }

  @PostMapping(value = "/{applicationId}/approve", version = Version.V1)
  @RequiresPermission(PermissionCode.ENROLLMENT_APPLICATION_APPROVE)
  public EnrollmentApplicationResponse approve(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID applicationId,
      final Authentication authentication) {
    return approveEnrollmentApplicationUseCase.execute(
        institutionId, applicationId, currentPersonId(authentication));
  }

  @PostMapping(value = "/{applicationId}/reject", version = Version.V1)
  @RequiresPermission(PermissionCode.ENROLLMENT_APPLICATION_REJECT)
  public EnrollmentApplicationResponse reject(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID applicationId,
      @Valid @RequestBody final RejectEnrollmentApplicationRequest request,
      final Authentication authentication) {
    return rejectEnrollmentApplicationUseCase.execute(
        institutionId, applicationId, request, currentPersonId(authentication));
  }

  @PostMapping(
      value = "/{applicationId}/courses/{applicationCourseId}/enroll",
      version = Version.V1)
  @RequiresPermission(PermissionCode.ENROLLMENT_APPLICATION_COURSE_ENROLL)
  public CourseEnrollmentResponse enrollCourse(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID applicationId,
      @PathVariable final UUID applicationCourseId,
      @Valid @RequestBody final EnrollApplicationCourseRequest request,
      final Authentication authentication) {
    return courseEnrollmentService.enrollApplicationCourse(
        institutionId,
        applicationId,
        applicationCourseId,
        request,
        currentPersonId(authentication));
  }

  @PostMapping(
      value = "/{applicationId}/courses/{applicationCourseId}/reject",
      version = Version.V1)
  @RequiresPermission(PermissionCode.ENROLLMENT_APPLICATION_COURSE_REJECT)
  public EnrollmentApplicationCourseResponse rejectCourse(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID applicationId,
      @PathVariable final UUID applicationCourseId,
      @Valid @RequestBody final RejectEnrollmentApplicationCourseRequest request,
      final Authentication authentication) {
    return rejectEnrollmentApplicationCourseUseCase.execute(
        institutionId,
        applicationId,
        applicationCourseId,
        request,
        currentPersonId(authentication));
  }

  private @Nullable UUID currentPersonId(final Authentication authentication) {
    if (authentication.getPrincipal() instanceof JwtAuthenticatedPlatformAccount) {
      return null;
    }

    return ((JwtAuthenticatedUser) requireNonNull(authentication.getPrincipal())).personId();
  }
}
