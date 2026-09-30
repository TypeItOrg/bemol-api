package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AssignPersonSystemRoleUseCase;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorCategory;
import ar.edu.utn.frvm.typeit.boero_api.common.time.BusinessDateProvider;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.CannotGuardianSelfException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.DependentAlreadyLinkedException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.DependentBirthDateMismatchException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.DependentHasAccountException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.DependentMustBeMinorException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.CreateGuardianDependentRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianDependentResponse;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class RegisterGuardianDependentUseCaseTest {

  private static final LocalDate BIRTH_DATE = LocalDate.of(2018, 9, 10);

  @Mock private InstitutionRepository institutionRepository;
  @Mock private PersonRepository personRepository;
  @Mock private UserRepository userRepository;
  @Mock private PersonGuardianRepository personGuardianRepository;
  @Mock private Validator validator;
  @Mock private AssignPersonSystemRoleUseCase assignPersonSystemRoleUseCase;
  @Mock private PersonRoleAssignmentRepository personRoleAssignmentRepository;

  private RegisterGuardianDependentUseCase useCase;
  private Institution institution;
  private Person tutor;

  @BeforeEach
  void setUp() {
    useCase =
        new RegisterGuardianDependentUseCase(
            institutionRepository,
            personRepository,
            userRepository,
            personGuardianRepository,
            validator,
            new BusinessDateProvider(Clock.systemUTC()),
            assignPersonSystemRoleUseCase,
            personRoleAssignmentRepository);
    institution = Institution.builder().id(UUID.randomUUID()).build();
    tutor =
        Person.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .documentNumber("35123456")
            .build();

    lenient()
        .when(institutionRepository.findById(institution.getId()))
        .thenReturn(Optional.of(institution));
    lenient()
        .when(personRepository.findByIdAndInstitution_Id(tutor.getId(), institution.getId()))
        .thenReturn(Optional.of(tutor));
    lenient()
        .when(personGuardianRepository.save(any(PersonGuardian.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  @DisplayName("Should create the minor without email and link it to the tutor")
  void execute_createsNewPersonAndLink() {
    when(personRepository.findByDocumentNumberAndInstitution_Id("54123456", institution.getId()))
        .thenReturn(Optional.empty());
    when(personRepository.save(any(Person.class)))
        .thenAnswer(invocation -> withGeneratedId(invocation.getArgument(0)));

    final GuardianDependentResponse response =
        useCase.execute(institution.getId(), tutor.getId(), request("54123456"));

    final var personCaptor = ArgumentCaptor.forClass(Person.class);
    verify(personRepository).save(personCaptor.capture());
    assertThat(personCaptor.getValue().getEmail()).isNull();
    assertThat(personCaptor.getValue().getBirthDate()).isEqualTo(BIRTH_DATE);
    assertThat(personCaptor.getValue().getInstitution()).isSameAs(institution);

    final var linkCaptor = ArgumentCaptor.forClass(PersonGuardian.class);
    verify(personGuardianRepository).save(linkCaptor.capture());
    assertThat(linkCaptor.getValue().getTutorPerson()).isSameAs(tutor);
    assertThat(linkCaptor.getValue().getRelationship()).isEqualTo(GuardianRelationship.FATHER);
    assertThat(linkCaptor.getValue().isPrimaryContact()).isTrue();
    assertThat(response.documentNumber()).isEqualTo("54123456");
    assertThat(response.relationship()).isEqualTo(GuardianRelationship.FATHER);
    final var assignedCaptor = ArgumentCaptor.forClass(Person.class);
    verify(assignPersonSystemRoleUseCase)
        .execute(assignedCaptor.capture(), eq(SystemRoleCode.APPLICANT), eq(false));
    assertThat(assignedCaptor.getValue().getId()).isEqualTo(response.dependentPersonId());
  }

  @Test
  @DisplayName("Should reject a dependent who is already an adult")
  void execute_rejectsAdultDependent() {
    final var adult =
        new CreateGuardianDependentRequest(
            "54123456",
            "Mateo",
            "Gonzalez",
            LocalDate.now().minusYears(18),
            GuardianRelationship.FATHER,
            true);

    assertThatThrownBy(() -> useCase.execute(institution.getId(), tutor.getId(), adult))
        .isInstanceOfSatisfying(
            DependentMustBeMinorException.class,
            exception -> assertThat(exception.category()).isEqualTo(ErrorCategory.INVALID_INPUT));
    verify(personRepository, never()).save(any(Person.class));
    verify(personGuardianRepository, never()).save(any(PersonGuardian.class));
    verify(assignPersonSystemRoleUseCase, never())
        .execute(any(Person.class), any(SystemRoleCode.class), anyBoolean());
  }

  @Test
  @DisplayName("Should link an existing person that has no account and the same birth date")
  void execute_linksExistingPersonWithoutAccount() {
    final Person existing = existingPerson("54123456", BIRTH_DATE);
    when(personRepository.findByDocumentNumberAndInstitution_Id("54123456", institution.getId()))
        .thenReturn(Optional.of(existing));
    when(userRepository.findByPerson_IdAndInstitution_Id(existing.getId(), institution.getId()))
        .thenReturn(Optional.empty());
    when(personGuardianRepository.existsByInstitution_IdAndTutorPerson_IdAndDependentPerson_Id(
            institution.getId(), tutor.getId(), existing.getId()))
        .thenReturn(false);

    final GuardianDependentResponse response =
        useCase.execute(institution.getId(), tutor.getId(), request("54123456"));

    verify(personRepository, never()).save(any(Person.class));
    assertThat(response.dependentPersonId()).isEqualTo(existing.getId());
    verify(assignPersonSystemRoleUseCase).execute(existing, SystemRoleCode.APPLICANT, false);
  }

  @Test
  @DisplayName("Should keep the roles of an existing person that already has one")
  void execute_doesNotReplaceExistingRoles() {
    final Person existing = existingPerson("54123456", BIRTH_DATE);
    when(personRepository.findByDocumentNumberAndInstitution_Id("54123456", institution.getId()))
        .thenReturn(Optional.of(existing));
    when(userRepository.findByPerson_IdAndInstitution_Id(existing.getId(), institution.getId()))
        .thenReturn(Optional.empty());
    when(personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(
            existing.getId(), institution.getId()))
        .thenReturn(
            List.of(
                PersonRoleAssignment.builder()
                    .role(Role.builder().name("Estudiante").build())
                    .build()));

    final GuardianDependentResponse response =
        useCase.execute(institution.getId(), tutor.getId(), request("54123456"));

    assertThat(response.roles()).containsExactly("Estudiante");
    verify(assignPersonSystemRoleUseCase, never())
        .execute(any(Person.class), any(SystemRoleCode.class), anyBoolean());
  }

  @Test
  @DisplayName("Should not link an existing person that owns an account")
  void execute_rejectsExistingPersonWithAccount() {
    final Person existing = existingPerson("54123456", BIRTH_DATE);
    when(personRepository.findByDocumentNumberAndInstitution_Id("54123456", institution.getId()))
        .thenReturn(Optional.of(existing));
    when(userRepository.findByPerson_IdAndInstitution_Id(existing.getId(), institution.getId()))
        .thenReturn(Optional.of(User.builder().build()));

    assertThatThrownBy(
            () -> useCase.execute(institution.getId(), tutor.getId(), request("54123456")))
        .isInstanceOfSatisfying(
            DependentHasAccountException.class,
            exception -> {
              assertThat(exception.category()).isEqualTo(ErrorCategory.CONFLICT);
              assertThat(exception.code()).isEqualTo("DEPENDENT_HAS_ACCOUNT");
            });
    verify(personGuardianRepository, never()).save(any(PersonGuardian.class));
  }

  @Test
  @DisplayName("Should not link an existing person whose birth date does not match")
  void execute_rejectsExistingPersonWithDifferentBirthDate() {
    final Person existing = existingPerson("54123456", LocalDate.of(2015, 1, 1));
    when(personRepository.findByDocumentNumberAndInstitution_Id("54123456", institution.getId()))
        .thenReturn(Optional.of(existing));
    when(userRepository.findByPerson_IdAndInstitution_Id(existing.getId(), institution.getId()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> useCase.execute(institution.getId(), tutor.getId(), request("54123456")))
        .isInstanceOfSatisfying(
            DependentBirthDateMismatchException.class,
            exception -> {
              assertThat(exception.category()).isEqualTo(ErrorCategory.INVALID_INPUT);
              assertThat(exception.code()).isEqualTo("DEPENDENT_BIRTH_DATE_MISMATCH");
            });
    verify(personGuardianRepository, never()).save(any(PersonGuardian.class));
  }

  @Test
  @DisplayName("Should reject a tutor linking themselves as dependent")
  void execute_rejectsSelfGuardianship() {
    when(personRepository.findByDocumentNumberAndInstitution_Id("35123456", institution.getId()))
        .thenReturn(Optional.of(tutor));

    assertThatThrownBy(
            () -> useCase.execute(institution.getId(), tutor.getId(), request("35123456")))
        .isInstanceOfSatisfying(
            CannotGuardianSelfException.class,
            exception -> {
              assertThat(exception.category()).isEqualTo(ErrorCategory.INVALID_INPUT);
              assertThat(exception.code()).isEqualTo("CANNOT_GUARDIAN_SELF");
            });
  }

  @Test
  @DisplayName("Should reject linking the same dependent twice")
  void execute_rejectsAlreadyLinkedDependent() {
    final Person existing = existingPerson("54123456", BIRTH_DATE);
    when(personRepository.findByDocumentNumberAndInstitution_Id("54123456", institution.getId()))
        .thenReturn(Optional.of(existing));
    when(personGuardianRepository.existsByInstitution_IdAndTutorPerson_IdAndDependentPerson_Id(
            institution.getId(), tutor.getId(), existing.getId()))
        .thenReturn(true);

    assertThatThrownBy(
            () -> useCase.execute(institution.getId(), tutor.getId(), request("54123456")))
        .isInstanceOfSatisfying(
            DependentAlreadyLinkedException.class,
            exception -> assertThat(exception.code()).isEqualTo("DEPENDENT_ALREADY_LINKED"));
  }

  @Test
  @DisplayName("Should translate a concurrent duplicated link into a conflict")
  void execute_translatesConcurrentDuplicate() {
    final Person existing = existingPerson("54123456", BIRTH_DATE);
    when(personRepository.findByDocumentNumberAndInstitution_Id("54123456", institution.getId()))
        .thenReturn(Optional.of(existing));
    when(userRepository.findByPerson_IdAndInstitution_Id(existing.getId(), institution.getId()))
        .thenReturn(Optional.empty());
    doThrow(new DataIntegrityViolationException("person_guardians_unique"))
        .when(personGuardianRepository)
        .flush();

    assertThatThrownBy(
            () -> useCase.execute(institution.getId(), tutor.getId(), request("54123456")))
        .isInstanceOf(DependentAlreadyLinkedException.class);
  }

  // JPA assigns the id when persisting; emulate it since the repository is mocked.
  private Person withGeneratedId(final Person person) {
    return Person.builder()
        .id(UUID.randomUUID())
        .institution(person.getInstitution())
        .firstName(person.getFirstName())
        .lastName(person.getLastName())
        .documentNumber(person.getDocumentNumber())
        .birthDate(person.getBirthDate())
        .build();
  }

  private Person existingPerson(final String documentNumber, final LocalDate birthDate) {
    return Person.builder()
        .id(UUID.randomUUID())
        .institution(institution)
        .documentNumber(documentNumber)
        .firstName("Mateo")
        .lastName("Gonzalez")
        .birthDate(birthDate)
        .build();
  }

  private CreateGuardianDependentRequest request(final String documentNumber) {
    return new CreateGuardianDependentRequest(
        documentNumber, "Mateo", "Gonzalez", BIRTH_DATE, GuardianRelationship.FATHER, true);
  }
}
