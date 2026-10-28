package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.enums.AccessInvitationPurpose;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.AccountEmailChangeTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.InstitutionalPasswordResetTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionMessages;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.OwnAccessException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.OwnAccessResponse;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GrantPersonAutonomyUseCase {
  private final InstitutionalAccountManagementUseCase accounts;
  private final UserRepository users;
  private final PersonGuardianRepository links;
  private final AccountEmailChangeTokenRepository emailChanges;
  private final InstitutionalPasswordResetTokenRepository recoveries;
  private final PasskeyCredentialRepository passkeys;
  private final PersonOwnAccessProvisioning provisioning;
  private final AccountAccessAuthorization access;
  private final SessionRevocationService sessions;
  private final AuditEventRecorder audit;
  private final Clock clock;

  @Transactional
  public OwnAccessResponse execute(
      final UUID institutionId,
      final UUID personId,
      final String email,
      final String currentPassword) {
    final var person = accounts.lock(institutionId, personId);
    access.requireAdministration(person);
    access.reauthenticate(currentPassword);
    if (access.currentActor() instanceof JwtAuthenticatedUser actor) {
      if (actor.actorPersonId().equals(personId)) {
        throw new OwnAccessException(InstitutionMessages.AUTONOMY_OTHER_ADMIN_REQUIRED);
      }
      access.requirePermission(
          institutionId, actor.actorPersonId(), PermissionCode.GUARDIAN_LINK_REVIEW);
    }
    links
        .findRepresentationsForPerson(
            institutionId,
            personId,
            person.getDocumentNumber(),
            List.of(GuardianLinkStatus.ACTIVE, GuardianLinkStatus.PENDING))
        .forEach(link -> link.end());
    provisioning.cancelInvitations(personId);
    users
        .findByPerson_IdAndInstitution_Id(personId, institutionId)
        .ifPresent(
            user -> {
              user.updateSelfAccess(false);
              recoveries.deleteByUserId(user.getId());
              emailChanges.deleteByUser_Id(user.getId());
              passkeys.findActiveByUserId(user.getId()).forEach(key -> key.revoke(clock.instant()));
              sessions.revokeInstitutionalSessionsForUser(user.getId());
            });
    accounts.issue(person, email, AccessInvitationPurpose.AUTONOMY);
    audit.recordCurrentActor(
        person.getInstitution(),
        personId,
        AuditAction.PERSON_AUTONOMY_GRANTED,
        AuditEntityType.PERSON,
        personId,
        AuditOrigin.administration(),
        Map.of());
    return accounts.describe(person);
  }
}
