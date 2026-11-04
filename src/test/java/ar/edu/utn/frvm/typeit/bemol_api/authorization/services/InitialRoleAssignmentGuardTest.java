package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.AccessScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.PermissionDelegationNotAllowedException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.RoleNotAssignableException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.RolePermissionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.OperatingContextResolver;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class InitialRoleAssignmentGuardTest {

  private static final UUID INSTITUTION_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID PERSON_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

  @Mock private AuthorizationService authorizationService;
  @Mock private InstitutionalSystemRoleResolver systemRoleResolver;
  @Mock private TrainingPathRepository trainingPathRepository;
  @Mock private RolePermissionRepository rolePermissionRepository;
  @Mock private AuthorityResolver authorityResolver;
  @Mock private OperatingContextResolver operatingContext;
  @Mock private CachedAuthoritySnapshotResolver snapshotResolver;

  private InitialRoleAssignmentGuard guard;

  @BeforeEach
  void setUp() {
    final var scopedAuthorization =
        new ScopedAuthorizationService(authorityResolver, operatingContext, snapshotResolver);
    final var scopeValidator =
        new RoleAssignmentScopeValidator(
            trainingPathRepository, rolePermissionRepository, scopedAuthorization);
    guard =
        new InitialRoleAssignmentGuard(authorizationService, systemRoleResolver, scopeValidator);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Should allow the default applicant role without elevated permissions")
  void check_allowsApplicant() {
    assertThatCode(() -> guard.check(null, SystemRoleCode.APPLICANT)).doesNotThrowAnyException();

    verifyNoInteractions(authorizationService);
  }

  @Test
  @DisplayName("Should reject a privileged initial role without role assignment permission")
  void check_rejectsPrivilegedRoleWithoutPermission() {
    final var authentication = new TestingAuthenticationToken("user", "");

    assertThatThrownBy(() -> guard.check(authentication, SystemRoleCode.INSTITUTIONAL_AUTHORITY))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("Should allow a privileged initial role for a platform administrator")
  void check_allowsPrivilegedRoleForPlatformAdmin() {
    final var principal =
        JwtAuthenticatedPlatformAccount.builder()
            .platformAccountId(UUID.randomUUID())
            .email("admin@example.com")
            .sessionId(UUID.randomUUID())
            .tokenId("token")
            .build();
    final var authentication = new TestingAuthenticationToken(principal, "");
    when(authorizationService.hasPlatformRole(authentication, PlatformRoleCode.PLATFORM_ADMIN))
        .thenReturn(true);

    assertThatCode(() -> guard.check(authentication, SystemRoleCode.INSTITUTIONAL_AUTHORITY))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Should allow a privileged initial role with assignment permission")
  void check_allowsPrivilegedRoleWithPermission() {
    final var authentication = new TestingAuthenticationToken("authority", "");
    when(authorizationService.hasActorPermission(
            authentication, PermissionCode.INSTITUTION_ROLE_ASSIGN))
        .thenReturn(true);

    assertThatCode(() -> guard.check(authentication, SystemRoleCode.TEACHER))
        .doesNotThrowAnyException();
  }

  @Test
  void check_rejectsInstitutionalAuthorityDespiteAssignmentPermission() {
    final var authentication = institutionalAuthentication();
    when(authorizationService.hasActorPermission(
            authentication, PermissionCode.INSTITUTION_ROLE_ASSIGN))
        .thenReturn(true);

    assertThatThrownBy(() -> guard.check(authentication, SystemRoleCode.INSTITUTIONAL_AUTHORITY))
        .isInstanceOf(RoleNotAssignableException.class);
  }

  @Test
  void checkDelegation_preservesDefaultApplicantWithoutRoleAssignmentPermission() {
    assertThatCode(() -> guard.checkDelegation(null, INSTITUTION_ID, SystemRoleCode.APPLICANT))
        .doesNotThrowAnyException();

    verifyNoInteractions(authorizationService, systemRoleResolver, snapshotResolver);
  }

  @Test
  void checkDelegation_rejectsInstitutionalAuthorityBeforeRoleResolution() {
    final var authentication = institutionalAuthentication();

    assertThatThrownBy(
            () ->
                guard.checkDelegation(
                    authentication, INSTITUTION_ID, SystemRoleCode.INSTITUTIONAL_AUTHORITY))
        .isInstanceOf(RoleNotAssignableException.class);

    verifyNoInteractions(systemRoleResolver, snapshotResolver);
  }

  @Test
  void checkDelegation_requiresFreshAssignmentPermission() {
    final var authentication = institutionalAuthentication();
    when(snapshotResolver.resolveFreshForPerson(PERSON_ID, INSTITUTION_ID))
        .thenReturn(new InstitutionalAuthoritySnapshot(Set.of(), List.of()));

    assertThatThrownBy(
            () -> guard.checkDelegation(authentication, INSTITUTION_ID, SystemRoleCode.TEACHER))
        .isInstanceOf(PermissionDelegationNotAllowedException.class);

    verifyNoInteractions(systemRoleResolver);
  }

  @Test
  void checkDelegation_rejectsRoleWhosePermissionsTheCallerDoesNotHold() {
    final var authentication = institutionalAuthentication();
    when(snapshotResolver.resolveFreshForPerson(PERSON_ID, INSTITUTION_ID))
        .thenReturn(
            new InstitutionalAuthoritySnapshot(
                Set.of(PermissionCode.INSTITUTION_ROLE_ASSIGN), List.of()));
    stubTeacherRole();

    assertThatThrownBy(
            () -> guard.checkDelegation(authentication, INSTITUTION_ID, SystemRoleCode.TEACHER))
        .isInstanceOf(PermissionDelegationNotAllowedException.class);
  }

  @Test
  void checkDelegation_rejectsInstitutionWideGrantFromTrainingPathLimitedPermission() {
    final var authentication = institutionalAuthentication();
    when(snapshotResolver.resolveFreshForPerson(PERSON_ID, INSTITUTION_ID))
        .thenReturn(
            new InstitutionalAuthoritySnapshot(
                Set.of(PermissionCode.INSTITUTION_ROLE_ASSIGN, PermissionCode.COURSE_READ),
                List.of(),
                Map.of(
                    PermissionCode.INSTITUTION_ROLE_ASSIGN,
                    PermissionAccess.institution(),
                    PermissionCode.COURSE_READ,
                    new PermissionAccess(AccessScope.TRAINING_PATHS, Set.of(ROLE_ID)))));
    stubTeacherRole();

    assertThatThrownBy(
            () -> guard.checkDelegation(authentication, INSTITUTION_ID, SystemRoleCode.TEACHER))
        .isInstanceOf(PermissionDelegationNotAllowedException.class);
  }

  @Test
  void checkDelegation_allowsRoleWhoseInstitutionWidePermissionsTheCallerHolds() {
    final var authentication = institutionalAuthentication();
    when(snapshotResolver.resolveFreshForPerson(PERSON_ID, INSTITUTION_ID))
        .thenReturn(
            new InstitutionalAuthoritySnapshot(
                Set.of(PermissionCode.INSTITUTION_ROLE_ASSIGN, PermissionCode.COURSE_READ),
                List.of()));
    stubTeacherRole();

    assertThatCode(
            () -> guard.checkDelegation(authentication, INSTITUTION_ID, SystemRoleCode.TEACHER))
        .doesNotThrowAnyException();

    verify(rolePermissionRepository).findPermissionCodesByRoleIds(List.of(ROLE_ID));
  }

  @Test
  void checkDelegation_rejectsCallerFromAnotherInstitution() {
    final var authentication = institutionalAuthentication();

    assertThatThrownBy(() -> guard.checkDelegation(authentication, ROLE_ID, SystemRoleCode.TEACHER))
        .isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(systemRoleResolver, snapshotResolver);
  }

  @Test
  void checkDelegation_preservesPlatformAdministratorAuthorityAssignment() {
    final var principal =
        JwtAuthenticatedPlatformAccount.builder()
            .platformAccountId(PERSON_ID)
            .email("admin@example.test")
            .sessionId(ROLE_ID)
            .tokenId("fixture-token")
            .build();
    final var authentication = new TestingAuthenticationToken(principal, "");
    when(authorizationService.hasPlatformRole(authentication, PlatformRoleCode.PLATFORM_ADMIN))
        .thenReturn(true);

    assertThatCode(
            () ->
                guard.checkDelegation(
                    authentication, INSTITUTION_ID, SystemRoleCode.INSTITUTIONAL_AUTHORITY))
        .doesNotThrowAnyException();

    verifyNoInteractions(systemRoleResolver, snapshotResolver);
  }

  private TestingAuthenticationToken institutionalAuthentication() {
    final var principal =
        JwtAuthenticatedUser.builder()
            .userId(PERSON_ID)
            .personId(PERSON_ID)
            .institutionId(INSTITUTION_ID)
            .sessionId(ROLE_ID)
            .tokenId("fixture-token")
            .build();
    final var authentication = new TestingAuthenticationToken(principal, "");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    return authentication;
  }

  private void stubTeacherRole() {
    final var role =
        Role.builder().id(ROLE_ID).code("TEACHER").scope(RoleScope.INSTITUTION).build();
    when(systemRoleResolver.requireInstitutionalSystemRole(INSTITUTION_ID, SystemRoleCode.TEACHER))
        .thenReturn(role);
    when(rolePermissionRepository.findPermissionCodesByRoleIds(List.of(ROLE_ID)))
        .thenReturn(List.of(PermissionCode.COURSE_READ.getCode()));
  }
}
