package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.GuardianMustBeAdultException;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.UserAlreadyExistsException;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.RegisterRequest;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.UserRegisteredResponse;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AssignPersonSystemRoleUseCase;
import ar.edu.utn.frvm.typeit.boero_api.common.time.BusinessDateProvider;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.City;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.DependentBirthDateMismatchException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionInactiveException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.Clock;
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
            assignPersonSystemRoleUseCase,
            new BusinessDateProvider(Clock.systemUTC()));
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
            institutionId,
            null);

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
    assertThat(personCaptor.getValue().getBirthDate()).isEqualTo(LocalDate.of(2010, 1, 1));
  }

  @Test
  @DisplayName("Should assign APPLICANT role after registering a person")
  void execute_assignsApplicantRole() {
    UUID institutionId = UUID.randomUUID();
    RegisterRequest request =
        new RegisterRequest(
            "Ana",
            "Garcia",
            LocalDate.of(2010, 1, 1),
            "12345678",
            "ana@example.com",
            "password123",
            institutionId,
            null);

    stubSuccessfulRegistration(institutionId, "password123", "encoded-hash");

    registerUserUseCase.execute(request);

    var personCaptor = ArgumentCaptor.forClass(Person.class);
    verify(assignPersonSystemRoleUseCase)
        .execute(personCaptor.capture(), eq(SystemRoleCode.APPLICANT), eq(false));
  }

  @Test
  @DisplayName("Should assign GUARDIAN instead of APPLICANT when registering as a guardian")
  void execute_assignsGuardianRoleWhenRegisteringAsGuardian() {
    UUID institutionId = UUID.randomUUID();
    RegisterRequest request =
        new RegisterRequest(
            "Carlos",
            "Gonzalez",
            LocalDate.of(1988, 5, 14),
            "12345678",
            "carlos@example.com",
            "password123",
            institutionId,
            true);

    stubSuccessfulRegistration(institutionId, "password123", "encoded-hash");

    registerUserUseCase.execute(request);

    verify(assignPersonSystemRoleUseCase)
        .execute(any(Person.class), eq(SystemRoleCode.GUARDIAN), eq(false));
    verify(assignPersonSystemRoleUseCase, never())
        .execute(any(Person.class), eq(SystemRoleCode.APPLICANT), eq(false));
  }

  @Test
  @DisplayName("Should reject registering as a guardian while under age")
  void execute_rejectsUnderageGuardian() {
    UUID institutionId = UUID.randomUUID();
    RegisterRequest request =
        new RegisterRequest(
            "Ana",
            "Garcia",
            LocalDate.now().minusYears(17),
            "12345678",
            "ana@example.com",
            "password123",
            institutionId,
            true);

    assertThatThrownBy(() -> registerUserUseCase.execute(request))
        .isInstanceOfSatisfying(
            GuardianMustBeAdultException.class,
            exception -> assertThat(exception.fieldErrors()).containsKey("birthDate"));
    verify(personRepository, never()).save(any(Person.class));
  }

  @Test
  @DisplayName("Should keep APPLICANT when isGuardian is explicitly false")
  void execute_assignsApplicantRoleWhenIsGuardianIsFalse() {
    UUID institutionId = UUID.randomUUID();
    RegisterRequest request =
        new RegisterRequest(
            "Ana",
            "Garcia",
            LocalDate.of(2000, 1, 1),
            "12345678",
            "ana@example.com",
            "password123",
            institutionId,
            false);

    stubSuccessfulRegistration(institutionId, "password123", "encoded-hash");

    registerUserUseCase.execute(request);

    verify(assignPersonSystemRoleUseCase)
        .execute(any(Person.class), eq(SystemRoleCode.APPLICANT), eq(false));
  }

  @Test
  @DisplayName("Should encode the password before saving the user")
  void execute_encodesPasswordBeforeSaving() {
    UUID institutionId = UUID.randomUUID();
    RegisterRequest request =
        new RegisterRequest(
            "Ana",
            "Garcia",
            LocalDate.of(2010, 1, 1),
            "12345678",
            "ana@example.com",
            "plaintext",
            institutionId,
            null);

    stubSuccessfulRegistration(institutionId, "plaintext", "bcrypt-hash");

    registerUserUseCase.execute(request);

    var userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    User savedUser = userCaptor.getValue();
    assertThat(savedUser.getPassword()).isEqualTo("bcrypt-hash");
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
            institutionId,
            null);

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
            institutionId,
            null);
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
            institutionId,
            null);
    Institution institution = institutionWith(institutionId);

    when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(institution));
    Person existingPerson =
        Person.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .firstName("Ana")
            .lastName("Garcia")
            .birthDate(LocalDate.of(2010, 1, 1))
            .documentNumber("12345678")
            .email("old@example.com")
            .build();
    when(personRepository.findByDocumentNumberAndInstitutionIdForUpdate("12345678", institutionId))
        .thenReturn(Optional.of(existingPerson));
    when(userRepository.findByPerson_IdAndInstitution_Id(existingPerson.getId(), institutionId))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(User.class)));

    assertThatThrownBy(() -> registerUserUseCase.execute(request))
        .isInstanceOf(UserAlreadyExistsException.class);

    verify(personRepository, never()).save(any());
    verify(userRepository, never()).save(any());
    verify(assignPersonSystemRoleUseCase, never()).execute(any(), any());
  }

  @Test
  @DisplayName("Should complete registration for an existing person without an account")
  void execute_registersExistingPersonAndPreservesRoles() {
    UUID institutionId = UUID.randomUUID();
    RegisterRequest request =
        new RegisterRequest(
            "Ana",
            "Garcia",
            LocalDate.of(2010, 1, 1),
            "12345678",
            "new@example.com",
            "password123",
            institutionId,
            null);
    Institution institution = institutionWith(institutionId);
    Person existingPerson =
        Person.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .firstName("Ana")
            .lastName("Garcia")
            .birthDate(request.birthDate())
            .documentNumber(request.documentNumber())
            .email("old@example.com")
            .build();
    User user =
        User.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .person(existingPerson)
            .build();

    when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(institution));
    when(personRepository.findByDocumentNumberAndInstitutionIdForUpdate(
            request.documentNumber(), institutionId))
        .thenReturn(Optional.of(existingPerson));
    when(userRepository.findByPerson_IdAndInstitution_Id(existingPerson.getId(), institutionId))
        .thenReturn(Optional.empty());
    when(passwordEncoder.encode(request.password())).thenReturn("encoded-hash");
    when(userRepository.save(any(User.class))).thenReturn(user);
    when(validator.validate(any(Person.class))).thenReturn(Set.of());

    UserRegisteredResponse response = registerUserUseCase.execute(request);

    assertThat(response.userId()).isEqualTo(user.getId());
    assertThat(existingPerson.getEmail()).isEqualTo(request.email());
    verify(assignPersonSystemRoleUseCase)
        .executePreservingRoles(existingPerson, SystemRoleCode.APPLICANT);
    verify(emailVerification).sendInitial(user);
    verify(personRepository).flush();
  }

  @Test
  @DisplayName("Should reject existing person registration when birth date differs")
  void execute_rejectsExistingPersonWithDifferentBirthDate() {
    UUID institutionId = UUID.randomUUID();
    RegisterRequest request =
        new RegisterRequest(
            "Ana",
            "Garcia",
            LocalDate.of(2011, 1, 1),
            "12345678",
            "new@example.com",
            "password123",
            institutionId,
            null);
    Institution institution = institutionWith(institutionId);
    Person existingPerson =
        Person.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .firstName("Ana")
            .lastName("Garcia")
            .birthDate(LocalDate.of(2010, 1, 1))
            .documentNumber(request.documentNumber())
            .email("old@example.com")
            .build();

    when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(institution));
    when(personRepository.findByDocumentNumberAndInstitutionIdForUpdate(
            request.documentNumber(), institutionId))
        .thenReturn(Optional.of(existingPerson));
    when(userRepository.findByPerson_IdAndInstitution_Id(existingPerson.getId(), institutionId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> registerUserUseCase.execute(request))
        .isInstanceOf(DependentBirthDateMismatchException.class);

    assertThat(existingPerson.getEmail()).isEqualTo("old@example.com");
    verify(userRepository, never()).save(any());
    verify(assignPersonSystemRoleUseCase, never()).executePreservingRoles(any(), any());
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
            institutionId,
            null);
    Institution institution = institutionWith(institutionId);
    ConstraintViolation<Person> violation =
        (ConstraintViolation<Person>) org.mockito.Mockito.mock(ConstraintViolation.class);

    when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(institution));
    when(personRepository.findByDocumentNumberAndInstitutionIdForUpdate("12345678", institutionId))
        .thenReturn(Optional.empty());
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
    when(personRepository.findByDocumentNumberAndInstitutionIdForUpdate("12345678", institutionId))
        .thenReturn(Optional.empty());
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
