package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ScopedAuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentDocumentAction;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentApplicationRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentApplicationResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListEnrollmentApplicationsUseCase {

  private final EnrollmentApplicationRepository enrollmentApplicationRepository;
  private final EnrollmentApplicationResponseFactory responseFactory;
  private final ScopedAuthorizationService authorization;

  private final EnrollmentDocumentAudit audit;

  private void requireDocumentFilter(boolean pending, @Nullable UUID institutionId) {
    var access = authorization.managementAccess(PermissionCode.ENROLLMENT_ATTACHMENT_READ);
    if (pending && !access.institutional() && access.trainingPathIds().isEmpty()) {
      audit.recordIndependent(
          SecurityContextHolder.getContext().getAuthentication(),
          institutionId,
          null,
          null,
          EnrollmentDocumentAction.METADATA_READ,
          "DENIED");
      throw new AccessDeniedException(EnrollmentMessages.ATTACHMENT_ACCESS_DENIED);
    }
  }

  @Transactional(readOnly = true)
  public Page<EnrollmentApplicationResponse> execute(
      final UUID institutionId,
      final @Nullable EnrollmentApplicationStatus status,
      final @Nullable UUID trainingPathId,
      final boolean open,
      final boolean pendingDocuments,
      final @Nullable Boolean pendingCourses,
      final Pageable pageable) {
    requireDocumentFilter(pendingDocuments, institutionId);
    return enrollmentApplicationRepository
        .findByInstitutionId(
            institutionId, status, trainingPathId, open, pendingDocuments, pendingCourses, pageable)
        .map(application -> responseFactory.from(application, false));
  }

  @Transactional(readOnly = true)
  public Page<EnrollmentApplicationResponse> executeForPlatform(
      final @Nullable UUID institutionId,
      final @Nullable EnrollmentApplicationStatus status,
      final @Nullable UUID trainingPathId,
      final boolean open,
      final boolean pendingDocuments,
      final @Nullable Boolean pendingCourses,
      final Pageable pageable) {
    requireDocumentFilter(pendingDocuments, institutionId);
    return enrollmentApplicationRepository
        .findByFilters(
            institutionId, status, trainingPathId, open, pendingDocuments, pendingCourses, pageable)
        .map(application -> responseFactory.from(application, false));
  }
}
