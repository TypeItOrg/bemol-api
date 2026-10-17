package ar.edu.utn.frvm.typeit.boero_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.boero_api.audit.interfaces.AuditEventRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.auth.events.InstitutionalEmailVerificationRequested;
import ar.edu.utn.frvm.typeit.boero_api.auth.events.InstitutionalPasswordRecoveryRequested;
import ar.edu.utn.frvm.typeit.boero_api.auth.events.PersonAccessInvited;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.RegisterRequest;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.RegisterUserUseCase;
import ar.edu.utn.frvm.typeit.boero_api.common.logging.RequestLoggingFilter;
import ar.edu.utn.frvm.typeit.boero_api.common.mail.MailAuditContext;
import ar.edu.utn.frvm.typeit.boero_api.common.mail.MailMessage;
import ar.edu.utn.frvm.typeit.boero_api.common.mail.MailSender;
import ar.edu.utn.frvm.typeit.boero_api.common.mail.MailSendingException;
import ar.edu.utn.frvm.typeit.boero_api.common.mail.MailType;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.support.AuthTestData;
import ar.edu.utn.frvm.typeit.boero_api.support.IntegrationTest;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@IntegrationTest
@SpringBootTest(
    properties = {
      "management.health.mail.enabled=false",
      "app.mail.from=no-reply@example.test",
      "app.auth.password-recovery.frontend-url=http://localhost:3000",
      "app.auth.password-recovery.token-expiration=30m"
    })
@AutoConfigureMockMvc
class MailAuditPostgresIntegrationTest extends DatabaseMigrationTestSupport {
  private static final String BASE = "/api/v1/admin/audit-events";
  private static final String TOKEN = "private-token-must-not-be-audited";
  private static final String PASSWORD = "DummyPassword123!";

  @Autowired ApplicationEventPublisher publisher;
  @Autowired TransactionTemplate transaction;
  @Autowired MailSender sender;
  @Autowired RegisterUserUseCase register;
  @Autowired AuditEventRepository events;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockitoBean JavaMailSender smtp;

  @BeforeEach
  void prepareSmtp() {
    when(smtp.createMimeMessage())
        .thenAnswer(ignored -> new MimeMessage(Session.getInstance(new Properties())));
  }

  @AfterEach
  void clearRequestContext() {
    MDC.clear();
  }

  @Test
  void auditsAllCommittedMailFlowsWithCapturedContextAndSearchableSafeMetadata() throws Exception {
    authenticatePlatformAdministrator();
    final var principal = requireNonNull(SecurityContextHolder.getContext().getAuthentication());
    doAnswer(
            ignored -> {
              assertThat(Thread.currentThread().getName()).startsWith("mail-");
              assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
              assertThat(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID_KEY)).isNull();
              return null;
            })
        .when(smtp)
        .send(any(MimeMessage.class));
    final UUID institutionId = fixtures.firstInstitutionId();
    final UUID personId = UUID.randomUUID();
    fixtures.insertPerson(personId, institutionId, fixtures.randomDocumentNumber(), false);
    final String requestId = "mail-" + UUID.randomUUID();
    final String recipient = UUID.randomUUID() + "@example.test";
    MDC.put(RequestLoggingFilter.MDC_REQUEST_ID_KEY, requestId);
    final var institution = Institution.builder().id(institutionId).name("Institución").build();
    final var person =
        Person.builder()
            .id(personId)
            .institution(institution)
            .email(recipient)
            .firstName("Ana")
            .lastName("García")
            .build();
    final var user =
        User.builder().id(UUID.randomUUID()).institution(institution).person(person).build();
    transaction.executeWithoutResult(
        ignored -> {
          publisher.publishEvent(InstitutionalEmailVerificationRequested.from(user, TOKEN));
          publisher.publishEvent(InstitutionalPasswordRecoveryRequested.from(user, TOKEN));
          publisher.publishEvent(
              new PersonAccessInvited(
                  personId,
                  recipient,
                  "Institución",
                  "Ana García",
                  TOKEN,
                  null,
                  MailAuditContext.capture(institutionId, personId)));
        });
    SecurityContextHolder.clearContext();
    MDC.clear();
    awaitMailEvents(requestId, 3);

    verify(smtp, times(3)).send(any(MimeMessage.class));
    assertThat(mailEvents(requestId))
        .allSatisfy(
            event ->
                assertThat(event)
                    .containsEntry("actor_kind", "PLATFORM")
                    .containsEntry("institution_id", institutionId)
                    .containsEntry("subject_person_id", personId)
                    .containsEntry("action", "EMAIL_SENT"));
    assertThat(mailEvents(requestId).toString())
        .doesNotContain(TOKEN, "htmlBody", "token=", PASSWORD);
    final var list =
        admin(
            get(BASE)
                .param("module", "EMAILS")
                .param("requestId", requestId)
                .param("personId", personId.toString())
                .param("institutionId", institutionId.toString())
                .param("actorKind", "PLATFORM")
                .param(
                    "actorId",
                    ((JwtAuthenticatedPlatformAccount) requireNonNull(principal.getPrincipal()))
                        .platformAccountId()
                        .toString())
                .param("search", recipient)
                .param("size", "1"));
    assertThat(list.get("totalItems").asInt()).isEqualTo(3);
    assertThat(list.at("/items/0/details/recipient").asString()).isEqualTo(recipient);
    assertThat(
            admin(
                    get(BASE)
                        .param("requestId", requestId)
                        .param("search", "recuperacion contraseña")
                        .param("module", "EMAILS"))
                .get("totalItems")
                .asInt())
        .isEqualTo(1);
    assertThat(admin(get(BASE).param("requestId", requestId)).get("totalItems").asInt()).isZero();
    for (final MailType type : MailType.values()) {
      final var filtered =
          admin(
              get(BASE)
                  .param("module", "EMAILS")
                  .param("mailType", type.name())
                  .param("requestId", requestId)
                  .param("action", "EMAIL_SENT")
                  .param("size", "1"));
      assertThat(filtered.get("totalItems").asInt()).isEqualTo(1);
      assertThat(filtered.at("/items/0/details/mailType").asString()).isEqualTo(type.getLabel());
    }
    assertThat(admin(get(BASE + "/actions").param("module", "EMAILS")).get("totalItems").asInt())
        .isEqualTo(2);
    final var detail = admin(get(BASE + "/" + list.at("/items/0/id").asString()));
    assertThat(detail.get("requestId").asString()).isEqualTo(requestId);
    assertThat(detail.get("actorId").isNull()).isFalse();
    assertThat(detail.get("details").size()).isEqualTo(2);
    assertThat(detail.get("origin").asString()).isEqualTo("MAIL_DELIVERY");
    assertThat(detail.get("occurredAt").asString()).isNotBlank();
    assertThat(detail.toString()).doesNotContain(TOKEN, "htmlBody", "token=");
  }

  @Test
  void recordsSmtpFailureAfterRegistrationCommitButNeverSendsForRolledBackEvents()
      throws Exception {
    final String requestId = "failed-" + UUID.randomUUID();
    MDC.put(RequestLoggingFilter.MDC_REQUEST_ID_KEY, requestId);
    doThrow(new MailSendException("Sensitive SMTP diagnostic " + TOKEN))
        .when(smtp)
        .send(any(MimeMessage.class));
    final UUID institutionId = fixtures.firstInstitutionId();
    final var account =
        register.execute(
            new RegisterRequest(
                "Ana",
                "García",
                LocalDate.of(2000, 1, 1),
                fixtures.randomDocumentNumber(),
                UUID.randomUUID() + "@example.test",
                PASSWORD,
                institutionId));
    awaitMailEvents(requestId, 1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE user_id = ?", Integer.class, account.userId()))
        .isEqualTo(1);
    assertThat(mailEvents(requestId))
        .singleElement()
        .satisfies(
            event ->
                assertThat(event)
                    .containsEntry("action", "EMAIL_FAILED")
                    .containsEntry("actor_kind", "ANONYMOUS"));
    final UUID personId =
        requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT person_id FROM users WHERE user_id = ?", UUID.class, account.userId()));
    transaction.executeWithoutResult(
        ignored ->
            publisher.publishEvent(
                new PersonAccessInvited(
                    personId,
                    "invitation@example.test",
                    "Institución",
                    "Ana",
                    TOKEN,
                    null,
                    MailAuditContext.capture(institutionId, personId))));
    awaitMailEvents(requestId, 2);
    assertThat(mailEvents(requestId).toString()).doesNotContain(TOKEN, "Sensitive SMTP", PASSWORD);
    assertThat(
            admin(
                    get(BASE)
                        .param("module", "EMAILS")
                        .param("requestId", requestId)
                        .param("action", "EMAIL_FAILED"))
                .get("totalItems")
                .asInt())
        .isEqualTo(2);

    final String rolledBackRequest = "rollback-" + UUID.randomUUID();
    MDC.put(RequestLoggingFilter.MDC_REQUEST_ID_KEY, rolledBackRequest);
    transaction.executeWithoutResult(
        rollback -> {
          publisher.publishEvent(
              new PersonAccessInvited(
                  UUID.randomUUID(),
                  "rollback@example.test",
                  "Institución",
                  "Ana",
                  TOKEN,
                  null,
                  MailAuditContext.capture(institutionId, UUID.randomUUID())));
          rollback.setRollbackOnly();
        });
    verify(smtp, after(300).times(2)).send(any(MimeMessage.class));
    assertThat(mailEvents(rolledBackRequest)).isEmpty();
  }

  @Test
  void outcomePersistsIndependentlyAndAuditFailureNeverChangesSmtpOutcomeOrRetries() {
    final String requestId = "independent-" + UUID.randomUUID();
    MDC.put(RequestLoggingFilter.MDC_REQUEST_ID_KEY, requestId);
    final UUID institutionId = fixtures.firstInstitutionId();
    final var actor = AuthTestData.institutionalPrincipal(UUID.randomUUID(), institutionId);
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(actor, ""));
    final var message = message(MailAuditContext.capture(institutionId, UUID.randomUUID()));
    SecurityContextHolder.clearContext();
    transaction.executeWithoutResult(
        rollback -> {
          sender.send(message);
          rollback.setRollbackOnly();
        });
    assertThat(mailEvents(requestId))
        .singleElement()
        .satisfies(
            event ->
                assertThat(event)
                    .containsEntry("action", "EMAIL_SENT")
                    .containsEntry("actor_kind", "INSTITUTION")
                    .containsEntry("actor_person_id", actor.personId()));

    assertThat(
            events.search(
                institutionId,
                message.auditContext().personId(),
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20)))
        .isEmpty();

    // The real FK fails when the independent audit transaction commits, after SMTP succeeded.
    final var unauditable = message(MailAuditContext.capture(UUID.randomUUID(), UUID.randomUUID()));
    assertThatCode(() -> sender.send(unauditable)).doesNotThrowAnyException();
    doThrow(new MailSendException("Sensitive SMTP diagnostic " + TOKEN))
        .when(smtp)
        .send(any(MimeMessage.class));
    assertThatThrownBy(() -> sender.send(unauditable)).isInstanceOf(MailSendingException.class);
    verify(smtp, times(3)).send(any(MimeMessage.class));
    assertThat(mailEvents(requestId)).hasSize(1);
  }

  private static MailMessage message(final MailAuditContext context) {
    return new MailMessage(
        "no-reply@example.test",
        "ana@example.test",
        "Asunto",
        "<p>" + TOKEN + "</p>",
        MailType.EMAIL_VERIFICATION,
        context);
  }

  private List<Map<String, @Nullable Object>> mailEvents(final String requestId) {
    return jdbcTemplate.queryForList(
        "SELECT actor_kind, actor_person_id, institution_id, subject_person_id, action, details::text FROM audit_events WHERE request_id = ? AND entity_type = 'EMAIL'",
        requestId);
  }

  private void awaitMailEvents(final String requestId, final int expected)
      throws InterruptedException {
    final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (mailEvents(requestId).size() < expected && System.nanoTime() < deadline) {
      TimeUnit.MILLISECONDS.sleep(25);
    }
    assertThat(mailEvents(requestId)).hasSize(expected);
  }

  private JsonNode admin(final MockHttpServletRequestBuilder request) throws Exception {
    authenticatePlatformAdministrator();
    final var actor = requireNonNull(SecurityContextHolder.getContext().getAuthentication());
    final var response =
        mvc.perform(request.with(authentication(actor)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(response);
  }
}
