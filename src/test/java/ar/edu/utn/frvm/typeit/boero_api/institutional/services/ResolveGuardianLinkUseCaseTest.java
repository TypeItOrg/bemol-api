package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AssignPersonSystemRoleUseCase;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorCategory;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.GuardianLinkAlreadyResolvedException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.GuardianLinkNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianLinkReviewResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResolveGuardianLinkUseCaseTest {

  @Mock private PersonGuardianRepository personGuardianRepository;
  @Mock private PersonRepository personRepository;
  @Mock private PersonRoleAssignmentRepository personRoleAssignmentRepository;
  @Mock private AssignPersonSystemRoleUseCase assignPersonSystemRoleUseCase;
  @Mock private AuditEventRecorder auditEventRecorder;

  private final UUID linkId = UUID.randomUUID();

  private ResolveGuardianLinkUseCase useCase;
  private Institution institution;
  private Person reviewer;
  private PersonGuardian link;

  @BeforeEach
  void setUp() {
    useCase =
        new ResolveGuardianLinkUseCase(
            personGuardianRepository,
            personRepository,
            personRoleAssignmentRepository,
            assignPersonSystemRoleUseCase,
            auditEventRecorder);
    institution = Institution.builder().id(UUID.randomUUID()).build();
    reviewer = Person.builder().id(UUID.randomUUID()).institution(institution).build();
    final Person tutor = Person.builder().id(UUID.randomUUID()).institution(institution).build();
    final Person dependent =
        Person.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .documentNumber("54123456")
            .build();
    link = PersonGuardian.request(institution, tutor, dependent, GuardianRelationship.MOTHER, true);
  }

  private void givenPendingLinkAndReviewer() {
    when(personGuardianRepository.findForUpdate(linkId, institution.getId()))
        .thenReturn(Optional.of(link));
    when(personRepository.findByIdAndInstitution_Id(reviewer.getId(), institution.getId()))
        .thenReturn(Optional.of(reviewer));
  }

  @Test
  @DisplayName("Should activate the link, grant the applicant role and audit the approval")
  void approve_activatesLinkAndGrantsApplicantRole() {
    givenPendingLinkAndReviewer();
    when(personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(
            link.getDependentPerson().getId(), institution.getId()))
        .thenReturn(List.of());

    final GuardianLinkReviewResponse response =
        useCase.approve(institution.getId(), reviewer.getId(), linkId);

    assertThat(link.getStatus()).isEqualTo(GuardianLinkStatus.ACTIVE);
    assertThat(link.getResolvedBy()).isSameAs(reviewer);
    assertThat(response.status()).isEqualTo(GuardianLinkStatus.ACTIVE);
    verify(assignPersonSystemRoleUseCase)
        .execute(link.getDependentPerson(), SystemRoleCode.APPLICANT, false);
    verify(auditEventRecorder)
        .record(
            institution,
            reviewer.getId(),
            link.getDependentPerson().getId(),
            AuditAction.GUARDIAN_LINK_APPROVED,
            AuditEntityType.PERSON_GUARDIAN,
            link.getId());
  }

  @Test
  @DisplayName("Should keep the roles of a person that already has one")
  void approve_doesNotReplaceExistingRoles() {
    givenPendingLinkAndReviewer();
    when(personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(
            link.getDependentPerson().getId(), institution.getId()))
        .thenReturn(
            List.of(
                PersonRoleAssignment.builder()
                    .role(Role.builder().name("Estudiante").build())
                    .build()));

    useCase.approve(institution.getId(), reviewer.getId(), linkId);

    assertThat(link.getStatus()).isEqualTo(GuardianLinkStatus.ACTIVE);
    verify(assignPersonSystemRoleUseCase, never())
        .execute(any(Person.class), any(SystemRoleCode.class), anyBoolean());
  }

  @Test
  @DisplayName("Should reject the link without granting any role and audit the rejection")
  void reject_rejectsLink() {
    givenPendingLinkAndReviewer();

    final GuardianLinkReviewResponse response =
        useCase.reject(institution.getId(), reviewer.getId(), linkId);

    assertThat(link.getStatus()).isEqualTo(GuardianLinkStatus.REJECTED);
    assertThat(response.status()).isEqualTo(GuardianLinkStatus.REJECTED);
    verifyNoInteractions(assignPersonSystemRoleUseCase);
    verify(auditEventRecorder)
        .record(
            institution,
            reviewer.getId(),
            link.getDependentPerson().getId(),
            AuditAction.GUARDIAN_LINK_REJECTED,
            AuditEntityType.PERSON_GUARDIAN,
            link.getId());
  }

  @Test
  @DisplayName("Should not find a link of another institution")
  void resolve_failsWhenLinkIsNotInTheInstitution() {
    final UUID unknownLinkId = UUID.randomUUID();
    when(personGuardianRepository.findForUpdate(unknownLinkId, institution.getId()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.approve(institution.getId(), reviewer.getId(), unknownLinkId))
        .isInstanceOfSatisfying(
            GuardianLinkNotFoundException.class,
            exception -> {
              assertThat(exception.category()).isEqualTo(ErrorCategory.NOT_FOUND);
              assertThat(exception.code()).isEqualTo("GUARDIAN_LINK_NOT_FOUND");
            });
    verifyNoInteractions(auditEventRecorder, assignPersonSystemRoleUseCase);
  }

  @Test
  @DisplayName("Should not resolve a link twice")
  void resolve_failsWhenAlreadyResolved() {
    givenPendingLinkAndReviewer();
    link.reject(reviewer);

    assertThatThrownBy(() -> useCase.approve(institution.getId(), reviewer.getId(), linkId))
        .isInstanceOf(GuardianLinkAlreadyResolvedException.class);
    assertThatThrownBy(() -> useCase.reject(institution.getId(), reviewer.getId(), linkId))
        .isInstanceOf(GuardianLinkAlreadyResolvedException.class);
    assertThat(link.getStatus()).isEqualTo(GuardianLinkStatus.REJECTED);
    verifyNoInteractions(auditEventRecorder, assignPersonSystemRoleUseCase);
  }
}
