package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.RolePermissionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.RoleRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.PlatformRoleListItemResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.search.SearchNormalization;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListPlatformRolesUseCase {

  private static final String INSTITUTION_NAME_SORT = "institutionName";
  private static final String INSTITUTION_ENTITY_NAME_SORT = "institution.name";

  private final RoleRepository roleRepository;
  private final PersonRoleAssignmentRepository assignmentRepository;
  private final RolePermissionRepository rolePermissionRepository;

  @Transactional(readOnly = true)
  public PaginatedResponse<PlatformRoleListItemResponse> execute(
      final String search,
      final UUID institutionId,
      final Boolean system,
      final Pageable pageable) {
    final Page<Role> rolesPage =
        roleRepository.findPlatformRoles(
            RoleScope.INSTITUTION,
            institutionId,
            system,
            SearchNormalization.normalizeSearch(search),
            mapSort(pageable));

    if (rolesPage.isEmpty()) {
      return PaginatedResponse.from(
          rolesPage.map(role -> PlatformRoleListItemResponse.from(role, 0, 0)));
    }

    final List<UUID> roleIds =
        rolesPage.getContent().stream().map(mappedRole -> mappedRole.getId()).toList();
    final Map<UUID, Long> assignmentCounts =
        assignmentRepository.countByRoleIds(roleIds).stream()
            .collect(Collectors.toMap(row -> row.getRoleId(), row -> row.getAssignmentCount()));

    final Map<UUID, Long> permissionCounts =
        rolePermissionRepository.findByRole_IdIn(roleIds).stream()
            .collect(
                Collectors.groupingBy(
                    permission -> permission.getRole().getId(), Collectors.counting()));

    return PaginatedResponse.from(
        rolesPage.map(
            role ->
                PlatformRoleListItemResponse.from(
                    role,
                    assignmentCounts.getOrDefault(role.getId(), 0L),
                    permissionCounts.getOrDefault(role.getId(), 0L).intValue())));
  }

  private Pageable mapSort(final Pageable pageable) {
    final List<Sort.Order> mappedOrders =
        pageable.getSort().stream()
            .map(
                order ->
                    order.withProperty(
                        INSTITUTION_NAME_SORT.equals(order.getProperty())
                            ? INSTITUTION_ENTITY_NAME_SORT
                            : order.getProperty()))
            .toList();
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(mappedOrders));
  }
}
