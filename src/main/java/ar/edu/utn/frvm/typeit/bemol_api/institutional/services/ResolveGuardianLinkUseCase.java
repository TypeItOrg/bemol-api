package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.PersonOwnAccessService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AssignPersonSystemRoleUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.GuardianLinkNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.GuardianLinkReviewResponse;
import java.util.Map;
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
  private final InstitutionRepository institutions;
  private final PersonOwnAccessService ownAccess;
  private final PersonRepository personRepository;
  private final AssignPersonSystemRoleUseCase assignPersonSystemRoleUseCase;
  private final AuditEventRecorder auditEventRecorder;

  @Transactional
  public GuardianLinkReviewResponse approve(
      final UUID institutionId, final UUID reviewerPersonId, final UUID linkId) {
    institutions.findByIdForUpdate(institutionId).orElseThrow(InstitutionNotFoundException::new);
    final PersonGuardian link = findLink(institutionId, linkId);
    final Person reviewer = findReviewer(institutionId, reviewerPersonId);

    link.approve(reviewer);
    ownAccess.enableRequested(link);
    assignPersonSystemRoleUseCase.executePreservingRoles(
        link.getDependentPerson(), SystemRoleCode.APPLICANT);
    audit(link, reviewer, AuditAction.GUARDIAN_LINK_APPROVED);

    return GuardianLinkReviewResponse.from(link);
  }

  @Transactional
  public GuardianLinkReviewResponse reject(
      final UUID institutionId, final UUID reviewerPersonId, final UUID linkId) {
    institutions.findByIdForUpdate(institutionId).orElseThrow(InstitutionNotFoundException::new);
    final PersonGuardian link = findLink(institutionId, linkId);
    final Person reviewer = findReviewer(institutionId, reviewerPersonId);

    link.reject(reviewer);
    audit(link, reviewer, AuditAction.GUARDIAN_LINK_REJECTED);

    return GuardianLinkReviewResponse.from(link);
  }

  @Transactional
  public GuardianLinkReviewResponse approveForPlatform(
      final UUID institutionId, final UUID linkId) {
    institutions.findByIdForUpdate(institutionId).orElseThrow(InstitutionNotFoundException::new);
    final PersonGuardian link = findLink(institutionId, linkId);

    link.approve(null);
    ownAccess.enableRequested(link);
    assignPersonSystemRoleUseCase.executePreservingRoles(
        link.getDependentPerson(), SystemRoleCode.APPLICANT);
    audit(link, null, AuditAction.GUARDIAN_LINK_APPROVED);

    return GuardianLinkReviewResponse.from(link);
  }

  @Transactional
  public GuardianLinkReviewResponse rejectForPlatform(final UUID institutionId, final UUID linkId) {
    institutions.findByIdForUpdate(institutionId).orElseThrow(InstitutionNotFoundException::new);
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

  private void audit(
      final PersonGuardian link, final @Nullable Person reviewer, final AuditAction action) {
    auditEventRecorder.recordCurrentActor(
        link.getInstitution(),
        link.getDependentPerson().getId(),
        action,
        AuditEntityType.PERSON_GUARDIAN,
        link.getId(),
        reviewer == null
            ? AuditOrigin.PLATFORM_ADMINISTRATION
            : AuditOrigin.INSTITUTIONAL_ADMINISTRATION,
        Map.of());
  }
}
