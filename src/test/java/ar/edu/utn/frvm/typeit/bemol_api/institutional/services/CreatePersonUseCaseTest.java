package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.RoleNotAssignableException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AssignPersonSystemRoleUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InitialRoleAssignmentGuard;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonAlreadyExistsException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.CreatePersonRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.PersonResponse;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CreatePersonUseCaseTest {

  @Mock private InstitutionRepository institutionRepository;
  @Mock private PersonRepository personRepository;

  @Mock
  private ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.CountryRepository
      countryRepository;

  @Mock private UserRepository userRepository;

  @Mock
  private ar.edu.utn.frvm.typeit.bemol_api.auth.services.InstitutionalAccountManagementUseCase
      accounts;

  @Mock private AssignPersonSystemRoleUseCase assignPersonSystemRoleUseCase;
  @Mock private InitialRoleAssignmentGuard initialRoleAssignmentGuard;

  @Spy private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Mock private AuditEventRecorder auditEventRecorder;

  @InjectMocks private CreatePersonUseCase createPersonUseCase;

  private UUID institutionId;
  private Institution institution;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.lenient()
        .when(countryRepository.findById(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            Optional.of(
                ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Country.builder()
                    .id(UUID.fromString("019e18e4-c93b-7583-90a6-0e9410ce3ad3"))
                    .name("Argentina")
                    .isoCode("ARG")
                    .build()));
    institutionId = UUID.randomUUID();
    institution = Institution.builder().id(institutionId).name("Conservatorio Boero").build();
  }

  @ParameterizedTest
  @EnumSource(
      value = SystemRoleCode.class,
      names = {"TEACHER", "REPRESENTATIVE"})
  @DisplayName(
      "Should create a person and invitation without a User with the selected initial role")
  void execute_createsPersonAndInvitationWithInitialRole(final SystemRoleCode role) {
    when(institutionRepository.findByIdForUpdate(institutionId))
        .thenReturn(Optional.of(institution));
    when(personRepository.existsByDocumentNumberAndInstitution_Id("12345678", institutionId))
        .thenReturn(false);
    when(personRepository.save(any(Person.class))).thenAnswer(inv -> inv.getArgument(0));

    PersonResponse response =
        createPersonUseCase.execute(
            institutionId,
            new CreatePersonRequest(
                "Ana",
                "García",
                "12345678",
                "ana@example.com",
                "0353-123456",
                LocalDate.of(1990, 1, 1),
                role,
                UUID.fromString("019e18e4-c93b-7583-90a6-0e9410ce3ad3")));

    assertThat(response.documentNumber()).isEqualTo("12345678");
    assertThat(response.firstName()).isEqualTo("Ana");
    verify(personRepository).save(any(Person.class));
    verify(userRepository, never()).save(any(User.class));
    verify(accounts).invite(eq(institutionId), any(), eq("ana@example.com"));
    verify(assignPersonSystemRoleUseCase).execute(any(Person.class), eq(role));
  }

  @Test
  @DisplayName("Should default to APPLICANT role when no initialRole provided")
  void execute_defaultsToApplicantRole() {
    when(institutionRepository.findByIdForUpdate(institutionId))
        .thenReturn(Optional.of(institution));
    when(personRepository.existsByDocumentNumberAndInstitution_Id("12345678", institutionId))
        .thenReturn(false);
    when(personRepository.save(any(Person.class))).thenAnswer(inv -> inv.getArgument(0));

    createPersonUseCase.execute(
        institutionId,
        new CreatePersonRequest(
            "Ana",
            "García",
            "12345678",
            "ana@example.com",
            "03534100000",
            java.time.LocalDate.of(2000, 1, 1),
            null,
            UUID.fromString("019e18e4-c93b-7583-90a6-0e9410ce3ad3")));

    verify(assignPersonSystemRoleUseCase).execute(any(Person.class), eq(SystemRoleCode.APPLICANT));
  }

  @Test
  @DisplayName("Should throw PersonAlreadyExistsException when document already exists")
  void execute_throwsWhenDocumentExists() {
    when(institutionRepository.findByIdForUpdate(institutionId))
        .thenReturn(Optional.of(institution));
    when(personRepository.existsByDocumentNumberAndInstitution_Id("12345678", institutionId))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                createPersonUseCase.execute(
                    institutionId,
                    new CreatePersonRequest(
                        "Ana",
                        "García",
                        "12345678",
                        "ana@example.com",
                        "03534100000",
                        java.time.LocalDate.of(2000, 1, 1),
                        SystemRoleCode.TEACHER,
                        UUID.fromString("019e18e4-c93b-7583-90a6-0e9410ce3ad3"))))
        .isInstanceOf(PersonAlreadyExistsException.class);

    verify(personRepository, never()).save(any());
    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("Should throw InstitutionNotFoundException when institution is missing")
  void execute_throwsWhenInstitutionMissing() {
    when(institutionRepository.findByIdForUpdate(institutionId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                createPersonUseCase.execute(
                    institutionId,
                    new CreatePersonRequest(
                        "Ana",
                        "García",
                        "12345678",
                        "ana@example.com",
                        "03534100000",
                        java.time.LocalDate.of(2000, 1, 1),
                        SystemRoleCode.TEACHER,
                        UUID.fromString("019e18e4-c93b-7583-90a6-0e9410ce3ad3"))))
        .isInstanceOf(InstitutionNotFoundException.class);
  }

  @Test
  @DisplayName("Should map a concurrent document constraint violation to conflict")
  void execute_mapsConcurrentDocumentConflict() {
    when(institutionRepository.findByIdForUpdate(institutionId))
        .thenReturn(Optional.of(institution));
    when(personRepository.existsByDocumentNumberAndInstitution_Id("12345678", institutionId))
        .thenReturn(false);
    when(personRepository.save(any(Person.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    doThrow(
            new DataIntegrityViolationException(
                "duplicate document",
                new org.hibernate.exception.ConstraintViolationException(
                    "duplicate",
                    new java.sql.SQLException("duplicate"),
                    "people_active_document_number_unique")))
        .when(personRepository)
        .flush();

    assertThatThrownBy(
            () ->
                createPersonUseCase.execute(
                    institutionId,
                    new CreatePersonRequest(
                        "Ana",
                        "García",
                        "12345678",
                        "ana@example.com",
                        "03534100000",
                        java.time.LocalDate.of(2000, 1, 1),
                        SystemRoleCode.TEACHER,
                        UUID.fromString("019e18e4-c93b-7583-90a6-0e9410ce3ad3"))))
        .isInstanceOf(PersonAlreadyExistsException.class);

    verify(userRepository, never()).save(any());
  }

  @Test
  void execute_rejectsUndelegableRoleBeforeCreatingPersonOrInvitation() {
    when(institutionRepository.findByIdForUpdate(institutionId))
        .thenReturn(Optional.of(institution));
    doThrow(new RoleNotAssignableException())
        .when(initialRoleAssignmentGuard)
        .checkDelegation(any(), eq(institutionId), eq(SystemRoleCode.INSTITUTIONAL_AUTHORITY));

    assertThatThrownBy(
            () ->
                createPersonUseCase.execute(
                    institutionId,
                    new CreatePersonRequest(
                        "Ana",
                        "Garcia",
                        "12345678",
                        "ana@example.com",
                        "03534100000",
                        LocalDate.of(2000, 1, 1),
                        SystemRoleCode.INSTITUTIONAL_AUTHORITY,
                        UUID.fromString("019e18e4-c93b-7583-90a6-0e9410ce3ad3"))))
        .isInstanceOf(RoleNotAssignableException.class);

    verify(personRepository, never()).save(any());
    verify(accounts, never()).invite(any(), any(), any());
    verify(assignPersonSystemRoleUseCase, never())
        .execute(any(Person.class), any(SystemRoleCode.class));
  }
}
