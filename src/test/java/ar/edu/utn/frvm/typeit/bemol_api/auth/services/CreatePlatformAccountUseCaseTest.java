package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PlatformAccountEmailAlreadyExistsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.CreatePlatformAccountRequest;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AssignPlatformRoleUseCase;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class CreatePlatformAccountUseCaseTest {

  @Mock private PlatformAccountRepository platformAccountRepository;
  @Mock private AssignPlatformRoleUseCase assignPlatformRoleUseCase;
  @Mock private PasswordEncoder passwordEncoder;

  private CreatePlatformAccountUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new CreatePlatformAccountUseCase(
            platformAccountRepository,
            assignPlatformRoleUseCase,
            passwordEncoder,
            org.mockito.Mockito.mock(AuditEventRecorder.class));
  }

  @Test
  void createNormalizesEmailHashesPasswordAndAssignsAdministratorRole() {
    final var request =
        new CreatePlatformAccountRequest(
            " María ", " González ", " ADMIN@BOERO.EDU.AR ", "password123");
    when(platformAccountRepository.existsByEmailIgnoreCase("admin@boero.edu.ar")).thenReturn(false);
    when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
    when(platformAccountRepository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation -> {
              final PlatformAccount account = invocation.getArgument(0);
              return PlatformAccount.builder()
                  .id(UUID.randomUUID())
                  .name(account.getName())
                  .lastName(account.getLastName())
                  .email(account.getEmail())
                  .password(account.getPassword())
                  .enabled(account.isEnabled())
                  .build();
            });

    final var response = useCase.execute(request);

    assertThat(response.email()).isEqualTo("admin@boero.edu.ar");
    assertThat(response.name()).isEqualTo("María");
    assertThat(response.lastName()).isEqualTo("González");
    assertThat(response.enabled()).isTrue();
    assertThat(response.roleCode()).isEqualTo(PlatformRoleCode.PLATFORM_ADMIN);
    verify(assignPlatformRoleUseCase)
        .execute(
            org.mockito.ArgumentMatchers.any(PlatformAccount.class),
            org.mockito.ArgumentMatchers.eq(PlatformRoleCode.PLATFORM_ADMIN),
            org.mockito.ArgumentMatchers.eq(false));
  }

  @Test
  void createRejectsDuplicateEmailBeforeEncodingPassword() {
    final var request =
        new CreatePlatformAccountRequest("María", "González", "admin@boero.edu.ar", "password123");
    when(platformAccountRepository.existsByEmailIgnoreCase("admin@boero.edu.ar")).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(request))
        .isInstanceOf(PlatformAccountEmailAlreadyExistsException.class);
    verifyNoInteractions(passwordEncoder, assignPlatformRoleUseCase);
  }
}
