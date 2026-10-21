package ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.AccessScope;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonRoleAssignmentRepository extends JpaRepository<PersonRoleAssignment, UUID> {

  interface AuthorityRow {
    String getRoleName();

    @Nullable String getPermissionCode();

    AccessScope getAccessScope();

    @Nullable UUID getTrainingPathId();
  }

  interface RoleAssignmentCount {
    UUID getRoleId();

    long getAssignmentCount();
  }

  boolean existsByPerson_IdAndRole_IdAndInstitution_Id(
      UUID personId, UUID roleId, UUID institutionId);

  boolean existsByPerson_IdAndInstitution_IdAndRole_Code(
      UUID personId, UUID institutionId, String roleCode);

  Optional<PersonRoleAssignment> findByPerson_IdAndRole_IdAndInstitution_Id(
      UUID personId, UUID roleId, UUID institutionId);

  @EntityGraph(attributePaths = "role")
  List<PersonRoleAssignment> findByPerson_IdAndInstitution_Id(UUID personId, UUID institutionId);

  @EntityGraph(attributePaths = "role")
  List<PersonRoleAssignment> findByPerson_IdInAndInstitution_Id(
      List<UUID> personIds, UUID institutionId);

  @EntityGraph(attributePaths = "role")
  List<PersonRoleAssignment> findByPerson_IdIn(List<UUID> personIds);

  long countByInstitution_IdAndRole_Code(UUID institutionId, String roleCode);

  long countByRole_Id(UUID roleId);

  @Query(
      """
      SELECT pra.role.id AS roleId, COUNT(pra.id) AS assignmentCount
      FROM PersonRoleAssignment pra
      WHERE pra.role.id IN :roleIds
      GROUP BY pra.role.id
      """)
  List<RoleAssignmentCount> countByRoleIds(@Param("roleIds") List<UUID> roleIds);

  List<PersonRoleAssignment> findByRole_Id(UUID roleId);

  @Query(
      """
      SELECT pra.person.id
      FROM PersonRoleAssignment pra
      WHERE pra.role.id = :roleId
      AND pra.institution.id = :institutionId
      """)
  List<UUID> findPersonIdsByRoleIdAndInstitutionId(
      @Param("roleId") UUID roleId, @Param("institutionId") UUID institutionId);

  @Query(
      """
      SELECT pra.role.id
      FROM PersonRoleAssignment pra
      WHERE pra.person.id = :personId
      AND pra.institution.id = :institutionId
      """)
  List<UUID> findRoleIdsByPersonIdAndInstitutionId(
      @Param("personId") UUID personId, @Param("institutionId") UUID institutionId);

  @Query(
      """
      SELECT COUNT(DISTINCT assignment.person.id)
      FROM PersonRoleAssignment assignment
      WHERE assignment.institution.id = :institutionId
        AND assignment.person.id IN :personIds
        AND assignment.role.code = :roleCode
      """)
  long countDistinctPersonsByInstitutionAndRoleCode(
      @Param("institutionId") UUID institutionId,
      @Param("personIds") List<UUID> personIds,
      @Param("roleCode") String roleCode);

  @Query(
      """
      SELECT pra.role.name AS roleName, permission.code AS permissionCode, pra.accessScope AS accessScope, pathId AS trainingPathId
      FROM PersonRoleAssignment pra
      LEFT JOIN pra.trainingPathIds pathId
      LEFT JOIN RolePermission rp ON rp.role.id = pra.role.id
      LEFT JOIN rp.permission permission
      WHERE pra.person.id = :personId
      AND pra.institution.id = :institutionId
      """)
  List<AuthorityRow> findAuthoritiesByPersonIdAndInstitutionId(
      @Param("personId") UUID personId, @Param("institutionId") UUID institutionId);

  @Query(
      """
      SELECT DISTINCT assignment.person.id
      FROM PersonRoleAssignment assignment
      LEFT JOIN RolePermission rp ON rp.role.id = assignment.role.id
      LEFT JOIN rp.permission permission
      WHERE assignment.institution.id = :institutionId
        AND assignment.person.id IN :personIds
        AND (assignment.role.code IN :institutionalRoleCodes
          OR permission.code NOT IN :academicPermissionCodes)
      """)
  List<UUID> findPersonIdsRequiringInstitutionalAccess(
      @Param("personIds") List<UUID> personIds,
      @Param("institutionId") UUID institutionId,
      @Param("institutionalRoleCodes") Set<String> institutionalRoleCodes,
      @Param("academicPermissionCodes") Set<String> academicPermissionCodes);

  @Query(
      """
      SELECT p FROM Person p
      LEFT JOIN FETCH p.institution
      WHERE NOT EXISTS (
        SELECT 1 FROM PersonRoleAssignment pra WHERE pra.person = p
      )
      """)
  List<Person> findPersonsWithoutRoleAssignments();
}
