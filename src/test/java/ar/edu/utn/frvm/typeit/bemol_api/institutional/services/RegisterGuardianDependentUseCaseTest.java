package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.AccountAccessAuthorization;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.AccountActionRateLimiter;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.PermissionDelegationNotAllowedException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.DependentAlreadyLinkedException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.CreateGuardianDependentRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.GuardianDependentRequestMode;
import jakarta.validation.Validation;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterGuardianDependentUseCaseTest {
  @Mock InstitutionRepository institutionRepository;
  @Mock PersonRepository personRepository;
  @Mock PersonGuardianRepository personGuardianRepository;
  @Mock AccountAccessAuthorization access;
  @Mock AccountActionRateLimiter limiter;
  @Mock AuditEventRecorder auditEventRecorder;

  @Spy
  jakarta.validation.Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @InjectMocks RegisterGuardianDependentUseCase useCase;
  final Institution institution = Institution.builder().id(UUID.randomUUID()).build();
  final Person tutor =
      Person.builder()
          .id(UUID.randomUUID())
          .institution(institution)
          .documentNumber("35123456")
          .build();

  @BeforeEach
  void setup() {
    org.mockito.Mockito.lenient()
        .when(institutionRepository.findByIdForUpdate(institution.getId()))
        .thenReturn(Optional.of(institution));
    org.mockito.Mockito.lenient()
        .when(personRepository.findByIdAndInstitution_Id(tutor.getId(), institution.getId()))
        .thenReturn(Optional.of(tutor));
  }

  @Test
  void newRequestNeverMaterializesOrReservesIdentity() {
    final var result =
        useCase.execute(
            institution.getId(), tutor.getId(), request(GuardianDependentRequestMode.NEW));
    assertThat(result.dependentPersonId()).isNull();
    assertThat(result.firstName()).isEqualTo("Mateo");
    assertThat(result.status()).isEqualTo(GuardianLinkStatus.PENDING);
    verify(personRepository, never()).save(any());
    verify(personRepository, never()).saveAndFlush(any());
    verify(personGuardianRepository).saveAndFlush(any(PersonGuardian.class));
    verify(limiter).guardianRequest(institution.getId(), tutor.getId());
  }

  @Test
  void existingRequestDisclosesOnlyDeclaredData() {
    final var protectedPerson =
        Person.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .documentNumber("54123456")
            .firstName("Nombre protegido")
            .build();
    when(personRepository.findByDocumentNumberAndInstitution_Id("54123456", institution.getId()))
        .thenReturn(Optional.of(protectedPerson));
    final var result =
        useCase.execute(
            institution.getId(), tutor.getId(), request(GuardianDependentRequestMode.EXISTING));
    assertThat(result.dependentPersonId()).isNull();
    assertThat(result.firstName()).isNull();
    assertThat(result.roles()).isEmpty();
    assertThat(result.activeApplicationsCount()).isZero();
  }

  @Test
  void permissionIsRequiredBeforePersistence() {
    doThrow(new PermissionDelegationNotAllowedException())
        .when(access)
        .requirePermission(
            institution.getId(), tutor.getId(), PermissionCode.GUARDIAN_LINK_REQUEST);
    assertThatThrownBy(
            () ->
                useCase.execute(
                    institution.getId(), tutor.getId(), request(GuardianDependentRequestMode.NEW)))
        .isInstanceOf(PermissionDelegationNotAllowedException.class);
    verify(personGuardianRepository, never()).saveAndFlush(any());
  }

  @Test
  void duplicatePendingDocumentIsRejectedForThisTutor() {
    when(personGuardianRepository
            .existsByInstitution_IdAndTutorPerson_IdAndDeclaredDocumentNumberAndStatusIn(
                institution.getId(),
                tutor.getId(),
                "54123456",
                List.of(GuardianLinkStatus.PENDING, GuardianLinkStatus.ACTIVE)))
        .thenReturn(true);
    assertThatThrownBy(
            () ->
                useCase.execute(
                    institution.getId(), tutor.getId(), request(GuardianDependentRequestMode.NEW)))
        .isInstanceOf(DependentAlreadyLinkedException.class);
    verify(personGuardianRepository, never()).saveAndFlush(any());
  }

  @Test
  void correctedDocumentDoesNotAllowDuplicatingAnActiveRepresentation() {
    final var person = Person.builder().id(UUID.randomUUID()).build();
    when(personRepository.findByDocumentNumberAndInstitution_Id("54123456", institution.getId()))
        .thenReturn(Optional.of(person));
    when(personGuardianRepository
            .existsByInstitution_IdAndTutorPerson_IdAndDependentPerson_IdAndStatusIn(
                institution.getId(),
                tutor.getId(),
                person.getId(),
                List.of(GuardianLinkStatus.ACTIVE)))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    institution.getId(), tutor.getId(), request(GuardianDependentRequestMode.NEW)))
        .isInstanceOf(DependentAlreadyLinkedException.class);
    verify(personGuardianRepository, never()).saveAndFlush(any());
  }

  private CreateGuardianDependentRequest request(final GuardianDependentRequestMode mode) {
    return new CreateGuardianDependentRequest(
        "54123456",
        "Mateo",
        "Gonzalez",
        LocalDate.of(2018, 9, 10),
        GuardianRelationship.FATHER,
        true,
        null,
        false,
        mode);
  }
}
