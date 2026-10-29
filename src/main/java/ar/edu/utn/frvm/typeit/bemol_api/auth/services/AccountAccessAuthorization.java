package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtPrincipal;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.PermissionDelegationNotAllowedException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PlatformAccountRoleRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.CachedAuthoritySnapshotResolver;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.PermissionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonGuardianRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountAccessAuthorization {
  private final UserRepository users;
  private final PlatformAccountRepository platforms;
  private final PlatformAccountRoleRepository platformRoles;
  private final PersonRoleAssignmentRepository roles;
  private final PersonGuardianRepository links;
  private final CachedAuthoritySnapshotResolver authorities;
  private final PasswordEncoder passwords;

  public JwtPrincipal currentActor() {
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal actor)) {
      throw new InvalidCredentialsException();
    }
    return actor;
  }

  public boolean isPlatformAdministrator(final UUID id) {
    return platforms.findById(id).filter(account -> account.isEnabled()).isPresent()
        && platformRoles.findAuthoritiesByPlatformAccountId(id).stream()
            .anyMatch(row -> PlatformRoleCode.PLATFORM_ADMIN.name().equals(row.getRoleCode()));
  }

  public void requirePlatformAdministration() {
    if (!(currentActor() instanceof JwtAuthenticatedPlatformAccount platform)
        || !isPlatformAdministrator(platform.platformAccountId())) {
      throw new PermissionDelegationNotAllowedException();
    }
  }

  public void requirePermission(
      final UUID institutionId, final UUID actorId, final PermissionCode permission) {
    if (users
            .findByPerson_IdAndInstitution_Id(actorId, institutionId)
            .filter(user -> user.isEnabled())
            .isEmpty()
        || !authorities
            .resolveFreshForPerson(actorId, institutionId)
            .permissionScopes()
            .getOrDefault(permission, PermissionAccess.none())
            .institutional()) {
      throw new PermissionDelegationNotAllowedException();
    }
  }

  public boolean canProvisionInstitutionally(final Person issuer, final Person subject) {
    final UUID institutionId = subject.getInstitution().getId();
    if (!issuer.getInstitution().getId().equals(institutionId)
        || issuer.isDeleted()
        || users
            .findByPerson_IdAndInstitution_Id(issuer.getId(), institutionId)
            .filter(user -> user.isEnabled())
            .isEmpty()) {
      return false;
    }
    final var actor = authorities.resolveFreshForPerson(issuer.getId(), institutionId);
    for (final var permission :
        List.of(
            PermissionCode.INSTITUTION_PERSON_CREATE,
            PermissionCode.INSTITUTION_USER_STATUS_UPDATE,
            PermissionCode.INSTITUTION_ROLE_ASSIGN)) {
      if (!actor
          .permissionScopes()
          .getOrDefault(permission, PermissionAccess.none())
          .institutional()) {
        return false;
      }
    }
    return authorities
        .resolveFreshForPerson(subject.getId(), institutionId)
        .permissionScopes()
        .entrySet()
        .stream()
        .allMatch(
            entry ->
                actor
                    .permissionScopes()
                    .getOrDefault(entry.getKey(), PermissionAccess.none())
                    .contains(entry.getValue()));
  }

  public boolean isStaff(final Person person) {
    return !roles
        .findPersonIdsRequiringInstitutionalAccess(
            List.of(person.getId()),
            person.getInstitution().getId(),
            Set.of("INSTITUTIONAL_AUTHORITY", "ADMINISTRATIVE", "TEACHER"),
            Set.of(
                PermissionCode.ACADEMIC_OFFER_READ.getCode(),
                PermissionCode.STUDY_PLAN_READ.getCode(),
                PermissionCode.ACADEMIC_YEAR_READ.getCode(),
                PermissionCode.GUARDIAN_LINK_REQUEST.getCode(),
                PermissionCode.OWN_ENROLLMENT_APPLICATION_READ.getCode(),
                PermissionCode.OWN_ENROLLMENT_APPLICATION_MANAGE.getCode(),
                PermissionCode.OWN_FORMATION_READ.getCode()))
        .isEmpty();
  }

  public boolean canManage(final JwtPrincipal actor, final Person subject) {
    if (actor instanceof JwtAuthenticatedPlatformAccount platform) {
      return isPlatformAdministrator(platform.platformAccountId());
    }
    return actor instanceof JwtAuthenticatedUser user
        && user.institutionId().equals(subject.getInstitution().getId())
        && canManageInstitutionally(user.actorPersonId(), subject);
  }

  public boolean canManageInstitutionally(final UUID actorId, final Person subject) {
    if (subject.isDeleted()) {
      return false;
    }
    final var user =
        users
            .findByPerson_IdAndInstitution_Id(actorId, subject.getInstitution().getId())
            .filter(value -> value.isEnabled());
    if (user.isEmpty()) {
      return false;
    }
    if (actorId.equals(subject.getId())
        || canProvisionInstitutionally(user.get().getPerson(), subject)) {
      return true;
    }
    return !isStaff(subject)
        && links.existsByInstitution_IdAndTutorPerson_IdAndDependentPerson_IdAndStatusIn(
            subject.getInstitution().getId(),
            actorId,
            subject.getId(),
            List.of(GuardianLinkStatus.ACTIVE));
  }

  public void requireManagement(final Person subject) {
    if (!canManage(currentActor(), subject)) {
      throw new PermissionDelegationNotAllowedException();
    }
  }

  public void requireAdministration(final Person subject) {
    final var actor = currentActor();
    if (actor instanceof JwtAuthenticatedPlatformAccount platform
        && isPlatformAdministrator(platform.platformAccountId())) {
      return;
    }
    if (actor instanceof JwtAuthenticatedUser user
        && user.institutionId().equals(subject.getInstitution().getId())) {
      final var issuer =
          users
              .findById(user.actorUserId())
              .orElseThrow(InvalidCredentialsException::new)
              .getPerson();
      if (canProvisionInstitutionally(issuer, subject)) {
        return;
      }
    }
    throw new PermissionDelegationNotAllowedException();
  }

  public void requireIdentityCorrection(final UUID institutionId) {
    final var actor = currentActor();
    if (actor instanceof JwtAuthenticatedPlatformAccount platform
        && isPlatformAdministrator(platform.platformAccountId())) {
      return;
    }
    if (actor instanceof JwtAuthenticatedUser user && user.institutionId().equals(institutionId)) {
      requirePermission(
          institutionId, user.actorPersonId(), PermissionCode.INSTITUTION_PERSON_UPDATE_ANY);
      return;
    }
    throw new PermissionDelegationNotAllowedException();
  }

  public void reauthenticate(final String password) {
    final var actor = currentActor();
    final String hash;
    if (actor instanceof JwtAuthenticatedUser user) {
      hash =
          users
              .findById(user.actorUserId())
              .filter(value -> value.isEnabled())
              .orElseThrow(InvalidCredentialsException::new)
              .getPassword();
    } else if (actor instanceof JwtAuthenticatedPlatformAccount platform) {
      hash =
          platforms
              .findById(platform.platformAccountId())
              .filter(value -> value.isEnabled())
              .orElseThrow(InvalidCredentialsException::new)
              .getPassword();
    } else {
      throw new InvalidCredentialsException();
    }
    if (!passwords.matches(password, hash)) {
      throw new InvalidCredentialsException();
    }
  }
}
