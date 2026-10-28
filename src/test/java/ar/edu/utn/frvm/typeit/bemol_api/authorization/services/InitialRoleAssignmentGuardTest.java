package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class InitialRoleAssignmentGuardTest {

  @Mock private AuthorizationService authorizationService;
  @InjectMocks private InitialRoleAssignmentGuard guard;

  @Test
  @DisplayName("Should allow the default applicant role without elevated permissions")
  void check_allowsApplicant() {
    assertThatCode(() -> guard.check(null, SystemRoleCode.APPLICANT)).doesNotThrowAnyException();

    verifyNoInteractions(authorizationService);
  }

  @ParameterizedTest
  @EnumSource(
      value = SystemRoleCode.class,
      names = {"INSTITUTIONAL_AUTHORITY", "REPRESENTATIVE"})
  @DisplayName("Should reject a privileged initial role without role assignment permission")
  void check_rejectsPrivilegedRoleWithoutPermission(final SystemRoleCode role) {
    final var authentication = new TestingAuthenticationToken("user", "");

    assertThatThrownBy(() -> guard.check(authentication, role))
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
}
