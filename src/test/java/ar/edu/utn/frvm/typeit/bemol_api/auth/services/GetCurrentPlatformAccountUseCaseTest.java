package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.platformPrincipal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformAccountResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;

@ExtendWith(MockitoExtension.class)
class GetCurrentPlatformAccountUseCaseTest {

  @Mock private PlatformAccountRepository platformAccountRepository;

  private GetCurrentPlatformAccountUseCase getCurrentPlatformAccountUseCase;

  @BeforeEach
  void setUp() {
    getCurrentPlatformAccountUseCase =
        new GetCurrentPlatformAccountUseCase(platformAccountRepository);
  }

  @Test
  @DisplayName("Should return the platform account response for an enabled account")
  void execute_returnsPlatformAccountResponse() {
    UUID platformAccountId = UUID.randomUUID();
    var principal = platformPrincipal(platformAccountId);
    PlatformAccount account =
        PlatformAccount.builder()
            .id(platformAccountId)
            .email("admin@plataforma.com")
            .name("Juan")
            .lastName("Perez")
            .password("encoded-hash")
            .enabled(true)
            .build();

    when(platformAccountRepository.findById(platformAccountId)).thenReturn(Optional.of(account));

    PlatformAccountResponse response = getCurrentPlatformAccountUseCase.execute(principal);

    assertThat(response.account().platformAccountId()).isEqualTo(platformAccountId);
    assertThat(response.account().email()).isEqualTo("admin@plataforma.com");
    assertThat(response.account().name()).isEqualTo("Juan");
    assertThat(response.account().lastName()).isEqualTo("Perez");
  }

  @Test
  @DisplayName("Should throw InvalidCredentialsException when platform account is not found")
  void execute_throwsWhenPlatformAccountNotFound() {
    UUID platformAccountId = UUID.randomUUID();
    var principal = platformPrincipal(platformAccountId);

    when(platformAccountRepository.findById(platformAccountId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> getCurrentPlatformAccountUseCase.execute(principal))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  @DisplayName("Should throw DisabledException when platform account is disabled")
  void execute_throwsWhenPlatformAccountIsDisabled() {
    UUID platformAccountId = UUID.randomUUID();
    var principal = platformPrincipal(platformAccountId);
    PlatformAccount disabledAccount =
        PlatformAccount.builder()
            .id(platformAccountId)
            .email("admin@plataforma.com")
            .name("Juan")
            .lastName("Perez")
            .password("encoded-hash")
            .enabled(false)
            .build();

    when(platformAccountRepository.findById(platformAccountId))
        .thenReturn(Optional.of(disabledAccount));

    assertThatThrownBy(() -> getCurrentPlatformAccountUseCase.execute(principal))
        .isInstanceOf(DisabledException.class);
  }
}
