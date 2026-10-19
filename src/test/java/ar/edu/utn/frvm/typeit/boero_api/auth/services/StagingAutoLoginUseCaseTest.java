package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import ar.edu.utn.frvm.typeit.boero_api.auth.config.StagingAutoLoginProperties;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.InstitutionalContextMismatchException;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.AuthResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(MockitoExtension.class)
class StagingAutoLoginUseCaseTest {
  @Mock private UserRepository users;
  @Mock private InstitutionalHostContext hostContext;
  @Mock private AuthenticationSessionIssuer issuer;
  @Mock private HttpServletRequest request;
  private final MockEnvironment environment = new MockEnvironment();
  private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private final UUID institutionId = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private User user;

  @BeforeEach
  void setup() {
    environment.setActiveProfiles("staging");
    final var institution = Institution.builder().id(institutionId).slug("any-institution").build();
    final var person =
        Person.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .documentNumber("99000001")
            .build();
    user =
        User.builder().id(userId).institution(institution).person(person).password("hash").build();
  }

  @Test
  void issuesASessionForTheConfiguredAccountWithoutRequiringAnyRole() {
    final var response = AuthResponse.builder().build();
    when(users.findWithPersonAndInstitutionById(userId)).thenReturn(Optional.of(user));
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    when(request.getHeader("User-Agent")).thenReturn("Browser");
    when(issuer.issue(user, "127.0.0.1", "Browser", false, "AUTO_LOGIN")).thenReturn(response);

    assertThat(
            useCase(new StagingAutoLoginProperties(true, userId, institutionId)).execute(request))
        .isSameAs(response);
    verify(hostContext).requireInstitution(institutionId);
  }

  @Test
  void refusesTheDisabledFlag() {
    assertThatThrownBy(
            () ->
                useCase(new StagingAutoLoginProperties(false, userId, institutionId))
                    .execute(request))
        .isInstanceOf(InvalidCredentialsException.class);
    verifyNoInteractions(users, issuer);
  }

  @ParameterizedTest
  @ValueSource(strings = {"user", "institution"})
  void requiresBothConfiguredIds(final String missing) {
    final var properties =
        new StagingAutoLoginProperties(
            true,
            missing.equals("user") ? null : userId,
            missing.equals("institution") ? null : institutionId);
    assertThatThrownBy(() -> useCase(properties).execute(request))
        .isInstanceOf(InvalidCredentialsException.class);
    verifyNoInteractions(users, issuer);
  }

  @Test
  void refusesAUserFromADifferentInstitution() {
    when(users.findWithPersonAndInstitutionById(userId)).thenReturn(Optional.of(user));
    assertThatThrownBy(
            () ->
                useCase(new StagingAutoLoginProperties(true, userId, UUID.randomUUID()))
                    .execute(request))
        .isInstanceOf(InvalidCredentialsException.class);
    verifyNoInteractions(issuer);
  }

  @Test
  void refusesAnAccountWithoutEnabledAccess() {
    user.updateAccess(false);
    when(users.findWithPersonAndInstitutionById(userId)).thenReturn(Optional.of(user));
    assertThatThrownBy(
            () ->
                useCase(new StagingAutoLoginProperties(true, userId, institutionId))
                    .execute(request))
        .isInstanceOf(InvalidCredentialsException.class);
    verifyNoInteractions(issuer);
  }

  @Test
  void preservesTheInstitutionalHostBoundary() {
    when(users.findWithPersonAndInstitutionById(userId)).thenReturn(Optional.of(user));
    doThrow(new InstitutionalContextMismatchException())
        .when(hostContext)
        .requireInstitution(institutionId);
    assertThatThrownBy(
            () ->
                useCase(new StagingAutoLoginProperties(true, userId, institutionId))
                    .execute(request))
        .isInstanceOf(InstitutionalContextMismatchException.class);
    verifyNoInteractions(issuer);
  }

  private StagingAutoLoginUseCase useCase(final StagingAutoLoginProperties properties) {
    return new StagingAutoLoginUseCase(properties, environment, users, hostContext, issuer);
  }
}
