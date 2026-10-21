package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.security.InstitutionalUsername;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReAuthenticateUseCaseTest {
  @Mock private UserRepository users;
  @Mock private CredentialsAuthenticator authenticator;
  @Mock private RecentAuthService recent;
  @Mock private User user;
  private final JwtAuthenticatedUser principal =
      JwtAuthenticatedUser.builder()
          .userId(UUID.randomUUID())
          .institutionId(UUID.randomUUID())
          .sessionId(UUID.randomUUID())
          .build();
  private ReAuthenticateUseCase useCase;
  private String username;

  @BeforeEach
  void setup() {
    useCase = new ReAuthenticateUseCase(users, authenticator, recent);
    when(users.findWithPersonAndInstitutionById(principal.userId())).thenReturn(Optional.of(user));
    when(user.getInstitutionId()).thenReturn(principal.institutionId());
    when(user.getDocumentNumber()).thenReturn("12345678");
    username = InstitutionalUsername.format(principal.institutionId(), "12345678");
  }

  @Test
  @DisplayName("Should mark recent authentication only after verifying credentials")
  void marksRecentAfterSuccess() {
    useCase.execute(principal, "password");
    final var order = inOrder(authenticator, recent);
    order.verify(authenticator).authenticate(username, "password");
    order
        .verify(recent)
        .mark(
            principal.sessionId(), principal.userId(), AuthenticationSessionIssuer.METHOD_PASSWORD);
  }

  @Test
  @DisplayName("Should not mark recent authentication for invalid credentials")
  void rejectsInvalidCredentials() {
    when(authenticator.authenticate(username, "wrong"))
        .thenThrow(new InvalidCredentialsException());
    assertThatThrownBy(() -> useCase.execute(principal, "wrong"))
        .isInstanceOf(InvalidCredentialsException.class);
    verifyNoInteractions(recent);
  }
}
