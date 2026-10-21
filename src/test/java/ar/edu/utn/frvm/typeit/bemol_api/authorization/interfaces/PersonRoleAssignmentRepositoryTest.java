package ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces;

import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.createInstitution;
import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.persist;
import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.person;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AssignPersonSystemRoleUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorizationCacheInvalidator;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionRoleProvisioner;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.PermissionRoleSeed;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.support.JpaAuditingTestConfig;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({
  JpaAuditingTestConfig.class,
  PermissionRoleSeed.class,
  InstitutionRoleProvisioner.class,
  AssignPersonSystemRoleUseCase.class,
  AuthorizationCacheInvalidator.class,
  SessionRevocationService.class
})
class PersonRoleAssignmentRepositoryTest {

  @MockitoBean private org.springframework.cache.CacheManager cacheManager;

  @Autowired private EntityManager entityManager;
  @Autowired private PermissionRoleSeed permissionRoleSeed;
  @Autowired private PersonRoleAssignmentRepository personRoleAssignmentRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private AssignPersonSystemRoleUseCase assignPersonSystemRoleUseCase;

  private Institution institution;
  private Person authority;
  private Person student;

  @BeforeEach
  void setUp() {
    institution = createInstitution(entityManager, "boero");
    permissionRoleSeed.run(new org.springframework.boot.DefaultApplicationArguments());
    authority = persist(entityManager, person(institution, "11111111"));
    student = persist(entityManager, person(institution, "22222222"));
    assignPersonSystemRoleUseCase.execute(authority, SystemRoleCode.INSTITUTIONAL_AUTHORITY, false);
    assignPersonSystemRoleUseCase.execute(student, SystemRoleCode.STUDENT, false);
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  @DisplayName("Should list person role assignments with role loaded")
  void findByPersonIdAndInstitutionId_loadsRole() {
    List<PersonRoleAssignment> assignments =
        personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(
            student.getId(), institution.getId());

    assertThat(assignments).hasSize(1);
    assertThat(assignments.getFirst().getRole().getCode()).isEqualTo(SystemRoleCode.STUDENT.name());
    assertThat(assignments.getFirst().getRole().getName()).isEqualTo("Estudiante");
  }

  @Test
  @DisplayName("Should count institutional authorities in institution")
  void countByInstitutionIdAndRoleCode_countsAuthorities() {
    assertThat(
            personRoleAssignmentRepository.countByInstitution_IdAndRole_Code(
                institution.getId(), SystemRoleCode.INSTITUTIONAL_AUTHORITY.name()))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("Should list institutional system roles")
  void findInstitutionalSystemRoles_returnsCatalog() {
    List<Role> roles =
        roleRepository.findByScopeAndSystemTrueAndInstitutionIsNullOrderByNameAsc(
            RoleScope.INSTITUTION);

    assertThat(roles).hasSize(SystemRoleCode.values().length);
    assertThat(roles)
        .extracting(mappedRole -> mappedRole.getCode())
        .contains(SystemRoleCode.TEACHER.name());
  }
}
