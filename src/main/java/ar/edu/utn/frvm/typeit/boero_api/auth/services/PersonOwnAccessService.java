package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.PersonAccessInvitation;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.auth.enums.EmailVerificationStatus;
import ar.edu.utn.frvm.typeit.boero_api.auth.events.PersonAccessInvited;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.PersonAccessInvitationRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.ActivateOwnAccessRequest;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.PermissionDelegationNotAllowedException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.CachedAuthoritySnapshotResolver;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.PermissionAccess;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.GuardianLinkNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionMessages;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.OwnAccessException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.UnauthorizedGuardianshipException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.OwnAccessResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.UpdateOwnAccessRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PersonOwnAccessService {
  private static final Set<String> INSTITUTIONAL_ROLE_CODES =
      Set.of(
          SystemRoleCode.INSTITUTIONAL_AUTHORITY.name(),
          SystemRoleCode.ADMINISTRATIVE.name(),
          SystemRoleCode.TEACHER.name());
  private static final Set<String> ACADEMIC_PERMISSION_CODES =
      Set.of(
          PermissionCode.ACADEMIC_OFFER_READ.getCode(),
          PermissionCode.STUDY_PLAN_READ.getCode(),
          PermissionCode.ACADEMIC_YEAR_READ.getCode());

  private final InstitutionRepository institutions;
  private final PersonRepository people;
  private final CachedAuthoritySnapshotResolver authorities;
  private final PersonGuardianRepository links;
  private final UserRepository users;
  private final PersonRoleAssignmentRepository roles;
  private final PersonAccessInvitationRepository invitations;
  private final SessionRevocationService sessions;
  private final InstitutionalHostContext hostContext;
  private final ApplicationEventPublisher events;
  private final PasswordEncoder passwords;
  private final Clock clock;
  private final AuditEventRecorder audit;
  private final EntityManager entityManager;
  private final SecureRandom random = new SecureRandom();

  @Transactional(readOnly = true)
  public OwnAccessResponse status(PersonGuardian link) {
    if (!link.isActive()) {
      return new OwnAccessResponse("UNAVAILABLE");
    }
    final UUID personId = link.getDependentPerson().getId();
    return new OwnAccessResponse(
        requireNonNull(statuses(link.getInstitution().getId(), List.of(personId)).get(personId)));
  }

  @Transactional(readOnly = true)
  public Map<UUID, String> statuses(final UUID institutionId, final List<UUID> personIds) {
    if (personIds.isEmpty()) {
      return Map.of();
    }
    final Map<UUID, User> usersByPerson =
        users.findByPerson_IdInAndInstitution_Id(personIds, institutionId).stream()
            .collect(Collectors.toMap(user -> user.getPerson().getId(), user -> user));
    final List<UUID> withoutAccount =
        personIds.stream().filter(id -> !usersByPerson.containsKey(id)).toList();
    final Set<UUID> institutionRequired =
        institutionalAccountRequiredIds(institutionId, withoutAccount);
    final Set<UUID> pendingInvitations =
        withoutAccount.isEmpty()
            ? Set.of()
            : invitations
                .findPendingByPersonIds(withoutAccount, institutionId, clock.instant())
                .stream()
                .filter(
                    invitation ->
                        invitation.isInstitutional()
                            || (!institutionRequired.contains(invitation.getPerson().getId())
                                && invitation.hasActiveRepresentation()))
                .map(invitation -> invitation.getPerson().getId())
                .collect(Collectors.toSet());

    return personIds.stream()
        .collect(
            Collectors.toMap(
                id -> id,
                id ->
                    status(
                        usersByPerson.get(id),
                        institutionRequired.contains(id),
                        pendingInvitations.contains(id))));
  }

  private String status(
      final @Nullable User user, final boolean institutionRequired, final boolean pending) {
    if (user != null) {
      if (!user.isAccessEnabled()) {
        return "INSTITUTION_BLOCKED";
      }
      return !user.isSelfAccessEnabled()
          ? "DISABLED"
          : user.requiresEmailVerification() ? "INVITATION_PENDING" : "ENABLED";
    }
    return pending
        ? "INVITATION_PENDING"
        : institutionRequired ? "INSTITUTION_REQUIRED" : "NO_ACCOUNT";
  }

  @Transactional
  public OwnAccessResponse inviteInstitutionally(
      final UUID institutionId, final UUID actorPersonId, final UUID personId, final String email) {
    final var person = requireInstitutionalProvisioning(institutionId, actorPersonId, personId);
    if (person.getEmail() != null && !person.getEmail().equalsIgnoreCase(email)) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_EMAIL_CHANGED);
    }
    cancelInvitations(personId);
    if (person.getEmail() == null) {
      person.updateContact(email, person.getPhoneNumber());
    }
    final var issuer =
        people
            .findByIdAndInstitution_Id(actorPersonId, institutionId)
            .orElseThrow(PersonNotFoundException::new);
    final var invitation = issueInvitation(person, null, issuer, email);
    audit.record(
        person.getInstitution(),
        actorPersonId,
        personId,
        AuditAction.PERSON_OWN_ACCESS_INVITED,
        AuditEntityType.PERSON_ACCESS_INVITATION,
        invitation.getId());
    return new OwnAccessResponse("INVITATION_PENDING");
  }

  @Transactional
  public void cancelInstitutionalInvitation(
      final UUID institutionId, final UUID actorPersonId, final UUID personId) {
    final var person = requireInstitutionalProvisioning(institutionId, actorPersonId, personId);
    cancelInvitations(personId);
    audit.record(
        person.getInstitution(),
        actorPersonId,
        personId,
        AuditAction.PERSON_OWN_ACCESS_INVITATION_CANCELLED,
        AuditEntityType.PERSON,
        personId);
  }

  private Person requireInstitutionalProvisioning(
      final UUID institutionId, final UUID actorPersonId, final UUID personId) {
    final var institution =
        institutions
            .findByIdForUpdate(institutionId)
            .orElseThrow(InstitutionNotFoundException::new);
    final var person =
        people
            .findByIdAndInstitutionIdForUpdate(personId, institutionId)
            .filter(value -> !value.isDeleted())
            .orElseThrow(PersonNotFoundException::new);
    final var issuer =
        people
            .findByIdAndInstitution_Id(actorPersonId, institutionId)
            .orElseThrow(PersonNotFoundException::new);
    if (!institution.isActive() || !canProvisionInstitutionally(issuer, person)) {
      throw new PermissionDelegationNotAllowedException();
    }
    if (users.findByPerson_IdAndInstitution_Id(personId, institutionId).isPresent()) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_ACCOUNT_EXISTS);
    }
    return person;
  }

  private boolean canProvisionInstitutionally(final Person issuer, final Person person) {
    final var institutionId = person.getInstitution().getId();
    if (!issuer.getInstitution().getId().equals(institutionId)
        || issuer.isDeleted()
        || users
            .findByPerson_IdAndInstitution_Id(issuer.getId(), institutionId)
            .filter(User::isEnabled)
            .isEmpty()) {
      return false;
    }
    final var actor = authorities.resolveFreshForPerson(issuer.getId(), institutionId);
    final var required =
        Set.of(
            PermissionCode.INSTITUTION_PERSON_CREATE,
            PermissionCode.INSTITUTION_USER_STATUS_UPDATE,
            PermissionCode.INSTITUTION_ROLE_ASSIGN);
    if (required.stream()
        .anyMatch(
            permission ->
                !actor
                    .permissionScopes()
                    .getOrDefault(permission, PermissionAccess.none())
                    .institutional())) {
      return false;
    }
    // Activating an existing privileged identity must not delegate powers the issuer lacks.
    return authorities
        .resolveFreshForPerson(person.getId(), institutionId)
        .permissionScopes()
        .entrySet()
        .stream()
        .allMatch(
            entry ->
                actor
                    .permissionScopes()
                    .getOrDefault(entry.getKey(), PermissionAccess.none())
                    .contains(entry.getValue()));
  }

  private void cancelInvitations(final UUID personId) {
    invitations.findByPerson_Id(personId).forEach(i -> i.consume(clock.instant()));
    invitations.flush();
  }

  @Transactional
  public OwnAccessResponse update(
      UUID institutionId, UUID actorPersonId, UUID linkId, UpdateOwnAccessRequest request) {
    institutions.findByIdForUpdate(institutionId).orElseThrow(InstitutionNotFoundException::new);
    final var link =
        links.findForUpdate(linkId, institutionId).orElseThrow(GuardianLinkNotFoundException::new);
    if (!link.isActive()
        || !link.getTutorPerson().getId().equals(actorPersonId)
        || link.getDependentPerson().isDeleted()) {
      throw new UnauthorizedGuardianshipException();
    }
    change(link, request.enabled(), request.email());
    audit.record(
        link.getInstitution(),
        actorPersonId,
        link.getDependentPerson().getId(),
        AuditAction.PERSON_OWN_ACCESS_CHANGED,
        AuditEntityType.PERSON_GUARDIAN,
        link.getId());
    return status(link);
  }

  @Transactional
  public void enableRequested(PersonGuardian link) {
    if (!link.isOwnAccessRequested()) {
      return;
    }
    // Approval grants representation even when an institutional login block must remain in force.
    final var user =
        users.findByPerson_IdAndInstitution_Id(
            link.getDependentPerson().getId(), link.getInstitution().getId());
    if (user.filter(value -> !value.isAccessEnabled()).isPresent()
        || (user.isEmpty() && requiresInstitutionalAccountCreation(link.getDependentPerson()))) {
      return;
    }
    change(link, true, link.getDeclaredEmail());
  }

  private void change(PersonGuardian link, boolean enabled, @Nullable String requestedEmail) {
    final var person = link.getDependentPerson();
    final var user =
        users.findByPerson_IdAndInstitution_Id(person.getId(), link.getInstitution().getId());
    if (enabled && user.isPresent() && !user.get().isAccessEnabled()) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_INSTITUTION_BLOCKED);
    }
    cancelInvitations(person.getId());
    if (user.isPresent()) {
      user.get().updateSelfAccess(enabled);
      if (!enabled) {
        users.flush();
        sessions.revokeInstitutionalSessionsForUser(user.get().getId());
      }
      return;
    }
    if (!enabled) {
      return;
    }
    if (requiresInstitutionalAccountCreation(person)) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_INSTITUTION_REQUIRED);
    }
    final String email = person.getEmail() != null ? person.getEmail() : requestedEmail;
    if (email == null || email.isBlank()) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_EMAIL_REQUIRED);
    }
    issueInvitation(person, link, null, email);
  }

  private PersonAccessInvitation issueInvitation(
      final Person person,
      final @Nullable PersonGuardian representation,
      final @Nullable Person issuer,
      final String email) {
    final byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    final String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    final String hash = RequestInstitutionalPasswordRecoveryUseCase.hash(raw);
    final var expiresAt = clock.instant().plus(24, ChronoUnit.HOURS);
    final var invitation =
        invitations.save(
            issuer != null
                ? PersonAccessInvitation.issueInstitutionally(
                    person, issuer, email, hash, expiresAt)
                : PersonAccessInvitation.issue(
                    java.util.Objects.requireNonNull(representation), email, hash, expiresAt));
    events.publishEvent(
        new PersonAccessInvited(
            person.getId(),
            email,
            person.getInstitution().getName(),
            person.getFirstName() + " " + person.getLastName(),
            raw,
            person.getInstitution().getPublicSubdomain()));
    return invitation;
  }

  private boolean requiresInstitutionalAccountCreation(final Person person) {
    return !institutionalAccountRequiredIds(
            person.getInstitution().getId(), List.of(person.getId()))
        .isEmpty();
  }

  private Set<UUID> institutionalAccountRequiredIds(
      final UUID institutionId, final List<UUID> personIds) {
    if (personIds.isEmpty()) {
      return Set.of();
    }
    // Never use cached grants: roles or permissions may change after an invitation is issued.
    return new HashSet<>(
        roles.findPersonIdsRequiringInstitutionalAccess(
            personIds, institutionId, INSTITUTIONAL_ROLE_CODES, ACADEMIC_PERMISSION_CODES));
  }

  @Transactional
  public void activate(ActivateOwnAccessRequest request) {
    final String hash = RequestInstitutionalPasswordRecoveryUseCase.hash(request.token());
    final var invitation =
        invitations
            .findByTokenHash(hash)
            .orElseThrow(
                () -> new OwnAccessException(InstitutionMessages.OWN_ACCESS_TOKEN_INVALID));
    final var institution = invitation.getPerson().getInstitution();
    hostContext.requireInstitution(institution.getId());
    institutions
        .findByIdForUpdate(institution.getId())
        .orElseThrow(InstitutionNotFoundException::new);
    // Refresh after the shared lock so cancellation, replay and revocation cannot use stale state.
    final var current =
        invitations
            .findByTokenHash(hash)
            .orElseThrow(
                () -> new OwnAccessException(InstitutionMessages.OWN_ACCESS_TOKEN_INVALID));
    entityManager.refresh(current);
    final var representation = current.getRepresentation();
    if (representation != null) {
      entityManager.refresh(representation);
    }
    entityManager.refresh(current.getPerson(), LockModeType.PESSIMISTIC_WRITE);
    entityManager.refresh(institution);
    if (!current.isUsableAt(clock.instant())
        || (!current.isInstitutional() && !current.hasActiveRepresentation())
        || current.getPerson().isDeleted()
        || !institution.isActive()
        || (current.getPerson().getEmail() != null
            && !current.getPerson().getEmail().equalsIgnoreCase(current.getEmail()))
        || users
            .findByPerson_IdAndInstitution_Id(current.getPerson().getId(), institution.getId())
            .isPresent()) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_TOKEN_INVALID);
    }
    final var issuer = current.getInstitutionalIssuer();
    if (issuer != null) {
      entityManager.refresh(issuer);
      if (!canProvisionInstitutionally(issuer, current.getPerson())) {
        throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_TOKEN_INVALID);
      }
    } else if (requiresInstitutionalAccountCreation(current.getPerson())) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_INSTITUTION_REQUIRED);
    }
    if (!request.password().equals(request.confirmPassword())) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_PASSWORD_MISMATCH);
    }
    if (current.getPerson().getEmail() == null) {
      current.getPerson().updateContact(current.getEmail(), current.getPerson().getPhoneNumber());
    }
    users.save(
        User.builder()
            .person(current.getPerson())
            .institution(institution)
            .password(passwords.encode(request.password()))
            .emailVerificationStatus(EmailVerificationStatus.VERIFIED)
            .emailVerifiedAt(clock.instant())
            .build());
    current.consume(clock.instant());
    audit.record(
        institution,
        current.getPerson().getId(),
        current.getPerson().getId(),
        AuditAction.PERSON_OWN_ACCESS_ACTIVATED,
        current.isInstitutional()
            ? AuditEntityType.PERSON_ACCESS_INVITATION
            : AuditEntityType.PERSON_GUARDIAN,
        current.isInstitutional()
            ? current.getId()
            : java.util.Objects.requireNonNull(representation).getId());
  }
}
