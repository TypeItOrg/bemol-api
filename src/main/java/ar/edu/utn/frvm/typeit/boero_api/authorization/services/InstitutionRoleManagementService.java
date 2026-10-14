package ar.edu.utn.frvm.typeit.boero_api.authorization.services;

import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionScope;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.DuplicateRoleNameException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.InstitutionInactiveForRoleManagementException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.InstitutionalAuthorityRoleImmutableException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.RoleNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.RoleWithAssignmentsException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.SystemRoleNotDeletableException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.RolePermissionRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.RoleRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.payloads.InstitutionRoleRequest;
import ar.edu.utn.frvm.typeit.boero_api.authorization.payloads.InstitutionRoleResponse;
import ar.edu.utn.frvm.typeit.boero_api.authorization.payloads.PlatformRoleResponse;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstitutionRoleManagementService {
  private static final Set<PermissionCode> PLATFORM_ADMIN_PERMISSIONS =
      Arrays.stream(PermissionCode.values())
          .filter(permission -> permission.getScope() == PermissionScope.INSTITUTION)
          .filter(permission -> permission.isConfigurable())
          .collect(Collectors.toUnmodifiableSet());
  private final RoleAdministrationLock administrationLock;
  private final RoleRepository roles;
  private final ScopedAuthorizationService authorization;
  private final RolePermissionRepository rolePermissions;
  private final PersonRoleAssignmentRepository assignments;
  private final InstitutionRepository institutions;
  private final AuthorizationCacheInvalidator cache;
  private final InstitutionRolePermissions permissions;
  private final RoleManagementProtection protection;
  private final InstitutionRoleResponseFactory responses;
  private final QueryInstitutionRolesUseCase queries;

  @Transactional(readOnly = true)
  public List<InstitutionRoleResponse> list(
      final UUID institutionId, final boolean includeAuthority) {
    return queries.list(institutionId, includeAuthority);
  }

  @Transactional(readOnly = true)
  public PaginatedResponse<InstitutionRoleResponse> list(
      final UUID institutionId, final String search, final Pageable pageable) {
    return queries.list(institutionId, search, pageable);
  }

  @Transactional(readOnly = true)
  public PlatformRoleResponse getAsPlatformAdmin(final UUID roleId) {
    return queries.platform(roleId);
  }

  @Transactional(readOnly = true)
  public InstitutionRoleResponse get(
      final UUID institutionId,
      final UUID roleId,
      final boolean includeAuthority,
      final UUID actorPersonId) {
    return queries.get(institutionId, roleId, includeAuthority, actorPersonId);
  }

  @Transactional
  public InstitutionRoleResponse create(
      final UUID institutionId,
      final InstitutionRoleRequest request,
      Set<PermissionCode> actorPermissions) {
    administrationLock.lock(institutionId);
    if (!authorization.isPlatformAdministrator()) {
      authorization.requireDelegation(
          PermissionCode.INSTITUTION_ROLE_CREATE, PermissionAccess.institution());
      actorPermissions = authorization.freshPermissions();
    }
    final var name = normalizedName(request.name());
    ensureUniqueName(institutionId, name, null);
    final var institution =
        institutions.findById(institutionId).orElseThrow(InstitutionNotFoundException::new);

    final var role =
        roles.save(
            Role.customInstitutional(
                "CUSTOM_" + UUID.randomUUID().toString().replace("-", ""), name, institution));
    permissions.replace(role, request.permissions(), actorPermissions, false);

    return responses.from(role);
  }

  @Transactional
  public InstitutionRoleResponse createAsPlatformAdmin(
      final UUID institutionId, final InstitutionRoleRequest request) {
    administrationLock.lock(institutionId);
    ensureActiveInstitution(institutionId);
    return create(institutionId, request, PLATFORM_ADMIN_PERMISSIONS);
  }

  @Transactional
  public InstitutionRoleResponse update(
      final UUID institutionId,
      final UUID roleId,
      final InstitutionRoleRequest request,
      final UUID actorPersonId,
      Set<PermissionCode> actorPermissions) {
    administrationLock.lock(institutionId);
    authorization.requireDelegation(
        PermissionCode.INSTITUTION_ROLE_UPDATE, PermissionAccess.institution());
    actorPermissions = authorization.freshPermissions();
    final var role = requireMutableRole(institutionId, roleId);
    final var name = normalizedName(request.name());
    ensureUniqueName(institutionId, name, roleId);

    role.rename(name);
    protection.requireRetained(role, institutionId, actorPersonId, request.permissions());
    permissions.requireDelegableChanges(role, request.permissions());
    permissions.replace(role, request.permissions(), actorPermissions, true);
    cache.evictPeopleForRole(role.getId(), institutionId);

    return responses.from(role);
  }

  @Transactional
  public InstitutionRoleResponse updateAsPlatformAdmin(
      final UUID institutionId, final UUID roleId, final InstitutionRoleRequest request) {
    administrationLock.lock(institutionId);
    final var role = requireRole(institutionId, roleId);
    ensureActiveInstitution(role);
    if (role.isInstitutionalAuthority()) {
      throw new InstitutionalAuthorityRoleImmutableException();
    }
    final var name = normalizedName(request.name());
    ensureUniqueName(institutionId, name, roleId);

    role.rename(name);
    permissions.replace(role, request.permissions(), PLATFORM_ADMIN_PERMISSIONS, true);
    cache.evictPeopleForRole(role.getId(), institutionId);

    return responses.from(role);
  }

  @Transactional
  public void delete(final UUID institutionId, final UUID roleId) {
    administrationLock.lock(institutionId);
    if (!authorization.isPlatformAdministrator()) {
      authorization.requireDelegation(
          PermissionCode.INSTITUTION_ROLE_DELETE, PermissionAccess.institution());
    }
    final var role = requireRole(institutionId, roleId);
    if (role.isSystem()) {
      throw new SystemRoleNotDeletableException();
    }
    if (assignments.countByRole_Id(roleId) > 0) {
      throw new RoleWithAssignmentsException();
    }

    rolePermissions.deleteAll(rolePermissions.findByRole_Id(roleId));
    roles.delete(role);
  }

  @Transactional
  public void deleteAsPlatformAdmin(final UUID institutionId, final UUID roleId) {
    administrationLock.lock(institutionId);
    final var role = requireRole(institutionId, roleId);
    ensureActiveInstitution(role);
    delete(institutionId, roleId);
  }

  private Role requireRole(final UUID institutionId, final UUID roleId) {
    return roles
        .findByIdAndScopeAndInstitution_Id(roleId, RoleScope.INSTITUTION, institutionId)
        .orElseThrow(RoleNotFoundException::new);
  }

  private Role requireMutableRole(final UUID institutionId, final UUID roleId) {
    final var role = requireRole(institutionId, roleId);
    if (role.isInstitutionalAuthority()) {
      throw new InstitutionalAuthorityRoleImmutableException();
    }
    return role;
  }

  private void ensureActiveInstitution(final UUID institutionId) {
    final var institution =
        institutions.findById(institutionId).orElseThrow(InstitutionNotFoundException::new);
    if (!institution.isActive()) {
      throw new InstitutionInactiveForRoleManagementException();
    }
  }

  private void ensureActiveInstitution(final Role role) {
    if (!role.getInstitution().isActive()) {
      throw new InstitutionInactiveForRoleManagementException();
    }
  }

  private void ensureUniqueName(
      final UUID institutionId, final String name, final @Nullable UUID excludedId) {
    final boolean exists =
        excludedId == null
            ? roles.existsByScopeAndInstitution_IdAndNameIgnoreCase(
                RoleScope.INSTITUTION, institutionId, name)
            : roles.existsByScopeAndInstitution_IdAndNameIgnoreCaseAndIdNot(
                RoleScope.INSTITUTION, institutionId, name, excludedId);
    if (exists) {
      throw new DuplicateRoleNameException();
    }
  }

  private String normalizedName(final String name) {
    return name.trim().replaceAll("\\s+", " ");
  }
}
