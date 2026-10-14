package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.UserAlreadyExistsException;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.RegisterRequest;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.UserRegisteredResponse;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AssignPersonSystemRoleUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.City;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionInactiveException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {
  private static final UUID PERSON_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final UUID USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  @Mock private InstitutionalHostContext hostContext;

  @Mock private UserRepository userRepository;
  @Mock private InstitutionRepository institutionRepository;
  @Mock private PersonRepository personRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private Validator validator;
  @Mock private AssignPersonSystemRoleUseCase assignPersonSystemRoleUseCase;

  @Mock private InstitutionalEmailVerificationUseCase emailVerification;
  private RegisterUserUseCase registerUserUseCase;

  @BeforeEach
  void setUp() {
    registerUserUseCase =
        new RegisterUserUseCase(
            hostContext,
            emailVerification,
            userRepository,
            institutionRepository,
            personRepository,
            passwordEncoder,
            validator,
            assignPersonSystemRoleUseCase);
  }

  @Test
  @DisplayName("Should register a pending user with an encoded password and applicant role")
  void execute_registersUserSuccessfully() {
    UUID institutionId = UUID.randomUUID();
    RegisterRequest request =
        new RegisterRequest(
            "Ana",
            "Garcia",
            LocalDate.of(2010, 1, 1),
            "12345678",
            "ana@example.com",
            "password123",
            institutionId);

    stubSuccessfulRegistration(institutionId, "password123", "encoded-hash");

    UserRegisteredResponse response = registerUserUseCase.execute(request);

    assertThat(response.emailVerificationRequired()).isTrue();
    verify(emailVerification)
        .sendInitial(
            org.mockito.ArgumentMatchers.argThat(
                mappedUser -> mappedUser.requiresEmailVerification()));
    assertThat(response.userId()).isEqualTo(USER_ID);
    assertThat(response.documentNumber()).isEqualTo("12345678");
    assertThat(response.institutionId()).isEqualTo(institutionId);

    var personCaptor = ArgumentCaptor.forClass(Person.class);
    verify(personRepository).save(personCaptor.capture());
    Person savedPerson = personCaptor.getValue();
    assertThat(savedPerson.getFirstName()).isEqualTo("Ana");
    assertThat(savedPerson.getLastName()).isEqualTo("Garcia");
    assertThat(savedPerson.getBirthDate()).isEqualTo(LocalDate.of(2010, 1, 1));
    assertThat(savedPerson.getEmail()).isEqualTo("ana@example.com");
    assertThat(savedPerson.getDocumentNumber()).isEqualTo("12345678");
    assertThat(savedPerson.getInstitution().getId()).isEqualTo(institutionId);

    var userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    User savedUser = userCaptor.getValue();
    assertThat(savedUser.getPassword()).isEqualTo("encoded-hash");
    assertThat(savedUser.requiresEmailVerification()).isTrue();
    assertThat(savedUser.getPerson().getId()).isEqualTo(PERSON_ID);
    assertThat(savedUser.getInstitutionId()).isEqualTo(institutionId);
    verify(assignPersonSystemRoleUseCase)
        .execute(savedUser.getPerson(), SystemRoleCode.APPLICANT, false);
  }

  @Test
  @DisplayName("Should throw when the institution does not exist")
  void execute_throwsWhenInstitutionNotFound() {
    UUID institutionId = UUID.randomUUID();
    RegisterRequest request =
        new RegisterRequest(
            "Ana",
            "Garcia",
            LocalDate.of(2010, 1, 1),
            "12345678",
            "ana@example.com",
            "password123",
            institutionId);

    when(institutionRepository.findById(institutionId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> registerUserUseCase.execute(request))
        .isInstanceOf(InstitutionNotFoundException.class);

    verify(personRepository, never()).save(any());
    verify(userRepository, never()).save(any());
    verify(assignPersonSystemRoleUseCase, never()).execute(any(), any());
  }

  @Test
  @DisplayName("Should reject registration for an inactive institution")
  void execute_throwsWhenInstitutionIsInactive() {
    final UUID institutionId = UUID.randomUUID();
    final RegisterRequest request =
        new RegisterRequest(
            "Ana",
            "Garcia",
            LocalDate.of(2010, 1, 1),
            "12345678",
            "ana@example.com",
            "password123",
            institutionId);
    final Institution institution = institutionWith(institutionId);
    institution.updateStatus(false);
    when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(institution));

    assertThatThrownBy(() -> registerUserUseCase.execute(request))
        .isInstanceOf(InstitutionInactiveException.class);

    verify(personRepository, never()).save(any());
    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName(
      "Should throw when a person with the same document already exists in the institution")
  void execute_throwsWhenPersonAlreadyExistsInInstitution() {
    UUID institutionId = UUID.randomUUID();
    RegisterRequest request =
        new RegisterRequest(
            "Ana",
            "Garcia",
            LocalDate.of(2010, 1, 1),
            "12345678",
            "ana@example.com",
            "password123",
            institutionId);
    Institution institution = institutionWith(institutionId);

    when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(institution));
    when(personRepository.existsByDocumentNumberAndInstitution_Id("12345678", institutionId))
        .thenReturn(true);

    assertThatThrownBy(() -> registerUserUseCase.execute(request))
        .isInstanceOf(UserAlreadyExistsException.class);

    verify(personRepository, never()).save(any());
    verify(userRepository, never()).save(any());
    verify(assignPersonSystemRoleUseCase, never()).execute(any(), any());
  }

  @Test
  @DisplayName("Should throw when the person entity fails Bean Validation")
  @SuppressWarnings("unchecked")
  void execute_throwsWhenPersonValidationFails() {
    UUID institutionId = UUID.randomUUID();
    RegisterRequest request =
        new RegisterRequest(
            "Ana",
            "Garcia",
            LocalDate.of(2010, 1, 1),
            "12345678",
            "ana@example.com",
            "password123",
            institutionId);
    Institution institution = institutionWith(institutionId);
    ConstraintViolation<Person> violation =
        (ConstraintViolation<Person>) org.mockito.Mockito.mock(ConstraintViolation.class);

    when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(institution));
    when(personRepository.existsByDocumentNumberAndInstitution_Id("12345678", institutionId))
        .thenReturn(false);
    when(validator.validate(any(Person.class))).thenReturn(Set.of(violation));

    assertThatThrownBy(() -> registerUserUseCase.execute(request))
        .isInstanceOf(ConstraintViolationException.class);

    verify(personRepository, never()).save(any());
    verify(userRepository, never()).save(any());
    verify(assignPersonSystemRoleUseCase, never()).execute(any(), any());
  }

  private void stubSuccessfulRegistration(
      UUID institutionId, String password, String encodedPassword) {
    Institution institution = institutionWith(institutionId);
    when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(institution));
    when(personRepository.existsByDocumentNumberAndInstitution_Id("12345678", institutionId))
        .thenReturn(false);
    when(validator.validate(any(Person.class))).thenReturn(Set.of());
    when(passwordEncoder.encode(password)).thenReturn(encodedPassword);
    when(personRepository.save(any(Person.class)))
        .thenAnswer(invocation -> persistedPerson(invocation.getArgument(0)));
    when(userRepository.save(any(User.class)))
        .thenAnswer(invocation -> persistedUser(invocation.getArgument(0)));
  }

  private static Person persistedPerson(final Person person) {
    return Person.builder()
        .id(PERSON_ID)
        .institution(person.getInstitution())
        .documentNumber(person.getDocumentNumber())
        .firstName(person.getFirstName())
        .lastName(person.getLastName())
        .birthDate(person.getBirthDate())
        .email(person.getEmail())
        .build();
  }

  private static User persistedUser(final User user) {
    return User.builder()
        .id(USER_ID)
        .institution(user.getInstitution())
        .person(user.getPerson())
        .password(user.getPassword())
        .emailVerificationStatus(user.getEmailVerificationStatus())
        .enabled(user.isAccessEnabled())
        .build();
  }

  private static Institution institutionWith(UUID id) {
    return Institution.builder()
        .id(id)
        .name("Conservatorio Boero")
        .slug("boero")
        .city(City.builder().build())
        .build();
  }
}
