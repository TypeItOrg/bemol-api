package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PersonAccessInvitationRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.GuardianLinkNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionMessages;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.OwnAccessException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.UnauthorizedGuardianshipException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.OwnAccessResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.UpdateOwnAccessRequest;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PersonOwnAccessService {
  private final InstitutionRepository institutions;
  private final PersonGuardianRepository links;
  private final UserRepository users;
  private final PersonAccessInvitationRepository invitations;
  private final Clock clock;
  private final AccountAccessAuthorization access;
  private final AuditEventRecorder audit;
  private final PersonOwnAccessProvisioning provisioning;
  private final InstitutionalAccountManagementUseCase accounts;

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
        provisioning.institutionalAccountRequiredIds(institutionId, personIds);
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
    if (user != null && !user.isAccessEnabled()) {
      return "INSTITUTION_BLOCKED";
    }
    if (institutionRequired) {
      return "INSTITUTION_REQUIRED";
    }
    if (user != null) {
      return !user.isSelfAccessEnabled()
          ? "DISABLED"
          : user.requiresEmailVerification() ? "INVITATION_PENDING" : "ENABLED";
    }
    return pending ? "INVITATION_PENDING" : "NO_ACCOUNT";
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
    access.requireManagement(link.getDependentPerson());
    change(link, requireNonNull(request.enabled()), request.email());
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
    // Representation never changes an existing account's access, including legacy requests.
    final var user =
        users.findByPerson_IdAndInstitution_Id(
            link.getDependentPerson().getId(), link.getInstitution().getId());
    if (user.isPresent()
        || provisioning.requiresInstitutionalAccountCreation(link.getDependentPerson())) {
      return;
    }
    change(link, true, link.getDeclaredEmail());
  }

  private void change(PersonGuardian link, boolean enabled, @Nullable String requestedEmail) {
    final var person = link.getDependentPerson();
    if (provisioning.requiresInstitutionalAccountCreation(person)) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_INSTITUTION_REQUIRED);
    }
    final var user =
        users.findByPerson_IdAndInstitution_Id(person.getId(), link.getInstitution().getId());
    if (user.isPresent()) {
      if (requestedEmail != null && !requestedEmail.equalsIgnoreCase(person.getEmail())) {
        throw new OwnAccessException(InstitutionMessages.ACCOUNT_EMAIL_CHANGE_REQUIRED);
      }
      accounts.enable(link.getInstitution().getId(), person.getId(), enabled);
      return;
    }
    if (!enabled) {
      provisioning.cancelInvitations(person.getId());
      return;
    }
    final String email = requestedEmail != null ? requestedEmail : person.getEmail();
    if (email == null || email.isBlank()) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_EMAIL_REQUIRED);
    }
    provisioning.issueInvitation(person, link, null, email);
  }
}
