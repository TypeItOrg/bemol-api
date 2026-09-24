package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AuthorityResolver;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.PermissionAccess;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentDocumentAction;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentApplicationRepository;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EnrollmentDocumentAuthorization {
  private final AuthorityResolver authorityResolver;
  private final AuthorizationService authorizationService;
  private final EnrollmentDocumentAudit audit;
  private final EnrollmentApplicationRepository applications;

  public boolean canAccess(
      final EnrollmentApplication application,
      final @Nullable Authentication authentication,
      final PermissionCode permission) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }
    if (authentication.getPrincipal() instanceof JwtAuthenticatedPlatformAccount) {
      return authorizationService.hasPlatformRole(authentication, PlatformRoleCode.PLATFORM_ADMIN);
    }
    if (authentication.getPrincipal() instanceof JwtAuthenticatedUser user
        && user.institutionId() != null
        && application.getInstitution() != null
        && user.institutionId().equals(application.getInstitution().getId())) {
      if (user.personId() != null
          && Set.of(
                  PermissionCode.ENROLLMENT_ATTACHMENT_READ,
                  PermissionCode.ENROLLMENT_ATTACHMENT_UPLOAD,
                  PermissionCode.ENROLLMENT_ATTACHMENT_DELETE)
              .contains(permission)
          && (Objects.equals(user.personId(), application.getApplicantPerson().getId())
              || applications.isAccessibleByPerson(application.getId(), user.personId()))) {
        return true;
      }
      return authorityResolver
          .resolvePersonAuthorities(user.personId(), user.institutionId())
          .permissionScopes()
          .getOrDefault(permission, PermissionAccess.none())
          .includes(application.getTrainingPathId());
    }
    return false;
  }

  public void require(
      final EnrollmentApplication application,
      final @Nullable Authentication authentication,
      final PermissionCode permission,
      final EnrollmentDocumentAction action,
      final @Nullable UUID attachmentId) {
    if (canAccess(application, authentication, permission)) {
      return;
    }
    audit.recordIndependent(
        authentication,
        application.getInstitution().getId(),
        application.getId(),
        attachmentId,
        action,
        "DENIED");
    throw new AccessDeniedException(EnrollmentMessages.ATTACHMENT_ACCESS_DENIED);
  }
}
