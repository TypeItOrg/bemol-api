package ar.edu.utn.frvm.typeit.boero_api.authorization.services;

import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.RoleNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.RoleRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.payloads.InstitutionRoleResponse;
import ar.edu.utn.frvm.typeit.boero_api.authorization.payloads.PlatformRoleResponse;
import ar.edu.utn.frvm.typeit.boero_api.common.search.SearchNormalization;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QueryInstitutionRolesUseCase {
  private final RoleRepository roles;
  private final PersonRoleAssignmentRepository assignments;
  private final InstitutionRolePermissions permissions;
  private final RoleManagementProtection protection;
  private final InstitutionRoleResponseFactory responses;

  @Transactional(readOnly = true)
  public List<InstitutionRoleResponse> list(
      final UUID institutionId, final boolean includeAuthority) {
    final var values =
        roles
            .findByScopeAndInstitution_IdOrderByNameAsc(RoleScope.INSTITUTION, institutionId)
            .stream()
            .filter(role -> includeAuthority || !role.isInstitutionalAuthority())
            .toList();

    return responses.from(values);
  }

  @Transactional(readOnly = true)
  public PaginatedResponse<InstitutionRoleResponse> list(
      final UUID institutionId, final String search, final Pageable pageable) {
    final var page =
        roles.findInstitutionRoles(
            RoleScope.INSTITUTION,
            institutionId,
            SearchNormalization.normalizeSearch(search),
            pageable);

    return new PaginatedResponse<>(
        responses.from(page.getContent()),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  @Transactional(readOnly = true)
  public PlatformRoleResponse platform(final UUID roleId) {
    final var role =
        roles
            .findByIdAndScope(roleId, RoleScope.INSTITUTION)
            .orElseThrow(RoleNotFoundException::new);

    return PlatformRoleResponse.from(
        role, assignments.countByRole_Id(roleId), permissions.codes(role), Set.of());
  }

  @Transactional(readOnly = true)
  public InstitutionRoleResponse get(
      final UUID institutionId,
      final UUID roleId,
      final boolean includeAuthority,
      final UUID actorPersonId) {
    final var role =
        roles
            .findByIdAndScopeAndInstitution_Id(roleId, RoleScope.INSTITUTION, institutionId)
            .orElseThrow(RoleNotFoundException::new);
    if (!includeAuthority && role.isInstitutionalAuthority()) {
      throw new RoleNotFoundException();
    }
    final var codes = permissions.codes(role);
    final var protectedCodes =
        new HashSet<>(protection.requiredForActor(role, institutionId, actorPersonId));
    protectedCodes.retainAll(codes);

    return responses.from(role, codes, Set.copyOf(protectedCodes));
  }
}
