package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import static ar.edu.utn.frvm.typeit.boero_api.support.AuthTestData.institutionalPrincipal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.UserResponse;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AuthorityResolver;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionalAuthoritySnapshot;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;

@ExtendWith(MockitoExtension.class)
class GetCurrentUserUseCaseTest {

  @Mock private UserRepository userRepository;
  @Mock private AuthorityResolver authorityResolver;
  @Mock private PersonRepository people;

  private GetCurrentUserUseCase getCurrentUserUseCase;

  @BeforeEach
  void setUp() {
    getCurrentUserUseCase = new GetCurrentUserUseCase(userRepository, authorityResolver, people);
  }

  @Test
  @DisplayName("Should return the user response for an enabled user")
  void execute_returnsUserResponse() {
    UUID userId = UUID.randomUUID();
    UUID institutionId = UUID.randomUUID();
    var principal = institutionalPrincipal(userId, institutionId);
    User user = userWith(userId, institutionId, "12345678", "Ana", "Garcia", true);

    when(userRepository.findWithPersonAndInstitutionById(userId)).thenReturn(Optional.of(user));
    when(authorityResolver.resolvePersonAuthorities(any(UUID.class), eq(institutionId)))
        .thenReturn(
            new InstitutionalAuthoritySnapshot(
                Set.of(PermissionCode.INSTITUTION_PERSON_READ_ANY), List.of("Preceptor")));

    UserResponse response = getCurrentUserUseCase.execute(principal);

    assertThat(response.user().userId()).isEqualTo(userId);
    assertThat(response.user().documentNumber()).isEqualTo("12345678");
    assertThat(response.user().name()).isEqualTo("Ana");
    assertThat(response.user().lastName()).isEqualTo("Garcia");
    assertThat(response.user().institutionId()).isEqualTo(institutionId);
    assertThat(response.user().permissions())
        .containsExactly(PermissionCode.INSTITUTION_PERSON_READ_ANY.getCode());
    assertThat(response.user().roles()).containsExactly("Preceptor");
  }

  @Test
  @DisplayName("Should throw InvalidCredentialsException when user is not found")
  void execute_throwsWhenUserNotFound() {
    UUID userId = UUID.randomUUID();
    var principal = institutionalPrincipal(userId, UUID.randomUUID());

    when(userRepository.findWithPersonAndInstitutionById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> getCurrentUserUseCase.execute(principal))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  @DisplayName("Should throw DisabledException when user is disabled")
  void execute_throwsWhenUserIsDisabled() {
    UUID userId = UUID.randomUUID();
    UUID institutionId = UUID.randomUUID();
    var principal = institutionalPrincipal(userId, institutionId);
    User disabledUser = userWith(userId, institutionId, "12345678", "Ana", "Garcia", false);

    when(userRepository.findWithPersonAndInstitutionById(userId))
        .thenReturn(Optional.of(disabledUser));

    assertThatThrownBy(() -> getCurrentUserUseCase.execute(principal))
        .isInstanceOf(DisabledException.class);
  }

  private static User userWith(
      UUID userId,
      UUID institutionId,
      String document,
      String firstName,
      String lastName,
      boolean enabled) {
    return User.builder()
        .id(userId)
        .institution(Institution.builder().id(institutionId).build())
        .person(
            Person.builder()
                .documentNumber(document)
                .firstName(firstName)
                .lastName(lastName)
                .build())
        .password("encoded-hash")
        .enabled(enabled)
        .build();
  }
}
