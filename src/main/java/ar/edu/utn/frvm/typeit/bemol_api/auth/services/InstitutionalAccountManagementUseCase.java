package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.enums.AccessInvitationPurpose;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.InstitutionalPasswordResetTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PersonAccessInvitationRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionMessages;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.OwnAccessException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.OwnAccessResponse;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstitutionalAccountManagementUseCase {
  private final InstitutionRepository institutions;
  private final PersonRepository people;
  private final UserRepository users;
  private final PlatformAccountRepository platforms;
  private final PersonGuardianRepository links;
  private final PersonAccessInvitationRepository invitations;
  private final InstitutionalPasswordResetTokenRepository recoveries;
  private final PersonOwnAccessProvisioning provisioning;
  private final AccountAccessAuthorization access;
  private final SessionRevocationService sessions;
  private final RequestInstitutionalPasswordRecoveryUseCase recovery;
  private final AuditEventRecorder audit;
  private final Clock clock;

  Person lock(final UUID institutionId, final UUID personId) {
    final var institution =
        institutions
            .findByIdForUpdate(institutionId)
            .orElseThrow(InstitutionNotFoundException::new);
    if (!institution.isActive()) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_INSTITUTION_REQUIRED);
    }
    return people
        .findByIdAndInstitutionIdForUpdate(personId, institutionId)
        .filter(value -> !value.isDeleted())
        .orElseThrow(PersonNotFoundException::new);
  }

  @Transactional(readOnly = true)
  public OwnAccessResponse status(final UUID institutionId, final UUID personId) {
    final var person =
        people
            .findByIdAndInstitution_Id(personId, institutionId)
            .filter(value -> !value.isDeleted())
            .orElseThrow(PersonNotFoundException::new);
    access.requireManagement(person);
    return describe(person);
  }

  OwnAccessResponse describe(final Person person) {
    final var user =
        users.findByPerson_IdAndInstitution_Id(person.getId(), person.getInstitution().getId());
    final var invitation =
        invitations
            .findFirstByPerson_IdOrderByIssuedAtDesc(person.getId())
            .map(value -> OwnAccessResponse.Invitation.from(value, clock.instant()))
            .orElse(null);
    final String status =
        user.map(
                value ->
                    !value.isAccessEnabled()
                        ? "INSTITUTION_BLOCKED"
                        : !value.isSelfAccessEnabled()
                            ? "DISABLED"
                            : value.requiresEmailVerification() ? "INVITATION_PENDING" : "ENABLED")
            .orElse(
                invitation != null && "PENDING".equals(invitation.status())
                    ? "INVITATION_PENDING"
                    : "NO_ACCOUNT");
    return new OwnAccessResponse(status, invitation);
  }

  void issue(final Person person, final String email, final AccessInvitationPurpose purpose) {
    final var actor = access.currentActor();
    final var issuer =
        actor instanceof JwtAuthenticatedUser user
            ? people
                .findByIdAndInstitution_Id(user.actorPersonId(), user.institutionId())
                .orElseThrow(PersonNotFoundException::new)
            : null;
    final var platform =
        actor instanceof JwtAuthenticatedPlatformAccount account
            ? platforms
                .findById(account.platformAccountId())
                .orElseThrow(PersonNotFoundException::new)
            : null;
    final var invitation =
        provisioning.issueInvitation(person, null, issuer, platform, purpose, email);
    audit.recordCurrentActor(
        person.getInstitution(),
        person.getId(),
        AuditAction.PERSON_OWN_ACCESS_INVITED,
        AuditEntityType.PERSON_ACCESS_INVITATION,
        invitation.getId(),
        AuditOrigin.administration(),
        Map.of("purpose", purpose.name()));
  }

  @Transactional
  public OwnAccessResponse invite(
      final UUID institutionId, final UUID personId, final String email) {
    final var person = lock(institutionId, personId);
    access.requireManagement(person);
    final var user = users.findByPerson_IdAndInstitution_Id(personId, institutionId);
    final var latest = invitations.findFirstByPerson_IdOrderByIssuedAtDesc(personId);
    final boolean autonomy =
        user.map(value -> !value.isSelfAccessEnabled()).orElse(true)
            && latest
                .filter(
                    value ->
                        value.getPurpose() == AccessInvitationPurpose.AUTONOMY
                            && value.getConsumedAt() == null)
                .isPresent();
    if (user.isPresent() && !autonomy) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_ACCOUNT_EXISTS);
    }
    final var actor = access.currentActor();
    if (actor instanceof JwtAuthenticatedUser institutional
        && !access.canProvisionInstitutionally(
            people
                .findByIdAndInstitution_Id(institutional.actorPersonId(), institutionId)
                .orElseThrow(PersonNotFoundException::new),
            person)) {
      final var representation =
          links
              .findByInstitution_IdAndTutorPerson_IdAndDependentPerson_IdAndStatus(
                  institutionId, institutional.actorPersonId(), personId, GuardianLinkStatus.ACTIVE)
              .orElseThrow(
                  () ->
                      new OwnAccessException(InstitutionMessages.OWN_ACCESS_INSTITUTION_REQUIRED));
      if (autonomy || access.isStaff(person)) {
        throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_INSTITUTION_REQUIRED);
      }
      final var invitation = provisioning.issueInvitation(person, representation, null, email);
      audit.recordCurrentActor(
          person.getInstitution(),
          personId,
          AuditAction.PERSON_OWN_ACCESS_INVITED,
          AuditEntityType.PERSON_ACCESS_INVITATION,
          invitation.getId(),
          AuditOrigin.SELF_SERVICE,
          Map.of());
    } else {
      issue(
          person,
          email,
          autonomy ? AccessInvitationPurpose.AUTONOMY : AccessInvitationPurpose.INITIAL_ACCESS);
    }
    return describe(person);
  }

  @Transactional
  public void cancel(final UUID institutionId, final UUID personId) {
    final var person = lock(institutionId, personId);
    access.requireManagement(person);
    provisioning.cancelInvitations(personId);
    audit.recordCurrentActor(
        person.getInstitution(),
        personId,
        AuditAction.PERSON_OWN_ACCESS_INVITATION_CANCELLED,
        AuditEntityType.PERSON,
        personId,
        AuditOrigin.administration(),
        Map.of());
  }

  @Transactional
  public OwnAccessResponse enable(
      final UUID institutionId, final UUID personId, final boolean enabled) {
    final var person = lock(institutionId, personId);
    access.requireManagement(person);
    final var user =
        users
            .findByPerson_IdAndInstitution_Id(personId, institutionId)
            .orElseThrow(
                () -> new OwnAccessException(InstitutionMessages.OWN_ACCESS_EMAIL_REQUIRED));
    if (enabled
        && (!user.isAccessEnabled()
            || invitations
                .findFirstByPerson_IdOrderByIssuedAtDesc(personId)
                .filter(
                    value ->
                        value.getPurpose() == AccessInvitationPurpose.AUTONOMY
                            && value.getConsumedAt() == null)
                .isPresent())) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_INSTITUTION_REQUIRED);
    }
    user.updateSelfAccess(enabled);
    if (!enabled) {
      provisioning.cancelInvitations(personId);
      recoveries.deleteByUserId(user.getId());
      sessions.revokeInstitutionalSessionsForUser(user.getId());
    }
    audit.recordCurrentActor(
        person.getInstitution(),
        personId,
        AuditAction.PERSON_OWN_ACCESS_CHANGED,
        AuditEntityType.USER,
        user.getId(),
        AuditOrigin.administration(),
        Map.of("enabled", enabled));
    return describe(person);
  }

  @Transactional
  public void recover(final UUID institutionId, final UUID personId, final String currentPassword) {
    final var person = lock(institutionId, personId);
    access.requireManagement(person);
    access.reauthenticate(currentPassword);
    users
        .findByPerson_IdAndInstitution_Id(personId, institutionId)
        .ifPresent(recovery::requestForUser);
  }
}
