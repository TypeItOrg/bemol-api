package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.cache.AuthorizationCacheNames;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.AccessScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PlatformAccountRoleRepository;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CachedAuthoritySnapshotResolver {

  private final PersonRoleAssignmentRepository personRoleAssignmentRepository;
  private final PlatformAccountRoleRepository platformAccountRoleRepository;

  @Cacheable(
      value = AuthorizationCacheNames.PERSON_AUTHORITIES,
      key = "#personId + '-' + #institutionId")
  @Transactional(readOnly = true)
  public InstitutionalAuthoritySnapshot resolveForPerson(UUID personId, UUID institutionId) {
    return resolveFreshForPerson(personId, institutionId);
  }

  @Transactional(readOnly = true)
  public InstitutionalAuthoritySnapshot resolveFreshForPerson(UUID personId, UUID institutionId) {
    List<PersonRoleAssignmentRepository.AuthorityRow> rows =
        personRoleAssignmentRepository.findAuthoritiesByPersonIdAndInstitutionId(
            personId, institutionId);

    Map<PermissionCode, PermissionAccess> scopes = new EnumMap<>(PermissionCode.class);
    for (var row : rows) {
      if (row.getPermissionCode() == null) {
        continue;
      }
      PermissionCode permission = PermissionCode.fromCode(row.getPermissionCode());
      PermissionAccess access;
      if (row.getAccessScope() == AccessScope.INSTITUTION) {
        access = PermissionAccess.institution();
      } else if (permission.supportsTrainingPaths() && row.getTrainingPathId() != null) {
        access = new PermissionAccess(AccessScope.TRAINING_PATHS, Set.of(row.getTrainingPathId()));
      } else {
        continue;
      }
      scopes.merge(permission, access, (left, right) -> left.union(right));
    }
    Set<PermissionCode> permissions = scopes.keySet();
    List<String> roles =
        rows.stream()
            .map(mappedAuthorityRow -> mappedAuthorityRow.getRoleName())
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();

    final var systemRoleCodes =
        rows.stream()
            .filter(row -> row.getSystemRole())
            .map(row -> SystemRoleCode.valueOf(row.getRoleCode()))
            .collect(Collectors.toUnmodifiableSet());

    return new InstitutionalAuthoritySnapshot(permissions, roles, scopes, systemRoleCodes);
  }

  @Cacheable(value = AuthorizationCacheNames.PLATFORM_AUTHORITIES, key = "#platformAccountId")
  @Transactional(readOnly = true)
  public PlatformAuthoritySnapshot resolveForPlatformAccount(UUID platformAccountId) {
    List<PlatformAccountRoleRepository.AuthorityRow> rows =
        platformAccountRoleRepository.findAuthoritiesByPlatformAccountId(platformAccountId);

    Set<PermissionCode> permissions =
        rows.stream()
            .map(mappedAuthorityRow -> mappedAuthorityRow.getPermissionCode())
            .filter(code -> code != null)
            .map(PermissionCode::fromCode)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(PermissionCode.class)));
    Set<PlatformRoleCode> roles =
        rows.stream()
            .map(mappedAuthorityRow -> mappedAuthorityRow.getRoleCode())
            .map(this::parsePlatformRoleCode)
            .flatMap(mappedOptional -> mappedOptional.stream())
            .collect(Collectors.toUnmodifiableSet());

    return new PlatformAuthoritySnapshot(permissions, roles);
  }

  private Optional<PlatformRoleCode> parsePlatformRoleCode(String code) {
    try {
      return Optional.of(PlatformRoleCode.valueOf(code));
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }
}
