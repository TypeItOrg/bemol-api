package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AssignPersonSystemRoleUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.GuardianLinkNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianLinkReviewResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The institution decides who may represent a person: approving or rejecting a pending link. */
@Service
@RequiredArgsConstructor
public class ResolveGuardianLinkUseCase {

  private final PersonGuardianRepository personGuardianRepository;
  private final PersonRepository personRepository;
  private final PersonRoleAssignmentRepository personRoleAssignmentRepository;
  private final AssignPersonSystemRoleUseCase assignPersonSystemRoleUseCase;
  private final AuditEventRecorder auditEventRecorder;

  @Transactional
  public GuardianLinkReviewResponse approve(
      final UUID institutionId, final UUID reviewerPersonId, final UUID linkId) {
    final PersonGuardian link = findLink(institutionId, linkId);
    final Person reviewer = findReviewer(institutionId, reviewerPersonId);

    link.approve(reviewer);
    assignApplicantRoleIfUnassigned(institutionId, link.getDependentPerson());
    audit(link, reviewer, AuditAction.GUARDIAN_LINK_APPROVED);

    return GuardianLinkReviewResponse.from(link);
  }

  @Transactional
  public GuardianLinkReviewResponse reject(
      final UUID institutionId, final UUID reviewerPersonId, final UUID linkId) {
    final PersonGuardian link = findLink(institutionId, linkId);
    final Person reviewer = findReviewer(institutionId, reviewerPersonId);

    link.reject(reviewer);
    audit(link, reviewer, AuditAction.GUARDIAN_LINK_REJECTED);

    return GuardianLinkReviewResponse.from(link);
  }

  @Transactional
  public GuardianLinkReviewResponse approveForPlatform(
      final UUID institutionId, final UUID linkId) {
    final PersonGuardian link = findLink(institutionId, linkId);

    link.approve(null);
    assignApplicantRoleIfUnassigned(institutionId, link.getDependentPerson());
    audit(link, null, AuditAction.GUARDIAN_LINK_APPROVED);

    return GuardianLinkReviewResponse.from(link);
  }

  @Transactional
  public GuardianLinkReviewResponse rejectForPlatform(final UUID institutionId, final UUID linkId) {
    final PersonGuardian link = findLink(institutionId, linkId);

    link.reject(null);
    audit(link, null, AuditAction.GUARDIAN_LINK_REJECTED);

    return GuardianLinkReviewResponse.from(link);
  }

  private Person findReviewer(final UUID institutionId, final UUID reviewerPersonId) {
    return personRepository
        .findByIdAndInstitution_Id(reviewerPersonId, institutionId)
        .orElseThrow(PersonNotFoundException::new);
  }

  /** Scoped to the institution, so a request of another institution is simply not found. */
  private PersonGuardian findLink(final UUID institutionId, final UUID linkId) {
    return personGuardianRepository
        .findForUpdate(linkId, institutionId)
        .orElseThrow(GuardianLinkNotFoundException::new);
  }

  /**
   * Dependents apply like any other person, so they carry the applicant role. Assigning it replaces
   * every other role, hence it is only granted to people that have none yet.
   */
  private void assignApplicantRoleIfUnassigned(final UUID institutionId, final Person dependent) {
    if (!personRoleAssignmentRepository
        .findByPerson_IdAndInstitution_Id(dependent.getId(), institutionId)
        .isEmpty()) {
      return;
    }

    assignPersonSystemRoleUseCase.execute(dependent, SystemRoleCode.APPLICANT, false);
  }

  private void audit(
      final PersonGuardian link, final @Nullable Person reviewer, final AuditAction action) {
    if (reviewer == null) {
      return;
    }

    auditEventRecorder.record(
        link.getInstitution(),
        reviewer.getId(),
        link.getDependentPerson().getId(),
        action,
        AuditEntityType.PERSON_GUARDIAN,
        link.getId());
  }
}
