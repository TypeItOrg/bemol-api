package ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.RolePermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.RolePermissionId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

  boolean existsByRoleIdAndPermissionId(UUID roleId, UUID permissionId);

  List<RolePermission> findByRole_Id(UUID roleId);

  List<RolePermission> findByRole_IdIn(List<UUID> roleIds);

  @Query(
      """
      SELECT rp.permission.code
      FROM RolePermission rp
      WHERE rp.role.id IN :roleIds
      """)
  List<String> findPermissionCodesByRoleIds(@Param("roleIds") List<UUID> roleIds);
}
