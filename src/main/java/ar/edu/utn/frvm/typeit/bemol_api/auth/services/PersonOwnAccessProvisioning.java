package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PersonAccessInvitation;
import ar.edu.utn.frvm.typeit.bemol_api.auth.events.PersonAccessInvited;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PersonAccessInvitationRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.PermissionDelegationNotAllowedException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.CachedAuthoritySnapshotResolver;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.PermissionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailAuditContext;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionMessages;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.OwnAccessException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class PersonOwnAccessProvisioning {
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
  private final UserRepository users;
  private final PersonRoleAssignmentRepository roles;
  private final PersonAccessInvitationRepository invitations;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  Person requireInstitutionalProvisioning(
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

  boolean canProvisionInstitutionally(final Person issuer, final Person person) {
    final var institutionId = person.getInstitution().getId();
    if (!issuer.getInstitution().getId().equals(institutionId)
        || issuer.isDeleted()
        || users
            .findByPerson_IdAndInstitution_Id(issuer.getId(), institutionId)
            .filter(user -> user.isEnabled())
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

  void cancelInvitations(final UUID personId) {
    invitations.findByPerson_Id(personId).forEach(i -> i.consume(clock.instant()));
    invitations.flush();
  }

  PersonAccessInvitation issueInvitation(
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
            person.getInstitution().getPublicSubdomain(),
            MailAuditContext.capture(person.getInstitution().getId(), person.getId())));
    return invitation;
  }

  boolean requiresInstitutionalAccountCreation(final Person person) {
    return !institutionalAccountRequiredIds(
            person.getInstitution().getId(), List.of(person.getId()))
        .isEmpty();
  }

  Set<UUID> institutionalAccountRequiredIds(final UUID institutionId, final List<UUID> personIds) {
    if (personIds.isEmpty()) {
      return Set.of();
    }
    // Never use cached grants: roles or permissions may change after an invitation is issued.
    return new HashSet<>(
        roles.findPersonIdsRequiringInstitutionalAccess(
            personIds, institutionId, INSTITUTIONAL_ROLE_CODES, ACADEMIC_PERMISSION_CODES));
  }
}
