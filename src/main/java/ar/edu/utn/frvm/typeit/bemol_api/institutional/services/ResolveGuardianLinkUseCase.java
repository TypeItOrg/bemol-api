package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.AccountAccessAuthorization;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.PersonOwnAccessService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AssignPersonSystemRoleUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.DependentAlreadyLinkedException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.GuardianLinkNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionMessages;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.OwnAccessException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.GuardianDependentRequestMode;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.GuardianLinkReviewResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  private final AccountAccessAuthorization access;
  private final Validator validator;

  @Transactional
  public GuardianLinkReviewResponse approve(
      final UUID institutionId, final UUID reviewerPersonId, final UUID linkId) {
    return approve(institutionId, reviewerPersonId, linkId, null);
  }

  @Transactional
  public GuardianLinkReviewResponse approve(
      final UUID institutionId,
      final UUID reviewerPersonId,
      final UUID linkId,
      final @Nullable UUID confirmedPersonId) {
    return approve(institutionId, reviewerPersonId, linkId, confirmedPersonId, reviewerPersonId);
  }

  @Transactional
  public GuardianLinkReviewResponse approve(
      final UUID institutionId,
      final UUID reviewerPersonId,
      final UUID linkId,
      final @Nullable UUID confirmedPersonId,
      final UUID actorPersonId) {
    institutions.findByIdForUpdate(institutionId).orElseThrow(InstitutionNotFoundException::new);
    access.requirePermission(institutionId, reviewerPersonId, PermissionCode.GUARDIAN_LINK_REVIEW);
    final PersonGuardian link = findLink(institutionId, linkId);
    final Person reviewer = findReviewer(institutionId, reviewerPersonId);
    if (reviewerPersonId.equals(link.getTutorPerson().getId())
        || actorPersonId.equals(link.getTutorPerson().getId())) {
      throw new OwnAccessException(InstitutionMessages.GUARDIAN_SELF_APPROVAL_FORBIDDEN);
    }
    materialize(link, reviewerPersonId, confirmedPersonId);
    link.approve(reviewer);
    ownAccess.applyRequestedAccess(link);
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

    access.requirePermission(institutionId, reviewerPersonId, PermissionCode.GUARDIAN_LINK_REVIEW);
    link.reject(reviewer);
    audit(link, reviewer, AuditAction.GUARDIAN_LINK_REJECTED);

    return GuardianLinkReviewResponse.from(link);
  }

  @Transactional
  public GuardianLinkReviewResponse approveForPlatform(
      final UUID institutionId, final UUID linkId) {
    return approveForPlatform(institutionId, linkId, null);
  }

  @Transactional
  public GuardianLinkReviewResponse approveForPlatform(
      final UUID institutionId, final UUID linkId, final @Nullable UUID confirmedPersonId) {
    access.requirePlatformAdministration();
    institutions.findByIdForUpdate(institutionId).orElseThrow(InstitutionNotFoundException::new);
    final PersonGuardian link = findLink(institutionId, linkId);
    materialize(link, null, confirmedPersonId);
    link.approve(null);
    ownAccess.applyRequestedAccess(link);
    assignPersonSystemRoleUseCase.executePreservingRoles(
        link.getDependentPerson(), SystemRoleCode.APPLICANT);
    audit(link, null, AuditAction.GUARDIAN_LINK_APPROVED);

    return GuardianLinkReviewResponse.from(link);
  }

  @Transactional
  public GuardianLinkReviewResponse rejectForPlatform(final UUID institutionId, final UUID linkId) {
    access.requirePlatformAdministration();
    institutions.findByIdForUpdate(institutionId).orElseThrow(InstitutionNotFoundException::new);
    final PersonGuardian link = findLink(institutionId, linkId);

    link.reject(null);
    audit(link, null, AuditAction.GUARDIAN_LINK_REJECTED);

    return GuardianLinkReviewResponse.from(link);
  }

  private void materialize(
      final PersonGuardian link,
      final @Nullable UUID reviewerId,
      final @Nullable UUID confirmedPersonId) {
    if (link.getStatus()
        != ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianLinkStatus.PENDING) {
      throw new ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions
          .GuardianLinkAlreadyResolvedException();
    }
    final var existing =
        personRepository.findByDocumentNumberAndInstitution_Id(
            link.getDocumentNumber(), link.getInstitution().getId());
    final Person person;
    if (existing.isPresent()) {
      person = existing.get();
      if (link.getRequestMode() == GuardianDependentRequestMode.NEW
          && (!Objects.equals(link.getDeclaredFirstName(), person.getFirstName())
              || !Objects.equals(link.getDeclaredLastName(), person.getLastName())
              || !Objects.equals(link.getDeclaredBirthDate(), person.getBirthDate()))
          && !person.getId().equals(confirmedPersonId)) {
        throw new OwnAccessException(InstitutionMessages.GUARDIAN_IDENTITY_CONFIRMATION_REQUIRED);
      }
    } else {
      if (link.getRequestMode() == GuardianDependentRequestMode.EXISTING) {
        throw new PersonNotFoundException();
      }
      if (reviewerId != null) {
        access.requirePermission(
            link.getInstitution().getId(), reviewerId, PermissionCode.INSTITUTION_PERSON_CREATE);
      }
      person =
          Person.builder()
              .institution(link.getInstitution())
              .documentNumber(link.getDocumentNumber())
              .firstName(Objects.requireNonNull(link.getDeclaredFirstName()))
              .lastName(Objects.requireNonNull(link.getDeclaredLastName()))
              .birthDate(Objects.requireNonNull(link.getDeclaredBirthDate()))
              .build();
      final var violations = validator.validate(person);
      if (!violations.isEmpty()) {
        throw new ConstraintViolationException(violations);
      }
      personRepository.saveAndFlush(person);
      auditEventRecorder.recordCurrentActor(
          link.getInstitution(),
          person.getId(),
          AuditAction.PERSON_CREATED,
          AuditEntityType.PERSON,
          person.getId(),
          AuditOrigin.administration(),
          Map.of("hasAccount", false));
    }
    if (personGuardianRepository
        .existsByInstitution_IdAndTutorPerson_IdAndDependentPerson_IdAndStatusIn(
            link.getInstitution().getId(),
            link.getTutorPerson().getId(),
            person.getId(),
            List.of(GuardianLinkStatus.ACTIVE))) {
      throw new DependentAlreadyLinkedException();
    }
    link.materialize(person);
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
        link.getDependentPersonId(),
        action,
        AuditEntityType.PERSON_GUARDIAN,
        link.getId(),
        reviewer == null
            ? AuditOrigin.PLATFORM_ADMINISTRATION
            : AuditOrigin.INSTITUTIONAL_ADMINISTRATION,
        Map.of());
  }
}
