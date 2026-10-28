package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.PersonResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.UpdatePersonByAdminRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdatePersonByAdminUseCaseTest {
  @org.mockito.Mock private AuditEventRecorder auditAuditEventRecorder;

  @Mock
  private ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository
      institutions;

  @Mock private ar.edu.utn.frvm.typeit.bemol_api.auth.services.AccountAccessAuthorization access;

  @Mock
  private ar.edu.utn.frvm.typeit.bemol_api.auth.services.SessionRevocationService
      sessionRevocationService;

  @Mock private PersonRepository personRepository;

  @Spy private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @InjectMocks private UpdatePersonByAdminUseCase updatePersonByAdminUseCase;

  private UUID institutionId;
  private UUID personId;
  private Person person;

  @BeforeEach
  void setUp() {
    institutionId = UUID.randomUUID();
    personId = UUID.randomUUID();
    Institution institution = Institution.builder().id(institutionId).name("Boero").build();
    org.mockito.Mockito.lenient()
        .when(institutions.findByIdForUpdate(institutionId))
        .thenReturn(Optional.of(institution));
    person =
        Person.builder()
            .id(personId)
            .institution(institution)
            .firstName("Ana")
            .lastName("García")
            .documentNumber("12345678")
            .email("ana@example.com")
            .build();
  }

  @Test
  @DisplayName("Should update provided fields")
  void execute_updatesProvidedFields() {
    when(personRepository.findByIdAndInstitutionIdForUpdate(personId, institutionId))
        .thenReturn(Optional.of(person));
    when(personRepository.findWithDetailsByIdAndInstitution_Id(personId, institutionId))
        .thenReturn(Optional.of(person));

    PersonResponse response =
        updatePersonByAdminUseCase.execute(
            institutionId,
            personId,
            new UpdatePersonByAdminRequest("Ana María", null, null, null, null));

    assertThat(response.firstName()).isEqualTo("Ana María");
    verify(personRepository).saveAndFlush(person);
  }

  @Test
  @DisplayName("Should throw when person not in institution")
  void execute_throwsWhenPersonNotInInstitution() {
    when(personRepository.findByIdAndInstitutionIdForUpdate(personId, institutionId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                updatePersonByAdminUseCase.execute(
                    institutionId,
                    personId,
                    new UpdatePersonByAdminRequest("Ana", null, null, null, null)))
        .isInstanceOf(
            ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonNotFoundException
                .class);
  }

  @Test
  @DisplayName("Should throw when all fields are null")
  void execute_throwsWhenAllFieldsNull() {
    when(personRepository.findByIdAndInstitutionIdForUpdate(personId, institutionId))
        .thenReturn(Optional.of(person));

    assertThatThrownBy(
            () ->
                updatePersonByAdminUseCase.execute(
                    institutionId,
                    personId,
                    new UpdatePersonByAdminRequest(null, null, null, null, null)))
        .isInstanceOf(ConstraintViolationException.class);

    verify(personRepository, never()).save(person);
  }

  @Test
  @DisplayName("Should not overwrite fields left null in the request")
  void execute_keepsNullFieldsUnchanged() {
    person.updateContact(person.getEmail(), "0353-999999");
    when(personRepository.findByIdAndInstitutionIdForUpdate(personId, institutionId))
        .thenReturn(Optional.of(person));
    when(personRepository.findWithDetailsByIdAndInstitution_Id(personId, institutionId))
        .thenReturn(Optional.of(person));

    PersonResponse response =
        updatePersonByAdminUseCase.execute(
            institutionId,
            personId,
            new UpdatePersonByAdminRequest("Ana María", null, null, null, null));

    assertThat(response.firstName()).isEqualTo("Ana María");
    assertThat(response.lastName()).isEqualTo("García");
    assertThat(response.phoneNumber()).isEqualTo("0353-999999");
  }
}
