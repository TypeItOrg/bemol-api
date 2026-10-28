package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.events.AccountEmailMessageRequested;
import ar.edu.utn.frvm.typeit.bemol_api.auth.events.PersonAccessInvited;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.ActivateOwnAccessRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PasswordRecoveryRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.AccountEmailChangeUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.ActivatePersonOwnAccessUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.InstitutionalAccountManagementUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.InvitationMailOutcomeRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.PersonOwnAccessService;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.RequestInstitutionalPasswordRecoveryUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.PermissionDelegationNotAllowedException;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailSender;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.OwnAccessException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.UpdateOwnAccessRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.CreateGuardianDependentRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.GuardianDependentRequestMode;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.CreatePersonRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.CreatePersonUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.ListGuardianDependentsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.RegisterGuardianDependentUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.ResolveGuardianLinkUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.UnlinkGuardianDependentUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class AuthorizedAccountsPostgresIntegrationTest extends DatabaseMigrationTestSupport {
  private static final String PASSWORD = "DummyAccounts2026!";
  @Autowired CreatePersonUseCase create;

  @Autowired
  ar.edu.utn.frvm.typeit.bemol_api.institutional.services.UpdatePersonByAdminUseCase corrections;

  @Autowired ActivatePersonOwnAccessUseCase activate;
  @Autowired InstitutionalAccountManagementUseCase accounts;
  @Autowired InvitationMailOutcomeRecorder invitationMailOutcomes;
  @Autowired ar.edu.utn.frvm.typeit.bemol_api.auth.services.GrantPersonAutonomyUseCase autonomy;
  @Autowired AccountEmailChangeUseCase emails;
  @Autowired RegisterGuardianDependentUseCase requests;
  @Autowired ResolveGuardianLinkUseCase reviews;
  @Autowired UnlinkGuardianDependentUseCase unlink;
  @Autowired ListGuardianDependentsUseCase dependents;
  @Autowired PersonOwnAccessService ownAccess;
  @Autowired RequestInstitutionalPasswordRecoveryUseCase recovery;
  @Autowired UserRepository users;
  @Autowired PersonRepository people;
  @Autowired PasswordEncoder passwords;
  @Autowired StringRedisTemplate redis;
  @Autowired ApplicationEvents events;
  @Autowired MockMvc mvc;
  @Autowired org.springframework.transaction.support.TransactionTemplate transaction;
  @Autowired ar.edu.utn.frvm.typeit.bemol_api.auth.services.PlatformLoginUseCase platformLogin;

  @Autowired
  ar.edu.utn.frvm.typeit.bemol_api.auth.services.AuthenticationSessionIssuer sessionIssuer;

  @Autowired ar.edu.utn.frvm.typeit.bemol_api.auth.services.RefreshTokenUseCase refresh;
  @Autowired ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PasskeyCredentialRepository keys;
  @MockitoBean MailSender mail;
  UUID institution;
  String originalPlatformPassword;

  @BeforeEach
  void prepare() {
    institution = fixtures.firstInstitutionId();
    authenticatePlatformAdministrator();
    originalPlatformPassword =
        requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT password FROM platform_accounts WHERE email = 'admin@plataforma.com'",
                String.class));
    jdbcTemplate.update(
        "UPDATE platform_accounts SET password = ? WHERE email = 'admin@plataforma.com'",
        passwords.encode(PASSWORD));
  }

  @AfterEach
  void restorePlatformPassword() {
    jdbcTemplate.update(
        "UPDATE platform_accounts SET password = ? WHERE email = 'admin@plataforma.com'",
        originalPlatformPassword);
  }

  @ParameterizedTest
  @EnumSource(
      value = SystemRoleCode.class,
      names = {"APPLICANT", "REPRESENTATIVE"})
  void creationDefersUserUntilOneTimeActivationAndSignupNeverWrites(final SystemRoleCode role)
      throws Exception {
    final long before = count("SELECT count(*) FROM people");
    mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{}"))
        .andExpect(status().isNotFound());
    assertThat(count("SELECT count(*) FROM people")).isEqualTo(before);
    authenticatePlatformAdministrator();
    final UUID person = createPerson(role);
    assertThat(count("SELECT count(*) FROM users WHERE person_id = ?", person)).isZero();
    final String raw = invitation(person);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT token_hash FROM person_access_invitations WHERE person_id = ?",
                String.class,
                person))
        .isNotEqualTo(raw);
    anonymousActivation(raw);
    final UUID id = user(person).getId();
    assertThat(user(person).isEnabled()).isTrue();
    assertThatThrownBy(() -> anonymousActivation(raw)).isInstanceOf(OwnAccessException.class);
    assertThat(user(person).getId()).isEqualTo(id);
  }

  @Test
  void concurrentMailOutcomeCannotUndoInvitationCancellation() throws Exception {
    final UUID person = createPerson(SystemRoleCode.APPLICANT);
    final String token = invitation(person);
    final UUID id =
        requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT invitation_id FROM person_access_invitations WHERE person_id = ?",
                UUID.class,
                person));
    awaitCondition(
        () ->
            count(
                    "SELECT count(*) FROM person_access_invitations WHERE invitation_id = ? AND mail_outcome IS NOT NULL",
                    id)
                == 1);
    final String currentOutcome =
        requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT mail_outcome FROM person_access_invitations WHERE invitation_id = ?",
                String.class,
                id));
    final String newOutcome = "SENT".equals(currentOutcome) ? "FAILED" : "SENT";

    try (final var pool = Executors.newSingleThreadExecutor()) {
      final AtomicReference<Future<?>> result = new AtomicReference<>();
      transaction.executeWithoutResult(
          status -> {
            jdbcTemplate.queryForObject(
                "SELECT invitation_id FROM person_access_invitations WHERE invitation_id = ? FOR UPDATE",
                UUID.class,
                id);
            result.set(pool.submit(() -> invitationMailOutcomes.record(id, newOutcome)));
            awaitCondition(
                () -> {
                  jdbcTemplate.execute("SELECT pg_stat_clear_snapshot()");
                  return count(
                          "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock' AND lower(query) LIKE '%update person_access_invitations%'")
                      > 0;
                });
            accounts.cancel(institution, person);
          });
      requireNonNull(result.get()).get(30, TimeUnit.SECONDS);
    }

    assertThat(
            count(
                "SELECT count(*) FROM person_access_invitations WHERE invitation_id = ? AND cancelled_at IS NOT NULL AND mail_outcome = ?",
                id,
                newOutcome))
        .isEqualTo(1);
    assertThatThrownBy(() -> anonymousActivation(token)).isInstanceOf(OwnAccessException.class);
    assertThat(count("SELECT count(*) FROM users WHERE person_id = ?", person)).isZero();
  }

  @Test
  void cancellationResendExpiryAndConcurrentConsumptionAreSafe() throws Exception {
    final UUID person = createPerson(SystemRoleCode.APPLICANT);
    final String old = invitation(person);
    accounts.cancel(institution, person);
    assertThatThrownBy(() -> anonymousActivation(old)).isInstanceOf(OwnAccessException.class);
    allowMail(person);
    accounts.invite(institution, person, "shared@example.test");
    final String replacement = invitation(person);
    assertThat(replacement).isNotEqualTo(old);
    assertThatThrownBy(() -> anonymousActivation(old)).isInstanceOf(OwnAccessException.class);
    try (final var pool = Executors.newFixedThreadPool(2)) {
      final Callable<Boolean> consume =
          () -> {
            try {
              anonymousActivation(replacement);
              return true;
            } catch (OwnAccessException exception) {
              return false;
            }
          };
      final var results = pool.invokeAll(List.of(consume, consume));
      assertThat(List.of(results.get(0).get(), results.get(1).get()))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(count("SELECT count(*) FROM users WHERE person_id = ?", person)).isEqualTo(1);
    authenticatePlatformAdministrator();
    final UUID expired = createPerson(SystemRoleCode.APPLICANT);
    jdbcTemplate.update(
        "UPDATE person_access_invitations SET expires_at = now() - interval '1 second' WHERE person_id = ?",
        expired);
    assertThatThrownBy(() -> anonymousActivation(invitation(expired)))
        .isInstanceOf(OwnAccessException.class);
  }

  @Test
  void requestsRequirePermissionStageIdentityAndThrottleWithoutReservingDocuments() {
    final UUID applicant = createActivated(SystemRoleCode.APPLICANT);
    as(applicant);
    final String deniedDoc = fixtures.randomDocumentNumber();
    assertThatThrownBy(() -> requests.execute(institution, applicant, request(deniedDoc)))
        .isInstanceOf(PermissionDelegationNotAllowedException.class);
    authenticatePlatformAdministrator();
    final UUID tutor = createActivated(SystemRoleCode.INSTITUTIONAL_AUTHORITY);
    as(tutor);
    final String document = fixtures.randomDocumentNumber();
    final var pending = requests.execute(institution, tutor, request(document));
    assertThat(pending.dependentPersonId()).isNull();
    assertThat(
            count(
                "SELECT count(*) FROM people WHERE institution_id = ? AND document_number = ?",
                institution,
                document))
        .isZero();
    assertThatThrownBy(() -> requests.execute(institution, tutor, request(document)))
        .isInstanceOf(
            ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions
                .DependentAlreadyLinkedException.class);
    assertThat(dependents.execute(institution, tutor))
        .extracting(value -> value.personGuardianId())
        .contains(pending.personGuardianId());
    for (int i = 0; i < 4; i++) {
      requests.execute(institution, tutor, request(fixtures.randomDocumentNumber()));
    }
    assertThatThrownBy(
            () -> requests.execute(institution, tutor, request(fixtures.randomDocumentNumber())))
        .isInstanceOf(
            ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.GuardianRequestLimitException
                .class);
    unlink.cancelRequest(institution, tutor, pending.personGuardianId());
    assertThat(
            count(
                "SELECT count(*) FROM people WHERE institution_id = ? AND document_number = ?",
                institution,
                document))
        .isZero();
  }

  @Test
  void concurrentApprovalsReuseOnePersonWithoutOverwritingDeclaredIdentity() throws Exception {
    final UUID tutor1 = createActivated(SystemRoleCode.INSTITUTIONAL_AUTHORITY);
    authenticatePlatformAdministrator();
    final UUID tutor2 = createActivated(SystemRoleCode.INSTITUTIONAL_AUTHORITY);
    final String doc = fixtures.randomDocumentNumber();
    as(tutor1);
    final UUID first = requests.execute(institution, tutor1, request(doc)).personGuardianId();
    as(tutor2);
    final UUID second = requests.execute(institution, tutor2, request(doc)).personGuardianId();
    try (final var pool = Executors.newFixedThreadPool(2)) {
      final var results =
          pool.invokeAll(
              List.of(
                  (Callable<UUID>)
                      () -> {
                        authenticatePlatformAdministrator();
                        return requireNonNull(
                            reviews.approveForPlatform(institution, first).dependent().personId());
                      },
                  (Callable<UUID>)
                      () -> {
                        authenticatePlatformAdministrator();
                        return requireNonNull(
                            reviews.approveForPlatform(institution, second).dependent().personId());
                      }));
      assertThat(results.get(0).get()).isEqualTo(results.get(1).get());
    }
    assertThat(
            count(
                "SELECT count(*) FROM people WHERE institution_id = ? AND document_number = ? AND NOT deleted",
                institution,
                doc))
        .isEqualTo(1);
  }

  @Test
  void identityCollisionRequiresExplicitReviewAndRejectionDoesNotCreateAnyone() {
    final UUID tutor = createActivated(SystemRoleCode.INSTITUTIONAL_AUTHORITY);
    as(tutor);
    final String doc = fixtures.randomDocumentNumber();
    final UUID pending = requests.execute(institution, tutor, request(doc)).personGuardianId();
    authenticatePlatformAdministrator();
    final UUID canonical = UUID.randomUUID();
    fixtures.insertPerson(canonical, institution, doc, false);
    assertThatThrownBy(() -> reviews.approveForPlatform(institution, pending))
        .isInstanceOf(OwnAccessException.class);
    final var approved = reviews.approveForPlatform(institution, pending, canonical);
    assertThat(approved.dependent().personId()).isEqualTo(canonical);
    assertThat(approved.dependent().firstName()).isNotEqualTo("Persona");
    as(tutor);
    final String rejectedDoc = fixtures.randomDocumentNumber();
    final UUID rejected =
        requests.execute(institution, tutor, request(rejectedDoc)).personGuardianId();
    authenticatePlatformAdministrator();
    reviews.rejectForPlatform(institution, rejected);
    assertThat(
            count(
                "SELECT count(*) FROM people WHERE document_number = ? AND institution_id = ?",
                rejectedDoc,
                institution))
        .isZero();
  }

  @Test
  void twoTutorsCanSuspendAndEnableButCannotChangeStaffOrLiftInstitutionalBlock() {
    final UUID tutor1 = createActivated(SystemRoleCode.APPLICANT);
    authenticatePlatformAdministrator();
    final UUID tutor2 = createActivated(SystemRoleCode.APPLICANT);
    authenticatePlatformAdministrator();
    final UUID child = createActivated(SystemRoleCode.APPLICANT);
    final UUID link1 = link(tutor1, child);
    final UUID link2 = link(tutor2, child);
    final var personalSession =
        sessionIssuer.issue(user(child), "127.0.0.1", "QA", false, "PASSWORD");
    final var key = key(child);
    as(tutor1);
    ownAccess.update(institution, tutor1, link1, new UpdateOwnAccessRequest(false, null));
    assertThat(user(child).isEnabled()).isFalse();
    assertThat(
            count(
                "SELECT count(*) FROM user_sessions WHERE user_id=? AND active",
                user(child).getId()))
        .isZero();
    assertThat(
            count(
                "SELECT count(*) FROM refresh_tokens r JOIN user_sessions s ON s.user_session_id=r.session_id WHERE s.user_id=? AND NOT r.revoked",
                user(child).getId()))
        .isZero();
    assertThat(keys.findById(key).orElseThrow().isActive()).isTrue();
    assertThatThrownBy(
            () ->
                refresh.execute(
                    new ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.RefreshTokenRequest(
                        personalSession.tokens().refreshToken())))
        .isInstanceOf(
            ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.RefreshTokenReuseException.class);

    as(tutor2);
    ownAccess.update(institution, tutor2, link2, new UpdateOwnAccessRequest(true, null));
    assertThat(user(child).isEnabled()).isTrue();
    jdbcTemplate.update("UPDATE users SET enabled = false WHERE person_id = ?", child);
    assertThatThrownBy(
            () ->
                ownAccess.update(
                    institution, tutor2, link2, new UpdateOwnAccessRequest(true, null)))
        .isInstanceOf(OwnAccessException.class);
    authenticatePlatformAdministrator();
    final UUID teacher = createActivated(SystemRoleCode.TEACHER);
    final UUID staffLink = link(tutor1, teacher);
    as(tutor1);
    assertThatThrownBy(
            () ->
                ownAccess.update(
                    institution, tutor1, staffLink, new UpdateOwnAccessRequest(false, null)))
        .isInstanceOf(PermissionDelegationNotAllowedException.class);
  }

  @Test
  void emailChangeIsPendingRechecksInitiatorAndInvalidatesEarlierTokens() {
    final UUID owner = createActivated(SystemRoleCode.APPLICANT);
    as(owner);
    allowMail(owner);
    final String previous = user(owner).getPerson().getEmail();
    emails.request(institution, owner, "alternative@example.test", PASSWORD);
    final String old = emailToken(owner);
    assertThat(user(owner).getPerson().getEmail()).isEqualTo(previous);
    allowMail(owner);
    emails.request(institution, owner, "shared@example.test", PASSWORD);
    final String raw = emailToken(owner);
    assertThatThrownBy(() -> emails.confirm(old)).isInstanceOf(OwnAccessException.class);
    emails.confirm(raw);
    assertThat(user(owner).getPerson().getEmail()).isEqualTo("shared@example.test");
    assertThatThrownBy(() -> emails.confirm(raw)).isInstanceOf(OwnAccessException.class);
    assertThat(
            events.stream(AccountEmailMessageRequested.class)
                .anyMatch(value -> value.email().equals(previous) && value.token() == null))
        .isTrue();
  }

  @Test
  void autonomyEndsAllLinksKeepsUserAndRequiresIndependentTakeoverActivation() {
    final UUID tutor = createActivated(SystemRoleCode.INSTITUTIONAL_AUTHORITY);
    authenticatePlatformAdministrator();
    final UUID child = createActivated(SystemRoleCode.APPLICANT);
    link(tutor, child);
    final UUID userId = user(child).getId();
    final UUID key = key(child);
    authenticatePlatformAdministrator();
    allowMail(child);
    autonomy.execute(institution, child, "autonomy@example.test", PASSWORD);
    assertThat(
            count(
                "SELECT count(*) FROM person_guardians WHERE dependent_person_id = ? AND status IN ('ACTIVE','PENDING')",
                child))
        .isZero();
    assertThat(user(child).isEnabled()).isFalse();
    assertThat(keys.findById(key).orElseThrow().isActive()).isFalse();
    assertThatThrownBy(() -> anonymousActivation(invitation(child)))
        .isInstanceOf(OwnAccessException.class);
    activate.execute(
        new ActivateOwnAccessRequest(
            invitation(child), "RenewedDummyPassword2026!", "RenewedDummyPassword2026!"));
    assertThat(passwords.matches(PASSWORD, user(child).getPassword())).isFalse();
    assertThat(passwords.matches("RenewedDummyPassword2026!", user(child).getPassword())).isTrue();
    assertThat(user(child).getId()).isEqualTo(userId);
    assertThat(user(child).getPerson().getEmail()).isEqualTo("autonomy@example.test");
    assertThat(user(child).isEnabled()).isTrue();
    assertThat(
            count(
                "SELECT count(*) FROM person_guardians WHERE dependent_person_id = ? AND status = 'ENDED'",
                child))
        .isEqualTo(1);
  }

  @Test
  void selectedPrivilegedIdentityDoesNotGrantActorPermissions() throws Exception {
    final UUID low = createActivated(SystemRoleCode.APPLICANT);
    authenticatePlatformAdministrator();
    final UUID high = createActivated(SystemRoleCode.INSTITUTIONAL_AUTHORITY);
    link(low, high);
    final var root = user(low);
    final var subject = user(high);
    final var delegated =
        new JwtAuthenticatedUser(
            subject.getId(),
            high,
            subject.getDocumentNumber(),
            institution,
            UUID.randomUUID(),
            "dummy",
            root.getId(),
            low);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new TestingAuthenticationToken(delegated, "", "ROLE_INSTITUTIONAL_USER"));
    assertThatThrownBy(() -> accounts.invite(institution, high, "attacker@example.test"))
        .isInstanceOf(PermissionDelegationNotAllowedException.class);
    assertThatThrownBy(
            () -> requests.execute(institution, low, request(fixtures.randomDocumentNumber())))
        .isInstanceOf(PermissionDelegationNotAllowedException.class);
    final String bearer =
        sessionIssuer.issue(root, "127.0.0.1", "QA", false, "PASSWORD").tokens().accessToken();
    jdbcTemplate.update(
        "UPDATE user_sessions SET active_person_id=? WHERE user_id=?", high, root.getId());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/institutions/{institutionId}/guardian-links", institution)
                .header("Authorization", "Bearer " + bearer))
        .andExpect(status().isOk());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/api/v1/institutions/{institutionId}/people/{personId}/status",
                    institution,
                    high)
                .header("Authorization", "Bearer " + bearer)
                .contentType("application/json")
                .content("{\"enabled\":true}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/auth/passkeys/registration/options")
                .header("Authorization", "Bearer " + bearer)
                .contentType("application/json")
                .content("{\"label\":\"unauthorized\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void guardianLinkReviewFollowsSelectedIdentityNotActor() throws Exception {
    final UUID actor = createActivated(SystemRoleCode.INSTITUTIONAL_AUTHORITY);
    authenticatePlatformAdministrator();
    final UUID selected = createActivated(SystemRoleCode.APPLICANT);
    link(actor, selected);
    final var account = user(actor);
    final String bearer =
        sessionIssuer.issue(account, "127.0.0.1", "QA", false, "PASSWORD").tokens().accessToken();
    jdbcTemplate.update(
        "UPDATE user_sessions SET active_person_id=? WHERE user_id=?", selected, account.getId());

    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/institutions/{institutionId}/guardian-links", institution)
                .header("Authorization", "Bearer " + bearer))
        .andExpect(status().isForbidden());
  }

  @Test
  void accountRoutesEnforcePanelBoundaryAndExplicitBoolean() throws Exception {
    final UUID tutor = createActivated(SystemRoleCode.APPLICANT);
    final UUID child = createActivated(SystemRoleCode.APPLICANT);
    link(tutor, child);
    as(tutor);
    final String bearer =
        sessionIssuer
            .issue(user(tutor), "127.0.0.1", "QA", false, "PASSWORD")
            .tokens()
            .accessToken();
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/institutions/{institutionId}/people/{personId}/access",
                    institution,
                    child)
                .header("Authorization", "Bearer " + bearer))
        .andExpect(status().isOk());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/admin/institutions/{institutionId}/people/{personId}/access",
                    institution,
                    child)
                .header("Authorization", "Bearer " + bearer))
        .andExpect(status().isForbidden());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/api/v1/institutions/{institutionId}/people/{personId}/own-access",
                    institution,
                    child)
                .header("Authorization", "Bearer " + bearer)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
    assertThat(user(child).isSelfAccessEnabled()).isTrue();
    final String platformBearer =
        platformLogin
            .execute(
                new ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PlatformLoginRequest(
                    "admin@plataforma.com", PASSWORD, false),
                new org.springframework.mock.web.MockHttpServletRequest())
            .tokens()
            .accessToken();
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/admin/institutions/{institutionId}/people/{personId}/access",
                    institution,
                    child)
                .header("Authorization", "Bearer " + platformBearer))
        .andExpect(status().isOk());
  }

  @Test
  void expiredEmailChangeCannotReplaceCurrentMailbox() {
    final UUID person = createActivated(SystemRoleCode.APPLICANT);
    as(person);
    allowMail(person);
    final String current = user(person).getPerson().getEmail();
    assertThatThrownBy(
            () -> emails.request(institution, person, "pending@example.test", "WrongPassword2026!"))
        .isInstanceOf(
            ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException.class);
    emails.request(institution, person, "pending@example.test", PASSWORD);
    final String token = emailToken(person);
    jdbcTemplate.update(
        "UPDATE account_email_change_tokens SET expires_at=now()-interval '1 second' WHERE user_id=?",
        user(person).getId());
    assertThatThrownBy(() -> emails.confirm(token)).isInstanceOf(OwnAccessException.class);
    assertThat(user(person).getPerson().getEmail()).isEqualTo(current);
  }

  @Test
  void recoveryCannotUnsuspendPersonalAccess() {
    final UUID person = createActivated(SystemRoleCode.APPLICANT);
    jdbcTemplate.update("UPDATE users SET self_access_enabled = false WHERE person_id = ?", person);
    recovery.execute(new PasswordRecoveryRequest(user(person).getDocumentNumber(), institution));
    assertThat(user(person).isEnabled()).isFalse();
    assertThat(
            count(
                "SELECT count(*) FROM institutional_password_reset_tokens WHERE user_id = ?",
                user(person).getId()))
        .isZero();
  }

  @Test
  void approvalRollsBackPersonRolesAndLinkAndCannotBeSelfApproved() {
    final UUID tutor = createActivated(SystemRoleCode.INSTITUTIONAL_AUTHORITY);
    as(tutor);
    final String doc = fixtures.randomDocumentNumber();
    final UUID pending = requests.execute(institution, tutor, request(doc)).personGuardianId();
    assertThatThrownBy(() -> reviews.approve(institution, tutor, pending))
        .isInstanceOf(OwnAccessException.class);
    authenticatePlatformAdministrator();
    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    state -> {
                      reviews.approveForPlatform(institution, pending);
                      throw new IllegalStateException("Rollback fixture");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            count(
                "SELECT count(*) FROM people WHERE institution_id=? AND document_number=?",
                institution,
                doc))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM person_guardians WHERE person_guardian_id=?",
                String.class,
                pending))
        .isEqualTo("PENDING");
    assertThat(
            count(
                "SELECT count(*) FROM person_guardians WHERE person_guardian_id=? AND dependent_person_id IS NOT NULL",
                pending))
        .isZero();
  }

  @Test
  void guardianEmailConfirmationFailsAfterRevocationAndAcrossInstitutions() {
    final UUID tutor = createActivated(SystemRoleCode.APPLICANT);
    final UUID child = createActivated(SystemRoleCode.APPLICANT);
    final UUID link = link(tutor, child);
    as(tutor);
    allowMail(child);
    final String previous = user(child).getPerson().getEmail();
    emails.request(institution, child, "pending@example.test", PASSWORD);
    final String token = emailToken(child);
    unlink.execute(institution, tutor, child);
    assertThatThrownBy(() -> emails.confirm(token)).isInstanceOf(OwnAccessException.class);
    assertThat(user(child).getPerson().getEmail()).isEqualTo(previous);
    final UUID other = UUID.randomUUID();
    fixtures.insertTestInstitution(other);
    assertThatThrownBy(() -> accounts.status(other, child))
        .isInstanceOf(
            ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonNotFoundException
                .class);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM person_guardians WHERE person_guardian_id=?",
                String.class,
                link))
        .isEqualTo("ENDED");
  }

  @Test
  void documentCorrectionPreservesIdentityAndRevokesPersonalSessions() {
    final UUID person = createActivated(SystemRoleCode.APPLICANT);
    final UUID id = user(person).getId();
    final String nextDocument = fixtures.randomDocumentNumber();
    sessionIssuer.issue(user(person), "127.0.0.1", "QA", false, "PASSWORD");
    authenticatePlatformAdministrator();
    corrections.execute(
        institution,
        person,
        new ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person
            .UpdatePersonByAdminRequest(null, null, nextDocument, null, null));
    assertThat(user(person).getId()).isEqualTo(id);
    assertThat(user(person).getDocumentNumber()).isEqualTo(nextDocument);
    assertThat(count("SELECT count(*) FROM user_sessions WHERE user_id=? AND active", id)).isZero();
    final String details =
        jdbcTemplate.queryForObject(
            "SELECT details::text FROM audit_events WHERE subject_person_id=? AND action='PERSON_UPDATED'",
            String.class,
            person);
    assertThat(details).contains("documentNumber").doesNotContain(nextDocument);
  }

  private UUID key(final UUID person) {
    return keys.saveAndFlush(
            ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PasskeyCredential.builder()
                .user(user(person))
                .credentialId(UUID.randomUUID().toString())
                .publicKeyCose(new byte[] {1, 2})
                .userHandle(new byte[] {1, 2, 3})
                .label("QA key")
                .build())
        .getId();
  }

  private UUID createPerson(final SystemRoleCode role) {
    return create
        .execute(
            institution,
            new CreatePersonRequest(
                "Persona",
                "Pruebas",
                fixtures.randomDocumentNumber(),
                UUID.randomUUID() + "@example.test",
                null,
                LocalDate.of(2000, 1, 1),
                role))
        .personId();
  }

  private UUID createActivated(final SystemRoleCode role) {
    authenticatePlatformAdministrator();
    final UUID person = createPerson(role);
    anonymousActivation(invitation(person));
    authenticatePlatformAdministrator();
    return person;
  }

  private void anonymousActivation(final String raw) {
    final var previous = SecurityContextHolder.getContext().getAuthentication();
    SecurityContextHolder.clearContext();
    try {
      activate.execute(new ActivateOwnAccessRequest(raw, PASSWORD, PASSWORD));
    } finally {
      SecurityContextHolder.getContext().setAuthentication(previous);
    }
  }

  private String invitation(final UUID person) {
    return events.stream(PersonAccessInvited.class)
        .filter(value -> value.personId().equals(person))
        .reduce((left, right) -> right)
        .orElseThrow()
        .token();
  }

  private String emailToken(final UUID person) {
    return requireNonNull(
        events.stream(AccountEmailMessageRequested.class)
            .filter(value -> value.personId().equals(person) && value.token() != null)
            .reduce((left, right) -> right)
            .orElseThrow()
            .token());
  }

  private User user(final UUID person) {
    final UUID id =
        requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE person_id = ?", UUID.class, person));
    return users.findWithPersonAndInstitutionById(id).orElseThrow();
  }

  private void as(final UUID person) {
    final var user = user(person);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new TestingAuthenticationToken(
                new JwtAuthenticatedUser(
                    user.getId(),
                    person,
                    user.getDocumentNumber(),
                    institution,
                    UUID.randomUUID(),
                    "dummy"),
                "",
                "ROLE_INSTITUTIONAL_USER"));
  }

  private UUID link(final UUID tutor, final UUID child) {
    as(tutor);
    final String doc = user(child).getDocumentNumber();
    // This helper also supports a low-privilege actor without granting it request permission.
    final UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO person_guardians(person_guardian_id,institution_id,tutor_person_id,dependent_person_id,declared_document_number,request_mode,relationship,status,created_at,updated_at) VALUES(?,?,?,?,?,'EXISTING','LEGAL_GUARDIAN','ACTIVE',now(),now())",
        id,
        institution,
        tutor,
        child,
        doc);
    return id;
  }

  private void allowMail(final UUID person) {
    redis.delete("bemol:auth:action:mail:" + person);
  }

  private long count(final String sql, final Object... args) {
    return requireNonNull(jdbcTemplate.queryForObject(sql, Long.class, args));
  }

  private void awaitCondition(final BooleanSupplier condition) {
    final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
    }
    assertThat(condition.getAsBoolean()).isTrue();
  }

  private CreateGuardianDependentRequest request(final String document) {
    return new CreateGuardianDependentRequest(
        document,
        "Persona",
        "Declarada",
        LocalDate.of(2016, 1, 1),
        GuardianRelationship.LEGAL_GUARDIAN,
        false,
        null,
        false,
        GuardianDependentRequestMode.NEW);
  }
}
