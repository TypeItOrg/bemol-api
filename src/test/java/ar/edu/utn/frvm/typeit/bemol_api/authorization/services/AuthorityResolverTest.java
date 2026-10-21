package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.createInstitution;
import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.persist;
import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.person;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.support.JpaAuditingTestConfig;
import jakarta.persistence.EntityManager;
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
  AssignPlatformRoleUseCase.class,
  AuthorizationCacheInvalidator.class,
  CachedAuthoritySnapshotResolver.class,
  AuthorityResolver.class,
  SessionRevocationService.class
})
class AuthorityResolverTest {

  @MockitoBean private org.springframework.cache.CacheManager cacheManager;

  @Autowired private EntityManager entityManager;
  @Autowired private PermissionRoleSeed permissionRoleSeed;
  @Autowired private InstitutionRoleProvisioner institutionRoleProvisioner;
  @Autowired private AssignPersonSystemRoleUseCase assignPersonSystemRoleUseCase;
  @Autowired private AssignPlatformRoleUseCase assignPlatformRoleUseCase;
  @Autowired private AuthorityResolver authorityResolver;

  @BeforeEach
  void seedCatalog() {
    permissionRoleSeed.run(new org.springframework.boot.DefaultApplicationArguments());
  }

  @Test
  @DisplayName("Should resolve APPLICANT permissions for a person with that role")
  void resolveForPerson_returnsApplicantPermissions() {
    Institution institution = createInstitution(entityManager, "boero-resolver");
    institutionRoleProvisioner.provision(institution);
    Person person = persist(entityManager, person(institution, "12345678"));
    entityManager.flush();

    assignPersonSystemRoleUseCase.execute(person, SystemRoleCode.APPLICANT);

    var authorities =
        authorityResolver.resolvePersonAuthorities(person.getId(), institution.getId());

    assertThat(authorities.permissions())
        .containsExactlyInAnyOrder(PermissionCode.ACADEMIC_OFFER_READ);
    assertThat(authorities.roles()).containsExactly(SystemRoleCode.APPLICANT.getDisplayName());
  }

  @Test
  @DisplayName("Should return no permissions for a person without role assignments")
  void resolveForPerson_returnsEmptyWhenNoRoles() {
    Institution institution = createInstitution(entityManager, "boero-no-roles");
    Person person = persist(entityManager, person(institution, "87654321"));
    entityManager.flush();

    assertThat(authorityResolver.resolveForPerson(person.getId(), institution.getId())).isEmpty();
  }

  @Test
  @DisplayName("Should resolve PLATFORM_ADMIN role for a platform account")
  void resolvePlatformRoles_returnsPlatformAdminRole() {
    PlatformAccount account =
        PlatformAccount.builder()
            .email("admin-resolver@test.com")
            .name("Admin")
            .lastName("Resolver")
            .password("encoded")
            .build();
    entityManager.persist(account);
    entityManager.flush();

    assignPlatformRoleUseCase.execute(account, PlatformRoleCode.PLATFORM_ADMIN, false);

    assertThat(authorityResolver.resolvePlatformRoles(account.getId()))
        .containsExactly(PlatformRoleCode.PLATFORM_ADMIN);
    assertThat(authorityResolver.resolveForPlatformAccount(account.getId()))
        .hasSize(PermissionCode.values().length);
  }
}
