package ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.RoleScope;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, UUID> {

  Optional<Role> findByScopeAndCodeAndInstitutionIsNull(RoleScope scope, String code);

  List<Role> findByScopeAndSystemTrueAndInstitutionIsNullOrderByNameAsc(RoleScope scope);

  Optional<Role> findByIdAndScopeAndInstitution_Id(UUID id, RoleScope scope, UUID institutionId);

  Optional<Role> findByIdAndScope(UUID id, RoleScope scope);

  Optional<Role> findByScopeAndCodeAndInstitution_Id(
      RoleScope scope, String code, UUID institutionId);

  List<Role> findByScopeAndInstitution_IdOrderByNameAsc(RoleScope scope, UUID institutionId);

  @Query(
      """
      SELECT role
      FROM Role role
      WHERE role.scope = :scope
        AND role.institution.id = :institutionId
        AND (
          :search IS NULL
          OR UNACCENT_LOWER(role.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR UNACCENT_LOWER(role.code) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
        )
      """)
  Page<Role> findInstitutionRoles(
      @Param("scope") RoleScope scope,
      @Param("institutionId") UUID institutionId,
      @Param("search") @Nullable String search,
      Pageable pageable);

  @EntityGraph(attributePaths = "institution")
  @Query(
      """
      SELECT role
      FROM Role role
      JOIN role.institution institution
      WHERE role.scope = :scope
        AND (:institutionId IS NULL OR institution.id = :institutionId)
        AND (:system IS NULL OR role.system = :system)
        AND (
          :search IS NULL
          OR UNACCENT_LOWER(role.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR UNACCENT_LOWER(institution.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
        )
      """)
  Page<Role> findPlatformRoles(
      @Param("scope") RoleScope scope,
      @Param("institutionId") @Nullable UUID institutionId,
      @Param("system") @Nullable Boolean system,
      @Param("search") @Nullable String search,
      Pageable pageable);

  boolean existsByScopeAndInstitution_IdAndNameIgnoreCase(
      RoleScope scope, UUID institutionId, String name);

  boolean existsByScopeAndInstitution_IdAndNameIgnoreCaseAndIdNot(
      RoleScope scope, UUID institutionId, String name, UUID id);
}
