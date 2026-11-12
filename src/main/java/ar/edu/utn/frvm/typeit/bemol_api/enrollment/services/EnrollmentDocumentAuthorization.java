package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.CurrentPrincipal;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorityResolver;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCapabilities;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCapabilitiesService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.PermissionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentDocumentAction;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.OperatingContextResolver;
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
  private static final Set<PermissionCode> OWN_ATTACHMENT_PERMISSIONS =
      Set.of(
          PermissionCode.ENROLLMENT_ATTACHMENT_READ,
          PermissionCode.ENROLLMENT_ATTACHMENT_UPLOAD,
          PermissionCode.ENROLLMENT_ATTACHMENT_DELETE);

  private final AuthorityResolver authorityResolver;
  private final OperatingContextResolver operatingContext;
  private final AuthorizationService authorizationService;
  private final EnrollmentDocumentAudit audit;
  private final InstitutionalCapabilitiesService capabilities;

  public boolean canAccess(
      final EnrollmentApplication application,
      final @Nullable Authentication authentication,
      final PermissionCode permission) {
    return canAccess(application, authentication, permission, false);
  }

  private boolean canAccess(
      final EnrollmentApplication application,
      final @Nullable Authentication authentication,
      final PermissionCode permission,
      final boolean fresh) {
    final var selected = operatingContext.currentPersonId();
    if (selected.isPresent()) {
      return selected.get().equals(application.getApplicantPerson().getId())
          && canAccessOwn(application, selected.get(), permission, fresh);
    }
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }
    if (CurrentPrincipal.platformAccount(authentication).isPresent()) {
      return authorizationService.hasPlatformRole(authentication, PlatformRoleCode.PLATFORM_ADMIN);
    }
    return CurrentPrincipal.institutionalUser(authentication)
        .filter(user -> belongsTo(user, application))
        .map(user -> canAccessAsMember(application, user, permission, fresh))
        .orElse(false);
  }

  private static boolean belongsTo(
      final JwtAuthenticatedUser user, final EnrollmentApplication application) {
    final var institution = application.getInstitution();
    if (user.institutionId() == null || institution == null) {
      return false;
    }
    return user.institutionId().equals(institution.getId());
  }

  private boolean canAccessAsMember(
      final EnrollmentApplication application,
      final JwtAuthenticatedUser user,
      final PermissionCode permission,
      final boolean fresh) {
    if (canAccessOwnAttachment(application, user, permission, fresh)) {
      return true;
    }
    return authorityResolver
        .resolvePersonAuthorities(user.personId(), user.institutionId())
        .permissionScopes()
        .getOrDefault(permission, PermissionAccess.none())
        .includes(application.getTrainingPathId());
  }

  private boolean canAccessOwnAttachment(
      final EnrollmentApplication application,
      final JwtAuthenticatedUser user,
      final PermissionCode permission,
      final boolean fresh) {
    final UUID personId = user.personId();
    final boolean ownApplication =
        personId != null && Objects.equals(personId, application.getApplicantPerson().getId());
    final boolean attachmentPermission = OWN_ATTACHMENT_PERMISSIONS.contains(permission);

    return ownApplication
        && attachmentPermission
        && canAccessOwn(application, personId, permission, fresh);
  }

  private boolean canAccessOwn(
      final EnrollmentApplication application,
      final UUID personId,
      final PermissionCode permission,
      final boolean fresh) {
    final var institutionId = application.getInstitution().getId();
    final var access =
        fresh
            ? capabilities.resolve(institutionId, personId)
            : InstitutionalCapabilities.from(
                authorityResolver.resolvePersonAuthorities(personId, institutionId));
    return switch (permission) {
      case ENROLLMENT_ATTACHMENT_READ -> access.canViewOwnEnrollmentApplications();
      case ENROLLMENT_ATTACHMENT_UPLOAD, ENROLLMENT_ATTACHMENT_DELETE ->
          access.canManageOwnEnrollmentApplications();
      default -> false;
    };
  }

  public void require(
      final EnrollmentApplication application,
      final @Nullable Authentication authentication,
      final PermissionCode permission,
      final EnrollmentDocumentAction action,
      final @Nullable UUID attachmentId) {
    if (canAccess(application, authentication, permission, true)) {
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
