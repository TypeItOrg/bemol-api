package ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.PlatformAccountRole;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformAccountRoleRepository extends JpaRepository<PlatformAccountRole, UUID> {

  interface AuthorityRow {
    String getRoleCode();

    @Nullable String getPermissionCode();
  }

  List<PlatformAccountRole> findByPlatformAccount_Id(UUID platformAccountId);

  boolean existsByPlatformAccount_IdAndRole_Id(UUID platformAccountId, UUID roleId);

  @Query(
      """
      SELECT par.role.id
      FROM PlatformAccountRole par
      WHERE par.platformAccount.id = :platformAccountId
      """)
  List<UUID> findRoleIdsByPlatformAccountId(@Param("platformAccountId") UUID platformAccountId);

  @Query(
      """
      SELECT par.role.code AS roleCode, permission.code AS permissionCode
      FROM PlatformAccountRole par
      LEFT JOIN RolePermission rp ON rp.role.id = par.role.id
      LEFT JOIN rp.permission permission
      WHERE par.platformAccount.id = :platformAccountId
      """)
  List<AuthorityRow> findAuthoritiesByPlatformAccountId(
      @Param("platformAccountId") UUID platformAccountId);

  @Query(
      """
      SELECT par.role.code
      FROM PlatformAccountRole par
      WHERE par.platformAccount.id = :platformAccountId
      AND par.role.system = true
      """)
  List<String> findSystemRoleCodesByPlatformAccountId(
      @Param("platformAccountId") UUID platformAccountId);

  @Query(
      """
      SELECT COUNT(par)
      FROM PlatformAccountRole par
      WHERE par.platformAccount.enabled = true
      AND par.role.scope = ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.RoleScope.PLATFORM
      AND par.role.code = :roleCode
      """)
  long countEnabledAccountsByRoleCode(@Param("roleCode") String roleCode);
}
