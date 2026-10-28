package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.AccountEmailChangeToken;
import ar.edu.utn.frvm.typeit.bemol_api.auth.events.AccountEmailMessageRequested;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.AccountEmailChangeTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.InstitutionalPasswordResetTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailAuditContext;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionMessages;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.OwnAccessException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountEmailChangeUseCase {
  private final InstitutionRepository institutions;
  private final PersonRepository people;
  private final UserRepository users;
  private final PlatformAccountRepository platforms;
  private final AccountEmailChangeTokenRepository tokens;
  private final InstitutionalPasswordResetTokenRepository recoveries;
  private final AccountAccessAuthorization access;
  private final AccountActionRateLimiter limiter;
  private final InstitutionalAccountManagementUseCase accounts;
  private final SessionRevocationService sessions;
  private final InstitutionalHostContext host;
  private final ApplicationEventPublisher events;
  private final AuditEventRecorder audit;
  private final Clock clock;
  private final EntityManager entityManager;
  private final SecureRandom random = new SecureRandom();

  @Transactional
  public void request(
      final UUID institutionId,
      final UUID personId,
      final String email,
      final String currentPassword) {
    institutions.findByIdForUpdate(institutionId).orElseThrow(InstitutionNotFoundException::new);
    final var person =
        people
            .findByIdAndInstitutionIdForUpdate(personId, institutionId)
            .filter(value -> !value.isDeleted())
            .orElseThrow(PersonNotFoundException::new);
    access.requireManagement(person);
    access.reauthenticate(currentPassword);
    final var user = users.findByPerson_IdAndInstitution_Id(personId, institutionId);
    if (user.isEmpty()) {
      accounts.invite(institutionId, personId, email);
      return;
    }
    limiter.mail(personId);
    final var actor = access.currentActor();
    final var actorPerson =
        actor instanceof JwtAuthenticatedUser institutional
            ? people
                .findByIdAndInstitution_Id(institutional.actorPersonId(), institutionId)
                .orElseThrow(PersonNotFoundException::new)
            : null;
    final var actorPlatform =
        actor instanceof JwtAuthenticatedPlatformAccount platform
            ? platforms
                .findById(platform.platformAccountId())
                .orElseThrow(PersonNotFoundException::new)
            : null;
    final byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    final String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    tokens.deleteByUser_Id(user.get().getId());
    tokens.flush();
    tokens.saveAndFlush(
        AccountEmailChangeToken.issue(
            user.get(),
            actorPerson,
            actorPlatform,
            email,
            RequestInstitutionalPasswordRecoveryUseCase.hash(raw),
            clock.instant(),
            clock.instant().plus(Duration.ofHours(24))));
    publish(person, email, raw);
    audit.recordCurrentActor(
        person.getInstitution(),
        personId,
        AuditAction.ACCOUNT_EMAIL_CHANGE_REQUESTED,
        AuditEntityType.USER,
        user.get().getId(),
        AuditOrigin.SELF_SERVICE,
        Map.of());
  }

  @Transactional
  public void confirm(final String raw) {
    final String hash = RequestInstitutionalPasswordRecoveryUseCase.hash(raw);
    final UUID personId =
        tokens
            .findPersonIdByHash(hash)
            .orElseThrow(
                () -> new OwnAccessException(InstitutionMessages.OWN_ACCESS_TOKEN_INVALID));
    final var initial = people.findById(personId).orElseThrow(PersonNotFoundException::new);
    final UUID institutionId = initial.getInstitution().getId();
    host.requireInstitution(institutionId);
    final var institution =
        institutions
            .findByIdForUpdate(institutionId)
            .orElseThrow(InstitutionNotFoundException::new);
    final var token =
        tokens
            .findByTokenHash(hash)
            .orElseThrow(
                () -> new OwnAccessException(InstitutionMessages.OWN_ACCESS_TOKEN_INVALID));
    final var user = token.getUser();
    entityManager.refresh(user);
    final var person = user.getPerson();
    entityManager.refresh(person, LockModeType.PESSIMISTIC_WRITE);
    final var actorPerson = token.getActorPerson();
    final var platform = token.getActorPlatformAccount();
    if (!institution.isActive()
        || person.isDeleted()
        || (actorPerson != null && !access.canManageInstitutionally(actorPerson.getId(), person))
        || (actorPerson == null
            && (platform == null || !access.isPlatformAdministrator(platform.getId())))) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_TOKEN_INVALID);
    }
    token.consume(clock.instant());
    final String previous = token.getPreviousEmail();
    person.updateContact(token.getNewEmail(), person.getPhoneNumber());
    user.confirmAccountEmail(clock.instant());
    recoveries.deleteByUserId(user.getId());
    sessions.revokeInstitutionalSessionsForUser(user.getId());
    publish(person, previous, null);
    audit.recordCurrentActor(
        institution,
        personId,
        AuditAction.INSTITUTIONAL_EMAIL_CHANGED,
        AuditEntityType.USER,
        user.getId(),
        AuditOrigin.EMAIL_VERIFICATION,
        Map.of(
            "changedFields",
            java.util.List.of("email"),
            "initiatorKind",
            actorPerson != null ? "INSTITUTION" : "PLATFORM",
            "initiatorId",
            actorPerson != null
                ? actorPerson.getId().toString()
                : java.util.Objects.requireNonNull(platform).getId().toString()));
  }

  private void publish(
      final Person person,
      final String email,
      final @org.jspecify.annotations.Nullable String token) {
    events.publishEvent(
        new AccountEmailMessageRequested(
            person.getId(),
            email,
            person.getFirstName() + " " + person.getLastName(),
            person.getInstitution().getName(),
            token,
            person.getInstitution().getPublicSubdomain(),
            person.getInstitution().getLogoVersion(),
            MailAuditContext.capture(person.getInstitution().getId(), person.getId())));
  }
}
