package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorCategory;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.CannotGuardianSelfException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.DependentAlreadyLinkedException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.DependentBirthDateMismatchException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.CreateGuardianDependentRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianDependentResponse;
import jakarta.validation.Validator;
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
  @Mock private PersonGuardianRepository personGuardianRepository;
  @Mock private Validator validator;
  @Mock private AuditEventRecorder auditEventRecorder;

  private RegisterGuardianDependentUseCase useCase;
  private Institution institution;
  private Person tutor;

  @BeforeEach
  void setUp() {
    useCase =
        new RegisterGuardianDependentUseCase(
            institutionRepository,
            personRepository,
            personGuardianRepository,
            validator,
            auditEventRecorder);
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
  @DisplayName("Should create the person without email and request a pending link to the tutor")
  void execute_createsNewPersonAndPendingLink() {
    when(personRepository.findByDocumentNumberAndInstitution_Id("54123456", institution.getId()))
        .thenReturn(Optional.empty());
    when(personRepository.save(any(Person.class)))
        .thenAnswer(invocation -> withGeneratedId(invocation.getArgument(0)));

    final GuardianDependentResponse response =
        useCase.execute(institution.getId(), tutor.getId(), request("54123456", BIRTH_DATE));

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
    assertThat(linkCaptor.getValue().getStatus()).isEqualTo(GuardianLinkStatus.PENDING);
    assertThat(response.status()).isEqualTo(GuardianLinkStatus.PENDING);
    assertThat(response.documentNumber()).isEqualTo("54123456");
    assertThat(response.relationship()).isEqualTo(GuardianRelationship.FATHER);
    verify(auditEventRecorder)
        .record(
            institution,
            tutor.getId(),
            response.dependentPersonId(),
            AuditAction.GUARDIAN_LINK_REQUESTED,
            AuditEntityType.PERSON_GUARDIAN,
            response.personGuardianId());
  }

  @Test
  @DisplayName("Should accept an adult as the person in charge")
  void execute_acceptsAdultPerson() {
    when(personRepository.findByDocumentNumberAndInstitution_Id("54123456", institution.getId()))
        .thenReturn(Optional.empty());
    when(personRepository.save(any(Person.class)))
        .thenAnswer(invocation -> withGeneratedId(invocation.getArgument(0)));

    final GuardianDependentResponse response =
        useCase.execute(
            institution.getId(),
            tutor.getId(),
            request("54123456", LocalDate.now().minusYears(40)));

    assertThat(response.status()).isEqualTo(GuardianLinkStatus.PENDING);
  }

  @Test
  @DisplayName("Should request a link to an existing person, even one with an account")
  void execute_requestsLinkToExistingPerson() {
    final Person existing = existingPerson("54123456", BIRTH_DATE);
    when(personRepository.findByDocumentNumberAndInstitution_Id("54123456", institution.getId()))
        .thenReturn(Optional.of(existing));
    when(personGuardianRepository
            .existsByInstitution_IdAndTutorPerson_IdAndDependentPerson_IdAndStatusIn(
                institution.getId(),
                tutor.getId(),
                existing.getId(),
                List.of(GuardianLinkStatus.PENDING, GuardianLinkStatus.ACTIVE)))
        .thenReturn(false);

    final GuardianDependentResponse response =
        useCase.execute(institution.getId(), tutor.getId(), request("54123456", BIRTH_DATE));

    verify(personRepository, never()).save(any(Person.class));
    assertThat(response.dependentPersonId()).isEqualTo(existing.getId());
    assertThat(response.status()).isEqualTo(GuardianLinkStatus.PENDING);
  }

  @Test
  @DisplayName("Should not expose the stored data of the person while the link is pending")
  void execute_hidesPersonDataWhilePending() {
    final Person existing = existingPerson("54123456", BIRTH_DATE);
    when(personRepository.findByDocumentNumberAndInstitution_Id("54123456", institution.getId()))
        .thenReturn(Optional.of(existing));

    final GuardianDependentResponse response =
        useCase.execute(institution.getId(), tutor.getId(), request("54123456", BIRTH_DATE));

    assertThat(response.firstName()).isNull();
    assertThat(response.lastName()).isNull();
    assertThat(response.roles()).isEmpty();
    assertThat(response.activeApplicationsCount()).isZero();
  }

  @Test
  @DisplayName("Should not link an existing person whose birth date does not match")
  void execute_rejectsExistingPersonWithDifferentBirthDate() {
    final Person existing = existingPerson("54123456", LocalDate.of(2015, 1, 1));
    when(personRepository.findByDocumentNumberAndInstitution_Id("54123456", institution.getId()))
        .thenReturn(Optional.of(existing));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    institution.getId(), tutor.getId(), request("54123456", BIRTH_DATE)))
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
            () ->
                useCase.execute(
                    institution.getId(), tutor.getId(), request("35123456", BIRTH_DATE)))
        .isInstanceOfSatisfying(
            CannotGuardianSelfException.class,
            exception -> {
              assertThat(exception.category()).isEqualTo(ErrorCategory.INVALID_INPUT);
              assertThat(exception.code()).isEqualTo("CANNOT_GUARDIAN_SELF");
            });
  }

  @Test
  @DisplayName("Should reject a request when one is already pending or active")
  void execute_rejectsAlreadyOpenLink() {
    final Person existing = existingPerson("54123456", BIRTH_DATE);
    when(personRepository.findByDocumentNumberAndInstitution_Id("54123456", institution.getId()))
        .thenReturn(Optional.of(existing));
    when(personGuardianRepository
            .existsByInstitution_IdAndTutorPerson_IdAndDependentPerson_IdAndStatusIn(
                institution.getId(),
                tutor.getId(),
                existing.getId(),
                List.of(GuardianLinkStatus.PENDING, GuardianLinkStatus.ACTIVE)))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    institution.getId(), tutor.getId(), request("54123456", BIRTH_DATE)))
        .isInstanceOfSatisfying(
            DependentAlreadyLinkedException.class,
            exception -> assertThat(exception.code()).isEqualTo("DEPENDENT_ALREADY_LINKED"));
  }

  @Test
  @DisplayName("Should translate a concurrent duplicated request into a conflict")
  void execute_translatesConcurrentDuplicate() {
    final Person existing = existingPerson("54123456", BIRTH_DATE);
    when(personRepository.findByDocumentNumberAndInstitution_Id("54123456", institution.getId()))
        .thenReturn(Optional.of(existing));
    doThrow(new DataIntegrityViolationException("person_guardians_open_unique"))
        .when(personGuardianRepository)
        .flush();

    assertThatThrownBy(
            () ->
                useCase.execute(
                    institution.getId(), tutor.getId(), request("54123456", BIRTH_DATE)))
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

  private CreateGuardianDependentRequest request(
      final String documentNumber, final LocalDate birthDate) {
    return new CreateGuardianDependentRequest(
        documentNumber, "Mateo", "Gonzalez", birthDate, GuardianRelationship.FATHER, true);
  }
}
