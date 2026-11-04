package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.platformPrincipal;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.cache.AuthorizationCacheKeys;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.cache.AuthorizationCacheNames;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.AccessScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PlatformAccountRoleRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.OperatingContextResolver;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(AuthorityResolverCacheConsistencyTest.Config.class)
class AuthorityResolverCacheConsistencyTest {
  private static final UUID PRINCIPAL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID INSTITUTION_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID TRAINING_PATH_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final UUID OTHER_PATH_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");

  @MockitoBean private PersonRoleAssignmentRepository assignments;
  @MockitoBean private PlatformAccountRoleRepository platformRoles;
  @Autowired private CachedAuthoritySnapshotResolver snapshots;
  @Autowired private AuthorityResolver authorities;
  @Autowired private CacheManager cacheManager;
  private AuthorizationService authorization;

  @BeforeEach
  void setUp() {
    cache(AuthorizationCacheNames.PERSON_AUTHORITIES).clear();
    cache(AuthorizationCacheNames.PLATFORM_AUTHORITIES).clear();
    authorization = new AuthorizationService(authorities, mock(OperatingContextResolver.class));
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void latePersonCacheFillCannotRestoreARevokedPermission() {
    var permission = PermissionCode.INSTITUTION_PERSON_READ_ANY;
    var row = institutionalRow(permission, AccessScope.INSTITUTION);
    when(assignments.findAuthoritiesByPersonIdAndInstitutionId(PRINCIPAL_ID, INSTITUTION_ID))
        .thenReturn(List.of(row));
    var beforeDowngrade = snapshots.resolveForPerson(PRINCIPAL_ID, INSTITUTION_ID);

    when(assignments.findAuthoritiesByPersonIdAndInstitutionId(PRINCIPAL_ID, INSTITUTION_ID))
        .thenReturn(List.of());
    var key = AuthorizationCacheKeys.person(PRINCIPAL_ID, INSTITUTION_ID);
    var cache = cache(AuthorizationCacheNames.PERSON_AUTHORITIES);
    cache.evict(key);
    // An older miss completes after the downgrade's after-commit eviction.
    cache.put(key, beforeDowngrade);
    assertThat(snapshots.resolveForPerson(PRINCIPAL_ID, INSTITUTION_ID).permissions())
        .contains(permission);

    var authentication = institutionalAuthentication();

    assertThat(authorization.hasPermission(authentication, permission)).isFalse();
    assertThat(authorization.hasAnyPermission(authentication, permission)).isFalse();
  }

  @Test
  void latePersonCacheFillCannotRestoreAnInstitutionWideScope() {
    var permission = PermissionCode.ENROLLMENT_APPLICATION_READ;
    var institutional = institutionalRow(permission, AccessScope.INSTITUTION);
    var limited = institutionalRow(permission, AccessScope.TRAINING_PATHS);
    when(assignments.findAuthoritiesByPersonIdAndInstitutionId(PRINCIPAL_ID, INSTITUTION_ID))
        .thenReturn(List.of(institutional));
    var beforeDowngrade = snapshots.resolveForPerson(PRINCIPAL_ID, INSTITUTION_ID);

    when(assignments.findAuthoritiesByPersonIdAndInstitutionId(PRINCIPAL_ID, INSTITUTION_ID))
        .thenReturn(List.of(limited));
    var key = AuthorizationCacheKeys.person(PRINCIPAL_ID, INSTITUTION_ID);
    var cache = cache(AuthorizationCacheNames.PERSON_AUTHORITIES);
    cache.evict(key);
    cache.put(key, beforeDowngrade);
    SecurityContextHolder.getContext().setAuthentication(institutionalAuthentication());
    var scopedAuthorization =
        new ScopedAuthorizationService(
            authorities, mock(OperatingContextResolver.class), snapshots);

    var access = scopedAuthorization.access(permission);

    assertThat(access.institutional()).isFalse();
    assertThat(access.includes(TRAINING_PATH_ID)).isTrue();
    assertThat(access.includes(OTHER_PATH_ID)).isFalse();
  }

  @Test
  void latePlatformCacheFillCannotRestoreARevokedRoleOrPermission() {
    var row = mock(PlatformAccountRoleRepository.AuthorityRow.class);
    var permission = PermissionCode.INSTITUTION_PERSON_READ_ANY;
    when(row.getRoleCode()).thenReturn(PlatformRoleCode.PLATFORM_ADMIN.name());
    when(row.getPermissionCode()).thenReturn(permission.getCode());
    when(platformRoles.findAuthoritiesByPlatformAccountId(PRINCIPAL_ID)).thenReturn(List.of(row));
    var beforeDowngrade = snapshots.resolveForPlatformAccount(PRINCIPAL_ID);

    when(platformRoles.findAuthoritiesByPlatformAccountId(PRINCIPAL_ID)).thenReturn(List.of());
    var cache = cache(AuthorizationCacheNames.PLATFORM_AUTHORITIES);
    cache.evict(PRINCIPAL_ID);
    cache.put(PRINCIPAL_ID, beforeDowngrade);
    assertThat(snapshots.resolveForPlatformAccount(PRINCIPAL_ID).roles())
        .contains(PlatformRoleCode.PLATFORM_ADMIN);
    var authentication = new TestingAuthenticationToken(platformPrincipal(PRINCIPAL_ID), "");

    assertThat(authorization.hasPlatformRole(authentication, PlatformRoleCode.PLATFORM_ADMIN))
        .isFalse();
    assertThat(authorization.hasPermission(authentication, permission)).isFalse();
  }

  private TestingAuthenticationToken institutionalAuthentication() {
    var principal =
        JwtAuthenticatedUser.builder()
            .userId(PRINCIPAL_ID)
            .personId(PRINCIPAL_ID)
            .institutionId(INSTITUTION_ID)
            .documentNumber("12345678")
            .sessionId(PRINCIPAL_ID)
            .tokenId("local-test")
            .build();
    return new TestingAuthenticationToken(principal, "");
  }

  private PersonRoleAssignmentRepository.AuthorityRow institutionalRow(
      PermissionCode permission, AccessScope scope) {
    var row = mock(PersonRoleAssignmentRepository.AuthorityRow.class);
    when(row.getRoleName()).thenReturn("Custom role");
    when(row.getPermissionCode()).thenReturn(permission.getCode());
    when(row.getAccessScope()).thenReturn(scope);
    if (scope == AccessScope.TRAINING_PATHS) {
      when(row.getTrainingPathId()).thenReturn(TRAINING_PATH_ID);
    }
    return row;
  }

  private Cache cache(String name) {
    return requireNonNull(cacheManager.getCache(name));
  }

  @Configuration(proxyBeanMethods = false)
  @EnableCaching
  @Import({CachedAuthoritySnapshotResolver.class, AuthorityResolver.class})
  static class Config {
    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager(
          AuthorizationCacheNames.PERSON_AUTHORITIES, AuthorizationCacheNames.PLATFORM_AUTHORITIES);
    }
  }
}
