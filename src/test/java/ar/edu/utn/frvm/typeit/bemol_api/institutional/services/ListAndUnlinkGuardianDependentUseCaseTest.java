package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.PersonOwnAccessService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.DependentNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.DependentApplicationCount;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.GuardianDependentResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ListAndUnlinkGuardianDependentUseCaseTest {

  private static final UUID INSTITUTION_ID = UUID.randomUUID();
  private static final UUID TUTOR_ID = UUID.randomUUID();

  @Mock private InstitutionRepository institutions;
  @Mock private PersonOwnAccessService ownAccess;

  @BeforeEach
  void setup() {
    org.mockito.Mockito.lenient()
        .when(institutions.findByIdForUpdate(INSTITUTION_ID))
        .thenReturn(Optional.of(Institution.builder().id(INSTITUTION_ID).build()));
  }

  @Mock private PersonGuardianRepository personGuardianRepository;
  @Mock private PersonRoleAssignmentRepository personRoleAssignmentRepository;
  @Mock private AuditEventRecorder auditEventRecorder;

  private static final List<GuardianLinkStatus> VISIBLE_STATUSES =
      List.of(
          GuardianLinkStatus.PENDING,
          GuardianLinkStatus.ACTIVE,
          GuardianLinkStatus.REJECTED,
          GuardianLinkStatus.ENDED);

  @ParameterizedTest
  @EnumSource(
      value = GuardianLinkStatus.class,
      names = {"PENDING", "REJECTED", "ENDED"})
  void nonActiveResponsesHideRegisteredDataEvenIfTheCallerSuppliesIt(
      final GuardianLinkStatus status) {
    final var response =
        GuardianDependentResponse.from(
            link("Mateo", "54123456", status), 3, List.of("Estudiante"), "ENABLED");

    assertThat(response.dependentPersonId()).isNull();
    assertThat(response.firstName()).isNull();
    assertThat(response.birthDate()).isNull();
    assertThat(response.roles()).isEmpty();
    assertThat(response.activeApplicationsCount()).isZero();
    assertThat(response.ownAccessStatus()).isEqualTo("UNAVAILABLE");
  }

  @Test
  @DisplayName("Should list dependents with their active applications count")
  void list_returnsDependentsWithCounts() {
    final PersonGuardian withApplication = link("Mateo", "54123456", GuardianLinkStatus.ACTIVE);
    final PersonGuardian withoutApplication = link("Lucia", "54123457", GuardianLinkStatus.ACTIVE);
    when(ownAccess.statuses(
            INSTITUTION_ID,
            List.of(
                withApplication.getDependentPerson().getId(),
                withoutApplication.getDependentPerson().getId())))
        .thenReturn(
            Map.of(
                withApplication.getDependentPerson().getId(), "NO_ACCOUNT",
                withoutApplication.getDependentPerson().getId(), "NO_ACCOUNT"));
    when(personGuardianRepository
            .findByInstitution_IdAndTutorPerson_IdAndStatusInOrderByCreatedAtAsc(
                INSTITUTION_ID, TUTOR_ID, VISIBLE_STATUSES))
        .thenReturn(List.of(withApplication, withoutApplication));
    when(personGuardianRepository.countActiveApplicationsByApplicant(
            INSTITUTION_ID,
            List.of(
                withApplication.getDependentPerson().getId(),
                withoutApplication.getDependentPerson().getId())))
        .thenReturn(
            List.of(
                new DependentApplicationCount(withApplication.getDependentPerson().getId(), 2)));
    when(personRoleAssignmentRepository.findByPerson_IdInAndInstitution_Id(
            List.of(
                withApplication.getDependentPerson().getId(),
                withoutApplication.getDependentPerson().getId()),
            INSTITUTION_ID))
        .thenReturn(
            List.of(
                assignment(withApplication.getDependentPerson(), "Estudiante"),
                assignment(withoutApplication.getDependentPerson(), "Postulante")));

    final List<GuardianDependentResponse> result =
        new ListGuardianDependentsUseCase(
                personGuardianRepository, ownAccess, personRoleAssignmentRepository)
            .execute(INSTITUTION_ID, TUTOR_ID);

    assertThat(result)
        .extracting(
            GuardianDependentResponse::firstName,
            GuardianDependentResponse::activeApplicationsCount,
            GuardianDependentResponse::roles,
            GuardianDependentResponse::ownAccessStatus)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Mateo", 2L, List.of("Estudiante"), "NO_ACCOUNT"),
            org.assertj.core.groups.Tuple.tuple("Lucia", 0L, List.of("Postulante"), "NO_ACCOUNT"));
  }

  @Test
  @DisplayName("Should show the status and hide the person's data while a link is not active")
  void list_hidesDataOfNonActiveLinks() {
    final PersonGuardian pending = link("Mateo", "54123456", GuardianLinkStatus.PENDING);
    final PersonGuardian rejected = link("Lucia", "54123457", GuardianLinkStatus.REJECTED);
    final PersonGuardian ended = link("Sofia", "54123458", GuardianLinkStatus.ENDED);
    when(personGuardianRepository
            .findByInstitution_IdAndTutorPerson_IdAndStatusInOrderByCreatedAtAsc(
                INSTITUTION_ID, TUTOR_ID, VISIBLE_STATUSES))
        .thenReturn(List.of(pending, rejected, ended));

    final List<GuardianDependentResponse> result =
        new ListGuardianDependentsUseCase(
                personGuardianRepository, ownAccess, personRoleAssignmentRepository)
            .execute(INSTITUTION_ID, TUTOR_ID);

    assertThat(result)
        .extracting(
            GuardianDependentResponse::status,
            GuardianDependentResponse::firstName,
            GuardianDependentResponse::documentNumber)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(GuardianLinkStatus.PENDING, null, "54123456"),
            org.assertj.core.groups.Tuple.tuple(GuardianLinkStatus.REJECTED, null, "54123457"),
            org.assertj.core.groups.Tuple.tuple(GuardianLinkStatus.ENDED, null, "54123458"));
    assertThat(result).extracting(GuardianDependentResponse::dependentPersonId).containsOnlyNulls();
    assertThat(result).extracting(GuardianDependentResponse::birthDate).containsOnlyNulls();
    assertThat(result)
        .allSatisfy(
            value -> {
              assertThat(value.roles()).isEmpty();
              assertThat(value.activeApplicationsCount()).isZero();
            });
    assertThat(result)
        .extracting(GuardianDependentResponse::ownAccessStatus)
        .containsOnly("UNAVAILABLE");
    verify(ownAccess).statuses(INSTITUTION_ID, List.of());
  }

  @Test
  void list_doesNotExposeActiveAccessThroughAnotherInactiveLink() {
    final PersonGuardian active = link("Mateo", "54123456", GuardianLinkStatus.ACTIVE);
    final PersonGuardian pending =
        PersonGuardian.builder()
            .id(UUID.randomUUID())
            .dependentPerson(active.getDependentPerson())
            .status(GuardianLinkStatus.PENDING)
            .build();
    final UUID personId = active.getDependentPerson().getId();
    when(personGuardianRepository
            .findByInstitution_IdAndTutorPerson_IdAndStatusInOrderByCreatedAtAsc(
                INSTITUTION_ID, TUTOR_ID, VISIBLE_STATUSES))
        .thenReturn(List.of(active, pending));
    when(ownAccess.statuses(INSTITUTION_ID, List.of(personId)))
        .thenReturn(Map.of(personId, "ENABLED"));

    final var result =
        new ListGuardianDependentsUseCase(
                personGuardianRepository, ownAccess, personRoleAssignmentRepository)
            .execute(INSTITUTION_ID, TUTOR_ID);

    assertThat(result)
        .extracting(GuardianDependentResponse::ownAccessStatus)
        .containsExactly("ENABLED", "UNAVAILABLE");
    verify(ownAccess).statuses(INSTITUTION_ID, List.of(personId));
  }

  @Test
  @DisplayName("Should return an empty list without counting when there are no dependents")
  void list_returnsEmptyWithoutCounting() {
    when(personGuardianRepository
            .findByInstitution_IdAndTutorPerson_IdAndStatusInOrderByCreatedAtAsc(
                INSTITUTION_ID, TUTOR_ID, VISIBLE_STATUSES))
        .thenReturn(List.of());

    assertThat(
            new ListGuardianDependentsUseCase(
                    personGuardianRepository, ownAccess, personRoleAssignmentRepository)
                .execute(INSTITUTION_ID, TUTOR_ID))
        .isEmpty();
    verify(personGuardianRepository, never())
        .countActiveApplicationsByApplicant(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyCollection());
    verifyNoInteractions(ownAccess);
  }

  @Test
  @DisplayName("Should end the link without deleting it")
  void unlink_endsTheLink() {
    final PersonGuardian link = link("Mateo", "54123456", GuardianLinkStatus.ACTIVE);
    final UUID dependentId = link.getDependentPerson().getId();
    when(personGuardianRepository
            .findByInstitution_IdAndTutorPerson_IdAndDependentPerson_IdAndStatus(
                INSTITUTION_ID, TUTOR_ID, dependentId, GuardianLinkStatus.PENDING))
        .thenReturn(Optional.empty());
    when(personGuardianRepository
            .findByInstitution_IdAndTutorPerson_IdAndDependentPerson_IdAndStatus(
                INSTITUTION_ID, TUTOR_ID, dependentId, GuardianLinkStatus.ACTIVE))
        .thenReturn(Optional.of(link));

    new UnlinkGuardianDependentUseCase(institutions, personGuardianRepository, auditEventRecorder)
        .execute(INSTITUTION_ID, TUTOR_ID, dependentId);

    assertThat(link.getStatus()).isEqualTo(GuardianLinkStatus.ENDED);
    verify(personGuardianRepository, never()).delete(link);
    verify(auditEventRecorder)
        .record(
            link.getInstitution(),
            TUTOR_ID,
            dependentId,
            AuditAction.GUARDIAN_DEPENDENT_UNLINKED,
            AuditEntityType.PERSON_GUARDIAN,
            link.getId());
  }

  @Test
  @DisplayName("Should cancel a pending link without deleting it")
  void unlink_endsPendingLink() {
    final PersonGuardian link = link("Mateo", "54123456", GuardianLinkStatus.PENDING);
    final UUID dependentId = link.getDependentPerson().getId();
    when(personGuardianRepository
            .findByInstitution_IdAndTutorPerson_IdAndDependentPerson_IdAndStatus(
                INSTITUTION_ID, TUTOR_ID, dependentId, GuardianLinkStatus.PENDING))
        .thenReturn(Optional.of(link));

    new UnlinkGuardianDependentUseCase(institutions, personGuardianRepository, auditEventRecorder)
        .execute(INSTITUTION_ID, TUTOR_ID, dependentId);

    assertThat(link.getStatus()).isEqualTo(GuardianLinkStatus.ENDED);
    verify(personGuardianRepository, never()).delete(link);
    verify(auditEventRecorder)
        .record(
            link.getInstitution(),
            TUTOR_ID,
            dependentId,
            AuditAction.GUARDIAN_DEPENDENT_UNLINKED,
            AuditEntityType.PERSON_GUARDIAN,
            link.getId());
  }

  @Test
  @DisplayName("Should fail when the tutor has no active link to that dependent")
  void unlink_failsWhenNotLinked() {
    final UUID dependentId = UUID.randomUUID();
    when(personGuardianRepository
            .findByInstitution_IdAndTutorPerson_IdAndDependentPerson_IdAndStatus(
                INSTITUTION_ID, TUTOR_ID, dependentId, GuardianLinkStatus.PENDING))
        .thenReturn(Optional.empty());
    when(personGuardianRepository
            .findByInstitution_IdAndTutorPerson_IdAndDependentPerson_IdAndStatus(
                INSTITUTION_ID, TUTOR_ID, dependentId, GuardianLinkStatus.ACTIVE))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                new UnlinkGuardianDependentUseCase(
                        institutions, personGuardianRepository, auditEventRecorder)
                    .execute(INSTITUTION_ID, TUTOR_ID, dependentId))
        .isInstanceOfSatisfying(
            DependentNotFoundException.class,
            exception -> assertThat(exception.code()).isEqualTo("DEPENDENT_NOT_FOUND"));
  }

  private PersonRoleAssignment assignment(final Person person, final String roleName) {
    return PersonRoleAssignment.builder()
        .person(person)
        .role(Role.builder().name(roleName).build())
        .build();
  }

  private PersonGuardian link(
      final String firstName, final String documentNumber, final GuardianLinkStatus status) {
    final Institution institution = Institution.builder().id(INSTITUTION_ID).build();
    final Person dependent =
        Person.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .firstName(firstName)
            .lastName("Gonzalez")
            .documentNumber(documentNumber)
            .birthDate(LocalDate.of(2018, 9, 10))
            .build();

    return PersonGuardian.builder()
        .id(UUID.randomUUID())
        .institution(institution)
        .tutorPerson(Person.builder().id(TUTOR_ID).institution(institution).build())
        .dependentPerson(dependent)
        .relationship(GuardianRelationship.FATHER)
        .primaryContact(true)
        .status(status)
        .build();
  }
}
