package ar.edu.utn.frvm.typeit.boero_api.authorization.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.authorization.cache.AuthorizationCacheNames;
import ar.edu.utn.frvm.typeit.boero_api.authorization.config.DefaultRolePermissions;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.Permission;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.RolePermission;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PermissionRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.RolePermissionRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.RoleRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class PermissionRoleSeed implements ApplicationRunner {

  private static final long SEED_LOCK_ID = 7_439_201_884L;
  private static final List<String> AUTHORITY_CACHE_NAMES =
      List.of(
          AuthorizationCacheNames.PERSON_AUTHORITIES, AuthorizationCacheNames.PLATFORM_AUTHORITIES);

  private final PermissionRepository permissionRepository;
  private final RoleRepository roleRepository;
  private final RolePermissionRepository rolePermissionRepository;
  private final PersonRoleAssignmentRepository personRoleAssignmentRepository;
  private final AssignPersonSystemRoleUseCase assignPersonSystemRoleUseCase;
  private final InstitutionRepository institutionRepository;
  private final InstitutionRoleProvisioner institutionRoleProvisioner;
  private final Environment environment;
  private final CacheManager cacheManager;
  private final EntityManager entityManager;

  @Override
  @Transactional
  public void run(final ApplicationArguments args) {
    acquireSeedLock();
    final Map<PermissionCode, Permission> permissions = syncPermissions();
    syncInstitutionalRoles(permissions);
    syncPlatformRoles(permissions);
    provisionInstitutionRoles();

    if (shouldBackfillApplicants()) {
      backfillApplicantRoleForPersonsWithoutRoles();
    }

    clearAuthorityCaches();
  }

  private void provisionInstitutionRoles() {
    for (final var institution : institutionRepository.findAll()) {
      institutionRoleProvisioner.provision(institution);
    }
  }

  private void acquireSeedLock() {
    if (environment.acceptsProfiles(Profiles.of("test"))) {
      return;
    }

    entityManager
        .createNativeQuery("select pg_advisory_xact_lock(:lockId)")
        .setParameter("lockId", SEED_LOCK_ID)
        .getSingleResult();
  }

  private Map<PermissionCode, Permission> syncPermissions() {
    Map<String, Permission> byCode =
        permissionRepository.findAll().stream()
            .collect(
                Collectors.toMap(permission -> permission.getCode(), permission -> permission));

    Map<PermissionCode, Permission> synced = new HashMap<>();

    for (PermissionCode code : PermissionCode.values()) {
      Permission permission =
          byCode.computeIfAbsent(
              code.getCode(),
              ignored ->
                  permissionRepository.save(
                      Permission.builder()
                          .code(code.getCode())
                          .description(code.getDescription())
                          .scope(code.getScope())
                          .build()));
      synced.put(code, permission);
    }

    return synced;
  }

  private void syncInstitutionalRoles(Map<PermissionCode, Permission> permissions) {
    for (SystemRoleCode roleCode : SystemRoleCode.values()) {
      syncScopedRole(
          RoleScope.INSTITUTION,
          roleCode.name(),
          roleCode.getDisplayName(),
          DefaultRolePermissions.institutional(roleCode),
          permissions);
    }
  }

  private void syncPlatformRoles(Map<PermissionCode, Permission> permissions) {
    for (PlatformRoleCode roleCode : PlatformRoleCode.values()) {
      Set<PermissionCode> rolePermissionCodes =
          roleCode == PlatformRoleCode.PLATFORM_ADMIN ? Set.of(PermissionCode.values()) : Set.of();
      syncScopedRole(
          RoleScope.PLATFORM,
          roleCode.name(),
          roleCode.getDisplayName(),
          rolePermissionCodes,
          permissions);
    }
  }

  private void syncScopedRole(
      RoleScope scope,
      String code,
      String displayName,
      Set<PermissionCode> permissionCodes,
      Map<PermissionCode, Permission> permissions) {
    Role role = upsertSystemRole(scope, code, displayName);
    syncRolePermissions(role, permissionCodes, permissions);
  }

  private Role upsertSystemRole(RoleScope scope, String code, String displayName) {
    Role role =
        roleRepository
            .findByScopeAndCodeAndInstitutionIsNull(scope, code)
            .orElseGet(
                () ->
                    roleRepository.save(
                        Role.builder()
                            .scope(scope)
                            .code(code)
                            .name(displayName)
                            .system(true)
                            .build()));

    if (!role.getName().equals(displayName)) {
      role.rename(displayName);
      roleRepository.save(role);
    }

    return role;
  }

  private void syncRolePermissions(
      Role role, Set<PermissionCode> permissionCodes, Map<PermissionCode, Permission> permissions) {
    final var requiredPermissionCodes = PermissionCode.withRequiredPermissions(permissionCodes);
    Set<UUID> desiredPermissionIds =
        requiredPermissionCodes.stream()
            .map(code -> requireNonNull(permissions.get(code)).getId())
            .collect(Collectors.toSet());

    for (final var rolePermission : rolePermissionRepository.findByRole_Id(role.getId())) {
      if (!desiredPermissionIds.contains(rolePermission.getPermission().getId())) {
        rolePermissionRepository.delete(rolePermission);
      }
    }

    for (PermissionCode permissionCode : requiredPermissionCodes) {
      Permission permission = requireNonNull(permissions.get(permissionCode));

      if (!rolePermissionRepository.existsByRoleIdAndPermissionId(
          role.getId(), permission.getId())) {
        rolePermissionRepository.save(RolePermission.of(role, permission));
      }
    }
  }

  private boolean shouldBackfillApplicants() {
    return environment.acceptsProfiles(Profiles.of("dev", "test"));
  }

  private void backfillApplicantRoleForPersonsWithoutRoles() {
    List<Person> persons = personRoleAssignmentRepository.findPersonsWithoutRoleAssignments();

    for (Person person : persons) {
      assignPersonSystemRoleUseCase.execute(person, SystemRoleCode.APPLICANT);
    }
  }

  private void clearAuthorityCaches() {
    for (final String cacheName : AUTHORITY_CACHE_NAMES) {
      final var cache = cacheManager.getCache(cacheName);

      if (cache != null) {
        cache.clear();
      }
    }
  }
}
