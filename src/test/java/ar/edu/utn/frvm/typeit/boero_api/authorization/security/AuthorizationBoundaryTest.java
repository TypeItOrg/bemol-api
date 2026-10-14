package ar.edu.utn.frvm.typeit.boero_api.authorization.security;

import static ar.edu.utn.frvm.typeit.boero_api.support.AuthTestData.institutionalPrincipal;
import static ar.edu.utn.frvm.typeit.boero_api.support.AuthTestData.platformPrincipal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresAnyPermission;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AuthorityResolver;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AuthorizationService;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AuthorizationBoundaryTest {
  private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID INSTITUTION_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000002");

  @Mock private AuthorityResolver resolver;
  private SecuredOperations target;
  private SecuredOperations operations;
  private AdministrativeOperations adminTarget;
  private AdministrativeOperations administrative;
  private AuthorizationService authorization;

  @BeforeEach
  void setUp() {
    authorization = new AuthorizationService(resolver);
    target = new SecuredOperations();
    operations = proxy(target);
    adminTarget = new AdministrativeOperations();
    administrative = proxy(adminTarget);
  }

  private <T> T proxy(T target) {
    AspectJProxyFactory factory = new AspectJProxyFactory(target);
    factory.setProxyTargetClass(true);
    factory.addAspect(new PermissionAuthorizationAspect(authorization));
    factory.addAspect(new RoleAuthorizationAspect(authorization));
    return factory.getProxy();
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @CsvSource({"NONE,false", "INSTITUTION_ROLE_ASSIGN,false", "INSTITUTION_PERSON_READ_ANY,true"})
  void singlePermissionCannotExecuteWithoutTheRequiredGrant(String permission, boolean allowed) {
    authenticateInstitutional(
        permission.equals("NONE") ? Set.of() : Set.of(PermissionCode.valueOf(permission)));

    if (allowed) {
      assertThat(operations.read()).isEqualTo("person data");
      assertThat(target.calls).isEqualTo(1);
    } else {
      assertDenied(operations::read);
      assertThat(target.calls).isZero();
    }
  }

  static Stream<Arguments> anyPermissionGrants() {
    return Stream.of(
        Arguments.of(Set.of(), false),
        Arguments.of(Set.of(PermissionCode.INSTITUTION_PERSON_DELETE), false),
        Arguments.of(Set.of(PermissionCode.INSTITUTION_PERSON_READ_ANY), true),
        Arguments.of(Set.of(PermissionCode.INSTITUTION_ROLE_ASSIGN), true));
  }

  @ParameterizedTest
  @MethodSource("anyPermissionGrants")
  void anyPermissionAcceptsEitherRequiredGrantButNotAnUnrelatedGrant(
      Set<PermissionCode> permissions, boolean allowed) {
    authenticateInstitutional(permissions);

    if (allowed) {
      assertThat(operations.assignOrRead()).isEqualTo("role or person data");
      assertThat(target.calls).isEqualTo(1);
    } else {
      assertDenied(operations::assignOrRead);
      assertThat(target.calls).isZero();
    }
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "anonymous")
  void absentAndUnknownPrincipalsCannotExecuteAnyProtectedOperation(String principal) {
    if (principal != null) {
      SecurityContextHolder.getContext()
          .setAuthentication(new TestingAuthenticationToken(principal, ""));
    }

    assertDenied(operations::read);
    assertDenied(operations::assignOrRead);
    assertDenied(operations::admin);
    assertDenied(administrative::execute);
    assertThat(target.calls).isZero();
    assertThat(adminTarget.calls).isZero();
    verifyNoInteractions(resolver);
  }

  @Test
  void platformRoleProtectsBothMethodsAndEntireTypes() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken(platformPrincipal(ACCOUNT_ID), ""));
    when(resolver.resolvePlatformRoles(ACCOUNT_ID))
        .thenReturn(Set.of(PlatformRoleCode.PLATFORM_ADMIN));

    assertThat(operations.admin()).isEqualTo("platform operation");
    assertThat(administrative.execute()).isEqualTo("administration");
    assertThat(target.calls).isEqualTo(1);
    assertThat(adminTarget.calls).isEqualTo(1);

    when(resolver.resolvePlatformRoles(ACCOUNT_ID)).thenReturn(Set.of());
    assertDenied(operations::admin);
    assertDenied(administrative::execute);
    assertThat(target.calls).isEqualTo(1);
    assertThat(adminTarget.calls).isEqualTo(1);
  }

  @Test
  void institutionalAccountsCannotBorrowPlatformRoles() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new TestingAuthenticationToken(institutionalPrincipal(ACCOUNT_ID, INSTITUTION_ID), ""));

    assertDenied(operations::admin);
    assertDenied(administrative::execute);
    assertThat(target.calls).isZero();
    assertThat(adminTarget.calls).isZero();
    verifyNoInteractions(resolver);
  }

  @Test
  void platformPermissionsResolveThroughThePlatformAccountNamespace() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken(platformPrincipal(ACCOUNT_ID), ""));
    when(resolver.resolveForPlatformAccount(ACCOUNT_ID))
        .thenReturn(Set.of(PermissionCode.INSTITUTION_PERSON_READ_ANY));

    assertThat(operations.read()).isEqualTo("person data");
    assertThat(operations.assignOrRead()).isEqualTo("role or person data");
    assertThat(target.calls).isEqualTo(2);
  }

  private void authenticateInstitutional(Set<PermissionCode> permissions) {
    var principal = institutionalPrincipal(ACCOUNT_ID, INSTITUTION_ID);
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken(principal, ""));
    when(resolver.resolveForPerson(principal.personId(), INSTITUTION_ID)).thenReturn(permissions);
  }

  private static void assertDenied(Runnable operation) {
    assertThatThrownBy(operation::run).isInstanceOf(AccessDeniedException.class);
  }

  static class SecuredOperations {
    private int calls;

    @RequiresPermission(PermissionCode.INSTITUTION_PERSON_READ_ANY)
    public String read() {
      calls++;
      return "person data";
    }

    @RequiresAnyPermission({
      PermissionCode.INSTITUTION_PERSON_READ_ANY,
      PermissionCode.INSTITUTION_ROLE_ASSIGN
    })
    public String assignOrRead() {
      calls++;
      return "role or person data";
    }

    @RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
    public String admin() {
      calls++;
      return "platform operation";
    }
  }

  @RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
  static class AdministrativeOperations {
    private int calls;

    public String execute() {
      calls++;
      return "administration";
    }
  }
}
