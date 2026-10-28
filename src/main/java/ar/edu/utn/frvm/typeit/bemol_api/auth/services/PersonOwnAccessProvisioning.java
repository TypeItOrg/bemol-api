package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PersonAccessInvitation;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.events.PersonAccessInvited;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PersonAccessInvitationRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailAuditContext;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian;
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
public class PersonOwnAccessProvisioning {
  private static final Set<String> INSTITUTIONAL_ROLE_CODES =
      Set.of(
          SystemRoleCode.INSTITUTIONAL_AUTHORITY.name(),
          SystemRoleCode.ADMINISTRATIVE.name(),
          SystemRoleCode.TEACHER.name());
  private static final Set<String> ACADEMIC_PERMISSION_CODES =
      Set.of(
          PermissionCode.ACADEMIC_OFFER_READ.getCode(),
          PermissionCode.STUDY_PLAN_READ.getCode(),
          PermissionCode.ACADEMIC_YEAR_READ.getCode(),
          PermissionCode.GUARDIAN_LINK_REQUEST.getCode());

  private final PersonRoleAssignmentRepository roles;
  private final PersonAccessInvitationRepository invitations;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final AccountActionRateLimiter limiter;
  private final SecureRandom random = new SecureRandom();

  void cancelInvitations(final UUID personId) {
    invitations.findByPerson_Id(personId).forEach(i -> i.cancel(clock.instant()));
    invitations.flush();
  }

  PersonAccessInvitation issueInvitation(
      final Person person,
      final @Nullable PersonGuardian representation,
      final @Nullable Person issuer,
      final String email) {
    return issueInvitation(
        person,
        representation,
        issuer,
        null,
        ar.edu.utn.frvm.typeit.bemol_api.auth.enums.AccessInvitationPurpose.INITIAL_ACCESS,
        email);
  }

  PersonAccessInvitation issueInvitation(
      final Person person,
      final @Nullable PersonGuardian representation,
      final @Nullable Person issuer,
      final @Nullable PlatformAccount platform,
      final ar.edu.utn.frvm.typeit.bemol_api.auth.enums.AccessInvitationPurpose purpose,
      final String email) {
    limiter.mail(person.getId());
    cancelInvitations(person.getId());
    final byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    final String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    final String hash = RequestInstitutionalPasswordRecoveryUseCase.hash(raw);
    final var now = clock.instant();
    final var expiresAt = now.plus(24, ChronoUnit.HOURS);
    final var invitation =
        invitations.saveAndFlush(
            representation != null
                ? PersonAccessInvitation.issue(representation, email, hash, expiresAt)
                : PersonAccessInvitation.issueAdministratively(
                    person, issuer, platform, purpose, email, hash, now, expiresAt));
    events.publishEvent(
        new PersonAccessInvited(
            person.getId(),
            email,
            person.getInstitution().getName(),
            person.getFirstName() + " " + person.getLastName(),
            raw,
            person.getInstitution().getPublicSubdomain(),
            person.getInstitution().getLogoVersion(),
            MailAuditContext.capture(person.getInstitution().getId(), person.getId())
                .forInvitation(invitation.getId()),
            invitation.getId()));
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
