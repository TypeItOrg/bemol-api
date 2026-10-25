package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditModule;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.events.InstitutionalEmailVerificationRequested;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.ConfirmEmailVerificationRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.CreatePlatformAccountRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.RegisterRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.UpdatePlatformAccountRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.CreatePlatformAccountUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.InstitutionalEmailVerificationUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.RegisterUserUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.UpdatePlatformAccountStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.UpdatePlatformAccountUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.AccessScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.AssignRoleRequest;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.InstitutionRoleRequest;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.ReplacePersonRolesRequest;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionRoleManagementService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ReplacePersonRolesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailSender;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.CreatePersonRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.UpdatePersonByAdminRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.UpdatePersonRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.CreatePersonUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.DeletePersonUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.UpdateInstitutionalUserStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.UpdatePersonByAdminUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.UpdatePersonUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@IntegrationTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class GlobalAuditPostgresIntegrationTest extends DatabaseMigrationTestSupport {
  private static final String BASE = "/api/v1/admin/audit-events";
  private static final String PASSWORD = "DummyPassword123!";
  private static final Instant EVENT_TIME = Instant.parse("2026-10-17T12:00:00Z");

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired CreatePlatformAccountUseCase createPlatformAccount;
  @Autowired CreatePersonUseCase createPerson;
  @Autowired UpdatePersonByAdminUseCase updatePerson;
  @Autowired UpdatePersonUseCase updateSelf;
  @Autowired DeletePersonUseCase deletePerson;
  @Autowired UpdatePlatformAccountUseCase updatePlatform;
  @Autowired RegisterUserUseCase register;
  @Autowired InstitutionalEmailVerificationUseCase verification;
  @Autowired InstitutionRoleManagementService roles;
  @Autowired ReplacePersonRolesUseCase replaceRoles;
  @Autowired UpdateInstitutionalUserStatusUseCase institutionalAccess;
  @Autowired UpdatePlatformAccountStatusUseCase platformAccess;
  @Autowired TransactionTemplate transaction;
  @Autowired ApplicationEvents events;
  @Autowired AuditEventRecorder auditRecorder;
  @Autowired EntityManager entityManager;
  @MockitoBean MailSender mailSender;

  @Test
  void auditsPersonalChangesAndPasswordsWithoutPrivateSnapshotsOrNoopEvents() throws Exception {
    authenticatePlatformAdministrator();
    final UUID institution = fixtures.firstInstitutionId();
    final UUID person = createPerson.execute(institution, personRequest()).personId();
    final UUID user = userId(person);
    final var change = new UpdatePersonByAdminRequest("Nombre Privado", null, null, "3515551212");
    updatePerson.execute(institution, person, change);
    updatePerson.execute(institution, person, change);
    assertThat(countForEntity(person, "PERSON_UPDATED")).isEqualTo(1);
    final String details =
        requireNonNull(event(person, "PERSON_UPDATED").get("details")).toString();
    assertThat(details)
        .contains("firstName", "phoneNumber")
        .doesNotContain("Nombre Privado", "3515551212");
    final var passwordChange =
        new UpdatePersonByAdminRequest(null, null, null, null, "OtherPassword123!");
    updatePerson.execute(institution, person, passwordChange);
    assertThat(event(user, "PASSWORD_CHANGED").get("actor_platform_account_id"))
        .isEqualTo(adminId());
    assertThat(countForEntity(person, "PERSON_UPDATED")).isEqualTo(1);
    final var principal =
        new JwtAuthenticatedUser(user, person, "12345678", institution, UUID.randomUUID(), "test");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new TestingAuthenticationToken(principal, "", "ROLE_INSTITUTIONAL_USER"));
    updateSelf.execute(
        principal,
        new UpdatePersonRequest(null, "Apellido Privado", null, null, null, null, null, null));
    final var self =
        jdbcTemplate.queryForMap(
            "SELECT * FROM audit_events WHERE entity_id = ? AND origin = 'SELF_SERVICE' AND action = 'PERSON_UPDATED'",
            person);
    assertThat(self.get("actor_person_id")).isEqualTo(person);
    assertThat(self.get("subject_person_id")).isEqualTo(person);
    assertThat(requireNonNull(self.get("details")).toString())
        .contains("lastName")
        .doesNotContain("Apellido Privado");
    authenticatePlatformAdministrator();
    transaction.executeWithoutResult(
        state -> {
          updatePerson.execute(
              institution, person, new UpdatePersonByAdminRequest("Rollback", null, null, null));
          state.setRollbackOnly();
        });
    assertThat(countForEntity(person, "PERSON_UPDATED")).isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT first_name FROM people WHERE person_id = ?", String.class, person))
        .isEqualTo("Nombre Privado");
    final UUID account = createPlatformAccount.execute(accountRequest()).platformAccountId();
    updatePlatform.execute(
        account,
        new UpdatePlatformAccountRequest(
            "Perfil", "Privado", UUID.randomUUID() + "@example.test", PASSWORD));
    assertThat(countForEntity(account, "PLATFORM_ACCOUNT_UPDATED")).isEqualTo(1);
    assertThat(countForEntity(account, "PASSWORD_CHANGED")).isEqualTo(1);
    deletePerson.execute(institution, person);
    deletePerson.execute(institution, person);
    assertThat(countForEntity(person, "PERSON_DELETED")).isEqualTo(1);
    final var result = asAdmin(get(BASE).param("personId", person.toString()).param("size", "1"));
    assertThat(result.path("totalItems").asLong()).isGreaterThanOrEqualTo(5);
    assertThat(result.path("items").size()).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT string_agg(details::text, '') FROM audit_events WHERE subject_person_id = ?",
                String.class,
                person))
        .doesNotContain(
            PASSWORD, "OtherPassword123!", "Nombre Privado", "Apellido Privado", "3515551212");
  }

  @Test
  void recordsAccountLifecyclesWithRealAndAnonymousActors() {
    authenticatePlatformAdministrator();
    final UUID adminId = adminId();
    final UUID accountId = createPlatformAccount.execute(accountRequest()).platformAccountId();
    final UUID institutionId = fixtures.firstInstitutionId();
    final UUID personId = createPerson.execute(institutionId, personRequest()).personId();
    final UUID userId = userId(personId);
    assertThat(event(accountId, "PLATFORM_ACCOUNT_CREATED").get("actor_platform_account_id"))
        .isEqualTo(adminId);
    assertThat(event(userId, "INSTITUTIONAL_ACCOUNT_CREATED").get("subject_person_id"))
        .isEqualTo(personId);
    assertThat(event(userId, "INSTITUTIONAL_ACCOUNT_CREATED").get("actor_kind"))
        .isEqualTo("PLATFORM");
    assertThat(countForSubject(personId, "PERSON_ROLES_CHANGED")).isZero();

    SecurityContextHolder.clearContext();
    final var registered =
        register.execute(
            new RegisterRequest(
                "Maria",
                "Perez",
                LocalDate.of(2000, 1, 1),
                fixtures.randomDocumentNumber(),
                UUID.randomUUID() + "@example.test",
                PASSWORD,
                institutionId));
    final var created = event(registered.userId(), "INSTITUTIONAL_ACCOUNT_CREATED");
    assertThat(created.get("actor_kind")).isEqualTo("ANONYMOUS");
    assertThat(created.get("actor_person_id")).isNull();
    assertThat(created.get("origin")).isEqualTo("SELF_REGISTRATION");
    assertThat(requireNonNull(created.get("details")).toString())
        .contains("PENDING")
        .doesNotContain(PASSWORD);
    final String token =
        events.stream(InstitutionalEmailVerificationRequested.class)
            .filter(event -> event.userId().equals(registered.userId()))
            .findFirst()
            .orElseThrow()
            .token();
    verification.confirm(new ConfirmEmailVerificationRequest(token));
    final var verified = event(registered.userId(), "INSTITUTIONAL_EMAIL_VERIFIED");
    assertThat(verified.get("actor_kind")).isEqualTo("ANONYMOUS");
    assertThat(requireNonNull(verified.get("details")).toString()).doesNotContain(token, PASSWORD);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT email_verification_status FROM users WHERE user_id = ?",
                String.class,
                registered.userId()))
        .isEqualTo("VERIFIED");
  }

  @Test
  void auditsEffectiveRolesAndAccessWithoutNoopEvents() {
    authenticatePlatformAdministrator();
    final UUID institutionId = fixtures.firstInstitutionId();
    final UUID personId = createPerson.execute(institutionId, personRequest()).personId();
    final var role =
        roles.createAsPlatformAdmin(
            institutionId,
            new InstitutionRoleRequest(
                "Audit " + UUID.randomUUID(), Set.of(PermissionCode.TRAINING_PATH_READ.getCode())));
    final UUID roleId = role.id();
    final UUID pathId = UUID.randomUUID();
    fixtures.insertTrainingPath(pathId, institutionId);
    final var institutional =
        new ReplacePersonRolesRequest(
            List.of(new AssignRoleRequest(roleId, AccessScope.INSTITUTION, Set.of())));
    replaceRoles.execute(institutionId, personId, institutional, true, Set.of());
    replaceRoles.execute(institutionId, personId, institutional, true, Set.of());
    assertThat(countForSubject(personId, "PERSON_ROLES_CHANGED")).isEqualTo(1);
    replaceRoles.execute(
        institutionId,
        personId,
        new ReplacePersonRolesRequest(
            List.of(new AssignRoleRequest(roleId, AccessScope.TRAINING_PATHS, Set.of(pathId)))),
        true,
        Set.of());
    assertThat(countForSubject(personId, "PERSON_ROLES_CHANGED")).isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT details::text FROM audit_events WHERE subject_person_id = ? AND action = 'PERSON_ROLES_CHANGED'",
                String.class,
                personId))
        .anySatisfy(
            details ->
                assertThat(details)
                    .contains("before", "after", "TRAINING_PATHS", pathId.toString()));

    final var updated =
        new InstitutionRoleRequest(
            "Audit changed " + UUID.randomUUID(),
            Set.of(PermissionCode.TRAINING_PATH_UPDATE.getCode()));
    roles.updateAsPlatformAdmin(institutionId, roleId, updated);
    roles.updateAsPlatformAdmin(institutionId, roleId, updated);
    assertThat(countForEntity(roleId, "ROLE_UPDATED")).isEqualTo(1);
    final UUID applicant =
        jdbcTemplate.queryForObject(
            "SELECT role_id FROM roles WHERE institution_id = ? AND code = 'APPLICANT'",
            UUID.class,
            institutionId);
    replaceRoles.execute(
        institutionId,
        personId,
        new ReplacePersonRolesRequest(
            List.of(
                new AssignRoleRequest(
                    requireNonNull(applicant), AccessScope.INSTITUTION, Set.of()))),
        true,
        Set.of());
    roles.deleteAsPlatformAdmin(institutionId, roleId);
    assertThat(countForEntity(roleId, "ROLE_CREATED")).isEqualTo(1);
    assertThat(countForEntity(roleId, "ROLE_DELETED")).isEqualTo(1);

    institutionalAccess.execute(institutionId, UUID.randomUUID(), personId, false);
    institutionalAccess.execute(institutionId, UUID.randomUUID(), personId, false);
    assertThat(countForEntity(userId(personId), "INSTITUTIONAL_ACCESS_CHANGED")).isEqualTo(1);
    final UUID accountId = createPlatformAccount.execute(accountRequest()).platformAccountId();
    platformAccess.execute(accountId, adminId(), false);
    platformAccess.execute(accountId, adminId(), false);
    assertThat(countForEntity(accountId, "PLATFORM_ACCESS_CHANGED")).isEqualTo(1);
  }

  @Test
  void rollsBackBusinessAndAuditAndProtectsHistoricalRecords() {
    authenticatePlatformAdministrator();
    final UUID rolledBackId =
        requireNonNull(
            transaction.execute(
                status -> {
                  final UUID id =
                      createPlatformAccount.execute(accountRequest()).platformAccountId();
                  status.setRollbackOnly();
                  return id;
                }));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_accounts WHERE platform_account_id = ?",
                Long.class,
                rolledBackId))
        .isZero();
    assertThat(countForEntity(rolledBackId, "PLATFORM_ACCOUNT_CREATED")).isZero();

    final UUID institution = UUID.randomUUID();
    fixtures.insertTestInstitution(institution);
    final UUID actor = UUID.randomUUID();
    fixtures.insertPerson(actor, institution, fixtures.randomDocumentNumber(), false);
    final UUID eventId =
        insertEvent(
            institution,
            "INSTITUTION",
            actor,
            null,
            actor,
            "ENROLLMENT_APPLICATION_SUBMITTED",
            "ENROLLMENT_APPLICATION",
            "legacy-audit");
    jdbcTemplate.update("DELETE FROM people WHERE person_id = ?", actor);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT actor_person_id FROM audit_events WHERE audit_event_id = ?",
                UUID.class,
                eventId))
        .isEqualTo(actor);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE audit_events SET action = 'PERSON_CREATED' WHERE audit_event_id = ?",
                    eventId))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append-only");
    assertThatThrownBy(
            () -> jdbcTemplate.update("DELETE FROM audit_events WHERE audit_event_id = ?", eventId))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append-only");
    assertThatThrownBy(
            () ->
                insertEvent(
                    institution,
                    "PLATFORM",
                    actor,
                    adminId(),
                    actor,
                    "PERSON_CREATED",
                    "PERSON",
                    "invalid-audit"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("audit_events_actor_shape");
    final UUID legacyId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO audit_events (audit_event_id, institution_id, actor_person_id, subject_person_id,
          action, entity_type, entity_id, acted_on_behalf, occurred_at)
        VALUES (?, ?, ?, ?, 'ENROLLMENT_APPLICATION_SUBMITTED', 'ENROLLMENT_APPLICATION', ?, false, ?)
        """,
        legacyId,
        institution,
        actor,
        actor,
        UUID.randomUUID(),
        Timestamp.from(EVENT_TIME));
    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT actor_kind, origin, details::text FROM audit_events WHERE audit_event_id = ?",
                legacyId))
        .containsEntry("actor_kind", "INSTITUTION")
        .containsEntry("origin", null)
        .containsEntry("details", "{}");
  }

  @Test
  void filtersBeforePaginationAndProvidesStableGlobalAndActorResults() throws Exception {
    final UUID firstInstitution = UUID.randomUUID(), secondInstitution = UUID.randomUUID();
    fixtures.insertTestInstitution(firstInstitution);
    fixtures.insertTestInstitution(secondInstitution);
    final UUID actor = UUID.randomUUID();
    fixtures.insertPerson(actor, firstInstitution, fixtures.randomDocumentNumber(), false);
    final String requestId = "audit-" + UUID.randomUUID();
    final UUID first =
        insertEvent(
            firstInstitution,
            "INSTITUTION",
            actor,
            null,
            actor,
            "ENROLLMENT_APPLICATION_SUBMITTED",
            "ENROLLMENT_APPLICATION",
            requestId);
    final UUID second =
        insertEvent(
            secondInstitution,
            "INSTITUTION",
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            "ENROLLMENT_APPLICATION_SUBMITTED",
            "ENROLLMENT_APPLICATION",
            requestId);
    insertEvent(
        null,
        "PLATFORM",
        null,
        adminId(),
        null,
        "PLATFORM_ACCOUNT_CREATED",
        "PLATFORM_ACCOUNT",
        requestId);
    insertEvent(
        firstInstitution,
        "ANONYMOUS",
        null,
        null,
        UUID.randomUUID(),
        "INSTITUTIONAL_ACCOUNT_CREATED",
        "USER",
        requestId);
    final var all = asAdmin(get(BASE).param("requestId", requestId).param("size", "1"));
    assertThat(all.get("totalItems").asInt()).isEqualTo(4);
    final var expected =
        jdbcTemplate.queryForList(
            "SELECT audit_event_id FROM audit_events WHERE request_id = ? ORDER BY occurred_at DESC, audit_event_id DESC",
            UUID.class,
            requestId);
    assertThat(all.at("/items/0/id").asString()).isEqualTo(expected.get(0).toString());
    assertThat(
            asAdmin(get(BASE).param("requestId", requestId).param("size", "1").param("page", "1"))
                .at("/items/0/id")
                .asString())
        .isEqualTo(expected.get(1).toString());
    final var scoped =
        asAdmin(
            get(BASE)
                .param("requestId", requestId)
                .param("institutionId", firstInstitution.toString())
                .param("actorKind", "INSTITUTION")
                .param("actorId", actor.toString())
                .param("subjectPersonId", actor.toString())
                .param("entityType", "ENROLLMENT_APPLICATION")
                .param("action", "ENROLLMENT_APPLICATION_SUBMITTED")
                .param("from", EVENT_TIME.toString())
                .param("to", EVENT_TIME.plusSeconds(1).toString()));
    assertThat(scoped.get("totalItems").asInt()).isEqualTo(1);
    assertThat(scoped.at("/items/0/id").asString()).isEqualTo(first.toString());
    assertThat(
            asAdmin(get(BASE).param("requestId", requestId).param("scope", "PLATFORM"))
                .get("totalItems")
                .asInt())
        .isEqualTo(1);
    assertThat(
            asAdmin(get(BASE).param("requestId", requestId).param("scope", "INSTITUTION"))
                .get("totalItems")
                .asInt())
        .isEqualTo(3);
    assertThat(
            asAdmin(get(BASE).param("requestId", requestId).param("to", EVENT_TIME.toString()))
                .get("totalItems")
                .asInt())
        .isZero();
    assertThat(
            asAdmin(
                    get(BASE + "/actors")
                        .param("institutionId", firstInstitution.toString())
                        .param("actorKind", "INSTITUTION")
                        .param("search", "Ana")
                        .param("size", "1"))
                .at("/items/0/actorId")
                .asString())
        .isEqualTo(actor.toString());
    assertThat(asAdmin(get(BASE + "/" + second)).get("institutionId").asString())
        .isEqualTo(secondInstitution.toString());
  }

  @Test
  void searchesModulesAndAllRelatedPeopleBeforePagination() throws Exception {
    final UUID institution = UUID.randomUUID();
    fixtures.insertTestInstitution(institution);
    final UUID tutor = UUID.randomUUID(),
        dependent = UUID.randomUUID(),
        reviewer = UUID.randomUUID();
    fixtures.insertPerson(tutor, institution, fixtures.randomDocumentNumber(), false);
    fixtures.insertPerson(dependent, institution, fixtures.randomDocumentNumber(), false);
    fixtures.insertPerson(reviewer, institution, fixtures.randomDocumentNumber(), false);
    jdbcTemplate.update(
        "UPDATE people SET first_name = 'Matías', last_name = 'Suárez' WHERE person_id = ?", tutor);
    jdbcTemplate.update(
        "UPDATE people SET first_name = 'Helena', last_name = 'Romero' WHERE person_id = ?",
        dependent);
    jdbcTemplate.update(
        "UPDATE people SET first_name = 'Luis', last_name = 'Perez' WHERE person_id = ?", reviewer);
    final UUID link = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO person_guardians (person_guardian_id, institution_id, tutor_person_id,
          dependent_person_id, relationship, status, created_at, updated_at)
        VALUES (?, ?, ?, ?, 'LEGAL_GUARDIAN', 'ENDED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        link,
        institution,
        tutor,
        dependent);
    final String requestId = "audit-related-" + UUID.randomUUID();
    insertEvent(
        institution, "INSTITUTION", tutor, null, dependent, "PERSON_CREATED", "PERSON", requestId);
    final UUID approved =
        insertEvent(
            institution,
            "PLATFORM",
            null,
            adminId(),
            dependent,
            "GUARDIAN_LINK_APPROVED",
            "PERSON_GUARDIAN",
            requestId,
            link,
            "{}");
    insertEvent(
        institution,
        "INSTITUTION",
        reviewer,
        null,
        tutor,
        "PERSON_ROLES_CHANGED",
        "PERSON",
        requestId,
        tutor,
        """
        {"before": [], "after": [{"roleName": "Coordinación musical", "roleId": "%s"}]}
        """
            .formatted(UUID.randomUUID()));
    insertEvent(
        institution,
        "INSTITUTION",
        reviewer,
        null,
        dependent,
        "GUARDIAN_LINK_REQUESTED",
        "PERSON_GUARDIAN",
        requestId,
        link,
        "{}");
    insertEvent(
        institution,
        "PLATFORM",
        null,
        adminId(),
        dependent,
        "GUARDIAN_DEPENDENT_UNLINKED",
        "PERSON_GUARDIAN",
        requestId,
        link,
        "{}");

    final var first =
        asAdmin(
            get(BASE)
                .param("requestId", requestId)
                .param("personId", tutor.toString())
                .param("size", "1"));
    final var second =
        asAdmin(
            get(BASE)
                .param("requestId", requestId)
                .param("personId", tutor.toString())
                .param("size", "1")
                .param("page", "1"));
    assertThat(first.get("totalItems").asInt()).isEqualTo(5);
    assertThat(second.get("totalItems").asInt()).isEqualTo(5);
    assertThat(first.at("/items/0/id").asString())
        .isNotEqualTo(second.at("/items/0/id").asString());
    assertThat(
            asAdmin(
                    get(BASE)
                        .param("requestId", requestId)
                        .param("personId", tutor.toString())
                        .param("module", "GUARDIAN_LINKS"))
                .get("totalItems")
                .asInt())
        .isEqualTo(3);
    assertThat(
            asAdmin(get(BASE).param("requestId", requestId).param("module", "USERS"))
                .get("totalItems")
                .asInt())
        .isEqualTo(1);
    assertThat(
            asAdmin(get(BASE).param("requestId", requestId).param("search", "vinculacion aprobada"))
                .at("/items/0/id")
                .asString())
        .isEqualTo(approved.toString());
    final var mixed =
        asAdmin(get(BASE).param("requestId", requestId).param("search", "Matias aprobada"));
    assertThat(mixed.get("totalItems").asInt()).isEqualTo(1);
    assertThat(mixed.at("/items/0/id").asString()).isEqualTo(approved.toString());
    assertThat(
            asAdmin(get(BASE).param("requestId", requestId).param("search", "coordinacion musical"))
                .get("totalItems")
                .asInt())
        .isEqualTo(1);
    for (final String literal : List.of("%", "_", tutor.toString())) {
      assertThat(
              asAdmin(get(BASE).param("requestId", requestId).param("search", literal))
                  .get("totalItems")
                  .asInt())
          .isZero();
    }

    jdbcTemplate.update("UPDATE people SET deleted = true WHERE person_id = ?", tutor);
    final var people =
        asAdmin(
            get(BASE + "/people")
                .param("institutionId", institution.toString())
                .param("size", "1"));
    assertThat(people.get("totalItems").asInt()).isEqualTo(3);
    assertThat(
            asAdmin(
                    get(BASE + "/people")
                        .param("institutionId", institution.toString())
                        .param("size", "1")
                        .param("page", "2"))
                .at("/items/0/personId")
                .asString())
        .isEqualTo(tutor.toString());
    assertThat(
            asAdmin(get(BASE + "/people").param("personId", tutor.toString()))
                .at("/items/0/label")
                .asString())
        .isEqualTo("Matías Suárez");
    assertThat(
            asAdmin(
                    get(BASE + "/people")
                        .param("institutionId", institution.toString())
                        .param("search", "matias suarez"))
                .get("totalItems")
                .asInt())
        .isEqualTo(1);
    assertThat(
            asAdmin(
                    get(BASE + "/actors")
                        .param("institutionId", institution.toString())
                        .param("search", "Matias"))
                .at("/items/0/actorId")
                .asString())
        .isEqualTo(tutor.toString());
    assertThat(
            asAdmin(get(BASE).param("requestId", requestId).param("search", "Matias aprobada"))
                .get("totalItems")
                .asInt())
        .isEqualTo(1);
    final var actions =
        asAdmin(
            get(BASE + "/actions")
                .param("module", "GUARDIAN_LINKS")
                .param("search", "vinculacion aprobada"));
    assertThat(actions.get("totalItems").asInt()).isEqualTo(1);
    assertThat(actions.at("/items/0/action").asString()).isEqualTo("GUARDIAN_LINK_APPROVED");
    final var firstActionPage = asAdmin(get(BASE + "/actions").param("size", "7"));
    final var actionModules = new ArrayList<AuditModule>();
    for (int page = 0; page < firstActionPage.get("totalPages").asInt(); page++) {
      final var actionPage =
          page == 0
              ? firstActionPage
              : asAdmin(
                  get(BASE + "/actions").param("size", "7").param("page", String.valueOf(page)));
      actionPage
          .get("items")
          .forEach(item -> actionModules.add(AuditModule.valueOf(item.get("module").asString())));
    }
    assertThat(actionModules)
        .isSorted()
        .doesNotContain(AuditModule.EMAILS)
        .contains(
            java.util.Arrays.stream(AuditModule.values())
                .filter(module -> module != AuditModule.EMAILS)
                .toArray(AuditModule[]::new));
    assertThat(
            asAdmin(get(BASE + "/actions").param("action", "ROLE_CREATED"))
                .at("/items/0/module")
                .asString())
        .isEqualTo("ROLES");
    assertThat(
            asAdmin(get(BASE + "/actions").param("size", "50").param("page", "2147483647"))
                .get("items")
                .size())
        .isZero();

    final String targetRequest = requestId + "-target";
    insertEvent(
        null,
        "PLATFORM",
        null,
        UUID.randomUUID(),
        null,
        "PLATFORM_ACCESS_CHANGED",
        "PLATFORM_ACCOUNT",
        targetRequest,
        adminId(),
        "{}");
    final String targetName =
        requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT first_name || ' ' || last_name FROM platform_accounts WHERE platform_account_id = ?",
                String.class,
                adminId()));
    assertThat(
            asAdmin(get(BASE).param("requestId", targetRequest).param("search", targetName))
                .get("totalItems")
                .asInt())
        .isEqualTo(1);
  }

  @Test
  void protectsEveryAdminEndpointAndPreservesTheInstitutionalContract() throws Exception {
    final UUID institution = UUID.randomUUID(),
        actor = UUID.randomUUID(),
        subject = UUID.randomUUID();
    fixtures.insertTestInstitution(institution);
    fixtures.insertPerson(actor, institution, fixtures.randomDocumentNumber(), false);
    fixtures.insertPerson(subject, institution, fixtures.randomDocumentNumber(), false);
    jdbcTemplate.update(
        "UPDATE people SET first_name = 'Mateo', last_name = 'Delgado' WHERE person_id = ?",
        subject);
    final UUID entity = UUID.randomUUID();
    transaction.executeWithoutResult(
        ignored ->
            auditRecorder.record(
                entityManager.getReference(Institution.class, institution),
                actor,
                subject,
                AuditAction.ENROLLMENT_APPLICATION_SUBMITTED,
                AuditEntityType.ENROLLMENT_APPLICATION,
                entity));
    final UUID legacy =
        requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT audit_event_id FROM audit_events WHERE entity_id = ?", UUID.class, entity));
    insertEvent(
        institution,
        "PLATFORM",
        null,
        adminId(),
        actor,
        "GUARDIAN_LINK_APPROVED",
        "PERSON_GUARDIAN",
        "compat-audit");
    authenticatePlatformAdministrator();
    final var accountInstitution = fixtures.firstInstitutionId();
    final var person = createPerson.execute(accountInstitution, personRequest());
    final var user = userId(person.personId());
    final var session = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO user_sessions (user_session_id, user_id, started_at, active, remember_me) VALUES (?, ?, now(), true, false)",
        session,
        user);
    SecurityContextHolder.clearContext();
    final Authentication institutional =
        new TestingAuthenticationToken(
            JwtAuthenticatedUser.builder()
                .userId(user)
                .sessionId(session)
                .personId(person.personId())
                .institutionId(accountInstitution)
                .build(),
            "",
            "ROLE_USER");
    final Authentication withoutRole =
        new TestingAuthenticationToken(
            AuthTestData.platformPrincipal(UUID.randomUUID()), "", "ROLE_USER");
    for (final String path :
        List.of(BASE, BASE + "/actors", BASE + "/people", BASE + "/actions", BASE + "/" + legacy)) {
      mvc.perform(get(path).with(anonymous())).andExpect(status().isUnauthorized());
      mvc.perform(get(path).with(authentication(institutional))).andExpect(status().isForbidden());
      mvc.perform(get(path).with(authentication(withoutRole))).andExpect(status().isForbidden());
    }
    final var response = asAdmin(get("/api/v1/institutions/" + institution + "/audit-events"));
    assertThat(response.get("totalItems").asInt()).isEqualTo(1);
    assertThat(response.at("/items/0/actorPersonId").asString()).isEqualTo(actor.toString());
    assertThat(response.at("/items/0/subjectPersonId").asString()).isEqualTo(subject.toString());
    assertThat(response.at("/items/0/actorName").asString()).isEqualTo("Ana Garcia");
    assertThat(response.at("/items/0/subjectName").asString()).isEqualTo("Mateo Delgado");
    assertThat(response.at("/items/0/actedOnBehalf").asBoolean()).isTrue();
    jdbcTemplate.update("DELETE FROM people WHERE person_id = ?", subject);
    final var historical = asAdmin(get("/api/v1/institutions/" + institution + "/audit-events"));
    assertThat(historical.get("totalItems").asInt()).isEqualTo(1);
    assertThat(historical.at("/items/0/actorPersonId").asString()).isEqualTo(actor.toString());
    assertThat(historical.at("/items/0/actorName").asString()).isEqualTo("Ana Garcia");
    assertThat(historical.at("/items/0/subjectName").isNull()).isTrue();
    mvc.perform(get(BASE).param("actorId", actor.toString()).with(authentication(admin())))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get(BASE)
                .param("scope", "PLATFORM")
                .param("institutionId", institution.toString())
                .with(authentication(admin())))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get(BASE)
                .param("from", EVENT_TIME.toString())
                .param("to", EVENT_TIME.toString())
                .with(authentication(admin())))
        .andExpect(status().isBadRequest());
    mvc.perform(get(BASE + "/" + UUID.randomUUID()).with(authentication(admin())))
        .andExpect(status().isNotFound());
  }

  private CreatePlatformAccountRequest accountRequest() {
    return new CreatePlatformAccountRequest(
        "Audit", "Administrator", UUID.randomUUID() + "@example.test", PASSWORD, true);
  }

  private CreatePersonRequest personRequest() {
    return new CreatePersonRequest(
        "Maria",
        "Perez",
        fixtures.randomDocumentNumber(),
        UUID.randomUUID() + "@example.test",
        null,
        LocalDate.of(2000, 1, 1),
        PASSWORD,
        null);
  }

  private UUID adminId() {
    return requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT platform_account_id FROM platform_accounts WHERE email = 'admin@plataforma.com'",
            UUID.class));
  }

  private UUID userId(UUID personId) {
    return requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT user_id FROM users WHERE person_id = ?", UUID.class, personId));
  }

  private Map<String, @Nullable Object> event(UUID entityId, String action) {
    return jdbcTemplate.queryForMap(
        "SELECT * FROM audit_events WHERE entity_id = ? AND action = ?", entityId, action);
  }

  private long countForEntity(UUID entityId, String action) {
    return requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_events WHERE entity_id = ? AND action = ?",
            Long.class,
            entityId,
            action));
  }

  private long countForSubject(UUID personId, String action) {
    return requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_events WHERE subject_person_id = ? AND action = ?",
            Long.class,
            personId,
            action));
  }

  private Authentication admin() {
    return new TestingAuthenticationToken(
        AuthTestData.platformPrincipal(adminId()), "", "ROLE_PLATFORM_ADMIN");
  }

  private JsonNode asAdmin(MockHttpServletRequestBuilder request) throws Exception {
    return json.readTree(
        mvc.perform(request.with(authentication(admin())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private UUID insertEvent(
      @Nullable UUID institution,
      String kind,
      @Nullable UUID person,
      @Nullable UUID platform,
      @Nullable UUID subject,
      String action,
      String entityType,
      String requestId) {
    return insertEvent(
        institution,
        kind,
        person,
        platform,
        subject,
        action,
        entityType,
        requestId,
        UUID.randomUUID(),
        "{}");
  }

  private UUID insertEvent(
      @Nullable UUID institution,
      String kind,
      @Nullable UUID person,
      @Nullable UUID platform,
      @Nullable UUID subject,
      String action,
      String entityType,
      String requestId,
      UUID entityId,
      String details) {
    final UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO audit_events (audit_event_id, institution_id, actor_kind, actor_person_id,
          actor_platform_account_id, subject_person_id, action, entity_type, entity_id,
          acted_on_behalf, request_id, occurred_at, details)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, false, ?, ?, CAST(? AS jsonb))
        """,
        id,
        institution,
        kind,
        person,
        platform,
        subject,
        action,
        entityType,
        entityId,
        requestId,
        Timestamp.from(EVENT_TIME),
        details);
    return id;
  }
}
