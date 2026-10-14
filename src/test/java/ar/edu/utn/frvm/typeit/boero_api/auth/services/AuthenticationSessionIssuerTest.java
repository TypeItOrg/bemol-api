package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.InvalidLoginAttemptException;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AuthorityResolver;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthenticationSessionIssuerTest {
  @Mock private LoginSessionPersistenceService persistence;
  @Mock private JwtService jwt;
  @Mock private AuthorityResolver authorities;
  @Mock private RecentAuthService recentAuth;
  @Mock private UserRepository users;
  @Mock private LoginAttemptService attempts;
  private AuthenticationSessionIssuer issuer;
  private final UUID userId = UUID.randomUUID();
  private final UUID institutionId = UUID.randomUUID();
  private final LoginAttempt attempt =
      new LoginAttempt("attempt", userId, institutionId, false, Instant.now());

  @BeforeEach
  void setup() {
    issuer =
        new AuthenticationSessionIssuer(persistence, jwt, authorities, recentAuth, users, attempts);
  }

  @Test
  @DisplayName("Should claim a verified attempt before persisting and issuing tokens")
  void claimsBeforeIssuance() {
    final var institution = Institution.builder().id(institutionId).build();
    final var person =
        Person.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .documentNumber("12345678")
            .build();
    final User user =
        User.builder().id(userId).institution(institution).person(person).password("hash").build();
    final UUID sessionId = UUID.randomUUID();
    when(users.findWithPersonAndInstitutionById(userId)).thenReturn(Optional.of(user));
    when(persistence.create(userId, "ip", "agent", false))
        .thenReturn(new LoginSessionPersistenceService.Result(sessionId, "refresh"));
    when(jwt.generateAccessToken(any())).thenReturn("access");
    var response = issuer.issuePassword(attempt, user, "ip", "agent", false);
    assertThat(response.tokens().accessToken()).isEqualTo("access");
    assertThat(response.tokens().refreshToken()).isEqualTo("refresh");
    assertThat(response.user().userId()).isEqualTo(userId);
    assertThat(response.user().personId()).isEqualTo(person.getId());
    assertThat(response.user().institutionId()).isEqualTo(institutionId);
    assertThat(response.user().documentNumber()).isEqualTo("12345678");
    final var order = inOrder(attempts, persistence, jwt, recentAuth);
    order.verify(attempts).claim("attempt");
    order.verify(persistence).create(userId, "ip", "agent", false);
    order
        .verify(jwt)
        .generateAccessToken(
            new InstitutionalAccessTokenInput(
                userId, person.getId(), institutionId, "12345678", sessionId));
    order.verify(recentAuth).mark(sessionId, userId, AuthenticationSessionIssuer.METHOD_PASSWORD);
  }

  @Test
  @DisplayName("Should reject an account disabled after password verification without claiming")
  void rejectsFreshlyDisabledAccount() {
    final User authenticated = realUser("hash");
    final User current = realUser("hash");
    current.updateAccess(false);
    when(users.findWithPersonAndInstitutionById(userId)).thenReturn(Optional.of(current));
    assertThatThrownBy(() -> issuer.issuePassword(attempt, authenticated, "ip", "agent", false))
        .isInstanceOf(InvalidCredentialsException.class);
    verifyNoInteractions(attempts, persistence, jwt, authorities, recentAuth);
  }

  @Test
  @DisplayName("Should reject a password reset between authentication and session issuance")
  void rejectsPasswordChangedAfterVerification() {
    final User authenticated = realUser("old-hash");
    final User current = realUser("new-hash");
    when(users.findWithPersonAndInstitutionById(userId)).thenReturn(Optional.of(current));
    assertThatThrownBy(() -> issuer.issuePassword(attempt, authenticated, "ip", "agent", false))
        .isInstanceOf(InvalidCredentialsException.class);
    verifyNoInteractions(attempts, persistence, jwt, authorities, recentAuth);
  }

  @Test
  @DisplayName("Should not issue a session if a verified attempt expires or was already claimed")
  void rejectsClaimFailureBeforePersistence() {
    final User authenticated = realUser("hash");
    when(users.findWithPersonAndInstitutionById(userId)).thenReturn(Optional.of(authenticated));
    when(attempts.claim("attempt")).thenThrow(new InvalidLoginAttemptException());
    assertThatThrownBy(() -> issuer.issuePassword(attempt, authenticated, "ip", "agent", false))
        .isInstanceOf(InvalidLoginAttemptException.class);
    verifyNoInteractions(persistence, jwt, authorities, recentAuth);
  }

  private User realUser(String password) {
    Institution institution = Institution.builder().id(institutionId).build();
    Person person =
        Person.builder()
            .id(UUID.fromString("00000000-0000-0000-0000-000000000003"))
            .institution(institution)
            .documentNumber("12345678")
            .build();
    return User.builder()
        .id(userId)
        .institution(institution)
        .person(person)
        .password(password)
        .build();
  }
}
