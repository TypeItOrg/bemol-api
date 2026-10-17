package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicSpace;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.CourseClass;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.DocumentDefinition;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.TrainingPathDocumentRequirement;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.ApprovalMode;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.RequirementType;
import ar.edu.utn.frvm.typeit.boero_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.PersonAccessInvitation;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.auth.enums.EmailVerificationStatus;
import ar.edu.utn.frvm.typeit.boero_api.auth.events.PersonAccessInvited;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.Permission;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.RolePermission;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AssignPersonSystemRoleUseCase;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionRoleProvisioner;
import ar.edu.utn.frvm.typeit.boero_api.common.mail.MailSender;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentPeriod;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentPeriodOffering;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentSource;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.DocumentRequirementLevel;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentPeriodStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Student;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianDependentResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.ListGuardianDependentsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.support.EnrollmentDocumentTestData;
import ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData;
import ar.edu.utn.frvm.typeit.boero_api.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.hibernate.SessionFactory;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@IntegrationTest
@RecordApplicationEvents
@SuppressWarnings({"null", "resource"})
class EnrollmentApplicationGuardianshipPostgresIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"));

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @DynamicPropertySource
  static void infrastructureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("spring.flyway.enabled", () -> true);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add("spring.flyway.sql-migration-prefix", () -> "");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired EntityManager entityManager;
  @Autowired InstitutionRoleProvisioner institutionRoleProvisioner;
  @Autowired AssignPersonSystemRoleUseCase assignRoles;
  @Autowired TransactionTemplate transactions;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired ApplicationEvents events;
  @Autowired ListGuardianDependentsUseCase listDependents;
  @MockitoBean MailSender mailSender;
  private static final String PASSWORD = "DummyPassword123!";

  private <T> T tx(Supplier<T> action) {
    return transactions.execute(status -> action.get());
  }

  private Scenario setup() {
    return tx(
        () -> {
          final var s = scenario();
          s.tutor().updateContact(s.tutor().getEmail(), "3515551234");
          assignRoles.execute(s.tutor(), SystemRoleCode.INSTITUTIONAL_AUTHORITY);
          entityManager.persist(
              User.builder()
                  .person(s.tutor())
                  .institution(s.institution())
                  .password(requireNonNull(encoder.encode(PASSWORD)))
                  .emailVerificationStatus(EmailVerificationStatus.VERIFIED)
                  .emailVerifiedAt(Instant.now())
                  .build());
          return s;
        });
  }

  private Authentication actor(Scenario s) {
    return new UsernamePasswordAuthenticationToken(
        JwtAuthenticatedUser.builder()
            .userId(UUID.randomUUID())
            .personId(s.tutor().getId())
            .documentNumber(s.tutor().getDocumentNumber())
            .institutionId(s.institution().getId())
            .build(),
        null,
        List.of());
  }

  private String base(Scenario s) {
    return "/api/v1/institutions/" + s.institution().getId();
  }

  private String profile(Scenario s, String person) {
    return base(s) + "/self-service/" + person;
  }

  private JsonNode request(
      Scenario s,
      org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<?> request,
      int status)
      throws Exception {
    final var response =
        mvc.perform(request.with(authentication(actor(s))))
            .andExpect(status().is(status))
            .andReturn()
            .getResponse();
    return response.getContentAsString().isEmpty()
        ? json.createObjectNode()
        : json.readTree(response.getContentAsString());
  }

  private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object value) {
    return request.contentType("application/json").content(json.writeValueAsString(value));
  }

  private JsonNode represent(Scenario s) throws Exception {
    return represent(s, false);
  }

  private JsonNode represent(Scenario s, boolean ownAccessRequested) throws Exception {
    return represent(s, ownAccessRequested, null);
  }

  private JsonNode represent(
      Scenario s, boolean ownAccessRequested, @Nullable String roleBeforeApproval)
      throws Exception {
    final var link =
        request(
            s,
            body(
                post(base(s) + "/representations"),
                Map.of(
                    "documentNumber",
                    docNumber(),
                    "firstName",
                    "Maria",
                    "lastName",
                    "Delgado",
                    "birthDate",
                    "1956-01-01",
                    "relationship",
                    "OTHER",
                    "isPrimaryContact",
                    true,
                    "ownAccessRequested",
                    ownAccessRequested,
                    "email",
                    "dummy-person@example.test")),
            201);
    assertThat(
            events.stream(PersonAccessInvited.class)
                .noneMatch(
                    event ->
                        event
                            .personId()
                            .toString()
                            .equals(link.get("dependentPersonId").asString())))
        .isTrue();
    if (roleBeforeApproval != null) {
      assignPrivilegedRole(
          s, UUID.fromString(link.get("dependentPersonId").asString()), roleBeforeApproval);
    }
    request(
        s,
        body(
            post(
                base(s)
                    + "/guardian-links/"
                    + link.get("personGuardianId").asString()
                    + "/approve"),
            Map.of()),
        200);
    return link;
  }

  private JsonNode start(Scenario s, String person) throws Exception {
    return request(
        s,
        body(
            post(profile(s, person) + "/enrollment-applications"),
            Map.of("trainingPathId", s.plan().getTrainingPath().getId())),
        201);
  }

  @Test
  void ownAccessStatusQueriesStayBounded() {
    final var s = setup();
    final Map<UUID, String> expected = new HashMap<>();
    tx(
        () -> {
          final var link = ownAccessStatusLink(s, GuardianLinkStatus.ACTIVE);
          entityManager.persist(ownAccessInvitation(link, Instant.now().plusSeconds(3600)));
          expected.put(link.getDependentPerson().getId(), "INVITATION_PENDING");
          return null;
        });
    final var statistics =
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    final boolean statisticsEnabled = statistics.isStatisticsEnabled();
    statistics.setStatisticsEnabled(true);
    try {
      statistics.clear();
      assertThat(tx(() -> listDependents.execute(s.institution().getId(), s.tutor().getId())))
          .hasSize(1);
      final long singlePersonQueries = statistics.getPrepareStatementCount();
      statistics.clear();
      assertThat(
              tx(() ->
                      listDependents.page(
                          s.institution().getId(),
                          s.tutor().getId(),
                          0,
                          50,
                          "",
                          null,
                          null,
                          null,
                          "createdAt",
                          "asc"))
                  .items())
          .hasSize(1);
      final long singlePersonPageQueries = statistics.getPrepareStatementCount();

      tx(
          () -> {
            for (int i = 0; i < 8; i++) {
              final var link = ownAccessStatusLink(s, GuardianLinkStatus.ACTIVE);
              entityManager.persist(ownAccessInvitation(link, Instant.now().plusSeconds(3600)));
              expected.put(link.getDependentPerson().getId(), "INVITATION_PENDING");
            }
            final var expired = ownAccessStatusLink(s, GuardianLinkStatus.ACTIVE);
            entityManager.persist(ownAccessInvitation(expired, Instant.now().minusSeconds(60)));
            expected.put(expired.getDependentPerson().getId(), "NO_ACCOUNT");
            final var consumed = ownAccessStatusLink(s, GuardianLinkStatus.ACTIVE);
            final var consumedInvitation =
                ownAccessInvitation(consumed, Instant.now().plusSeconds(3600));
            consumedInvitation.consume(Instant.now());
            entityManager.persist(consumedInvitation);
            expected.put(consumed.getDependentPerson().getId(), "NO_ACCOUNT");
            final var account = ownAccessStatusLink(s, GuardianLinkStatus.ACTIVE);
            entityManager.persist(
                InstitutionalTestData.user(account.getInstitution(), account.getDependentPerson()));
            expected.put(account.getDependentPerson().getId(), "ENABLED");
            final var pending = ownAccessStatusLink(s, GuardianLinkStatus.PENDING);
            expected.put(pending.getDependentPerson().getId(), "UNAVAILABLE");
            final var rejected = ownAccessStatusLink(s, GuardianLinkStatus.REJECTED);
            expected.put(rejected.getDependentPerson().getId(), "UNAVAILABLE");
            return null;
          });

      statistics.clear();
      final var listed =
          tx(() -> listDependents.execute(s.institution().getId(), s.tutor().getId()));
      assertThat(statistics.getPrepareStatementCount()).isEqualTo(singlePersonQueries);
      assertThat(
              listed.stream()
                  .collect(
                      Collectors.toMap(
                          GuardianDependentResponse::dependentPersonId,
                          GuardianDependentResponse::ownAccessStatus)))
          .containsExactlyInAnyOrderEntriesOf(expected);

      statistics.clear();
      final var paged =
          tx(
              () ->
                  listDependents.page(
                      s.institution().getId(),
                      s.tutor().getId(),
                      0,
                      50,
                      "",
                      null,
                      null,
                      null,
                      "createdAt",
                      "asc"));
      assertThat(statistics.getPrepareStatementCount()).isEqualTo(singlePersonPageQueries);
      assertThat(
              paged.items().stream()
                  .collect(
                      Collectors.toMap(
                          GuardianDependentResponse::dependentPersonId,
                          GuardianDependentResponse::ownAccessStatus)))
          .containsExactlyInAnyOrderEntriesOf(expected);
    } finally {
      statistics.setStatisticsEnabled(statisticsEnabled);
    }
  }

  private PersonGuardian ownAccessStatusLink(Scenario s, GuardianLinkStatus status) {
    final var institution = entityManager.getReference(Institution.class, s.institution().getId());
    final var link =
        PersonGuardian.request(
            institution,
            entityManager.getReference(Person.class, s.tutor().getId()),
            person(institution),
            GuardianRelationship.OTHER,
            false);
    if (status == GuardianLinkStatus.ACTIVE) {
      link.approve(null);
    } else if (status == GuardianLinkStatus.REJECTED) {
      link.reject(null);
    }
    entityManager.persist(link);
    assignRoles.execute(link.getDependentPerson(), SystemRoleCode.APPLICANT);
    return link;
  }

  private PersonAccessInvitation ownAccessInvitation(PersonGuardian link, Instant expiresAt) {
    // A different representative exposes lazy queries that the listed link could otherwise hide.
    final var representation =
        PersonGuardian.request(
            link.getInstitution(),
            person(link.getInstitution()),
            link.getDependentPerson(),
            GuardianRelationship.OTHER,
            false);
    representation.approve(null);
    entityManager.persist(representation);
    return PersonAccessInvitation.issue(
        representation,
        link.getDependentPerson().getEmail(),
        UUID.randomUUID().toString().replace("-", "").repeat(2),
        expiresAt);
  }

  @Test
  void representsAndSubmitsWithRealActor() throws Exception {
    final var s = setup();
    final var link = represent(s);
    final String person = link.get("dependentPersonId").asString();
    final var context = request(s, get(profile(s, person)), 200);
    assertThat(context.get("actorPersonId").asString()).isEqualTo(s.tutor().getId().toString());
    assertThat(context.get("self").asBoolean()).isFalse();
    assertThat(context.get("canEnroll").asBoolean()).isTrue();
    assertThat(context.get("roles")).isEqualTo(json.valueToTree(List.of("Postulante")));
    assertThat(
            request(s, get(profile(s, s.tutor().getId().toString())), 200)
                .get("canEnroll")
                .asBoolean())
        .isFalse();
    final var app = start(s, person);
    assertThat(app.get("applicantFirstName").asString()).isEqualTo("Maria");
    assertThat(app.get("applicantLastName").asString()).isEqualTo("Delgado");
    assertThat(app.get("applicantDocumentNumber")).isEqualTo(context.get("documentNumber"));
    final var personal = app.get("data").get("personalData");
    assertThat(personal.get("firstName").asString()).isEqualTo("Maria");
    assertThat(personal.get("birthDate").asString()).isEqualTo("1956-01-01");
    assertThat(personal.get("email").isNull()).isTrue();
    assertThat(personal.get("documentNumber").asString())
        .isNotEqualTo(s.tutor().getDocumentNumber());
    assertThat(app.get("data").get("responsible").get("documentNumber").asString())
        .isEqualTo(s.tutor().getDocumentNumber());
    final String appId = app.get("applicationId").asString();
    final var search =
        request(s, get(profile(s, person) + "/search").param("search", "Instrumento"), 200);
    assertThat(search.get("groups").size()).isEqualTo(1);
    assertThat(search.at("/groups/0/entityType").asString()).isEqualTo("enrollment-application");
    assertThat(search.at("/groups/0/items/0/id").asString()).isEqualTo(appId);
    assertThat(
            request(
                    s,
                    get(profile(s, person) + "/search")
                        .param("search", s.tutor().getDocumentNumber()),
                    200)
                .get("groups")
                .isEmpty())
        .isTrue();
    request(s, get(profile(s, person) + "/search").param("search", "ab").param("limit", "6"), 400);
    final String path = profile(s, person) + "/enrollment-applications/" + appId;
    final var options = request(s, get(path + "/courses"), 200);
    final String course = options.get("items").get(0).get("courseId").asString();
    request(
        s,
        body(
            patch(path + "/draft"),
            Map.of(
                "data",
                Map.of(
                    "academicBackground",
                    Map.of("currentlyStudying", false, "educationLevel", "NO_SCHOOLING"),
                    "preference",
                    Map.of("preferredShift", "Mañana"),
                    "courses",
                    List.of(Map.of("courseId", course))))),
        200);
    final String requirement = app.get("documents").get(0).get("id").asString();
    final var file = EnrollmentDocumentTestData.pdf("dummy.pdf");
    request(
        s, multipart(path + "/attachments").file(file).param("requirementId", requirement), 201);
    final var submitted = request(s, post(path + "/submit"), 200);
    assertThat(submitted.get("submittedByPersonId").asString())
        .isEqualTo(s.tutor().getId().toString());
    assertThat(submitted.get("personId").asString()).isEqualTo(person);
    assertThat(submitted.get("status").asString()).isEqualTo("SUBMITTED");
    assertThat(submitted.get("data").get("personalData")).isEqualTo(personal);
    assertThat(request(s, get(path), 200).get("submittedByPersonId").asString())
        .isEqualTo(s.tutor().getId().toString());
    final var audit =
        tx(
            () ->
                entityManager
                    .createQuery(
                        "select a from AuditEvent a where a.entityId = :application and a.action = :action",
                        AuditEvent.class)
                    .setParameter("application", UUID.fromString(appId))
                    .setParameter("action", AuditAction.ENROLLMENT_APPLICATION_SUBMITTED)
                    .getSingleResult());
    assertThat(audit.getActorPersonId()).isEqualTo(s.tutor().getId());
    assertThat(audit.getSubjectPersonId()).isEqualTo(UUID.fromString(person));
    assertThat(audit.isActedOnBehalf()).isTrue();
    final var selectedCourse = enrollForSearch(s, person);
    final var actorCourse = enrollForSearch(s, s.tutor().getId().toString());
    final var courseSearch =
        request(s, get(profile(s, person) + "/search").param("search", "Espacio"), 200);
    assertThat(courseSearch.at("/groups/0/entityType").asString()).isEqualTo("course-enrollment");
    assertThat(courseSearch.at("/groups/0/items").size()).isEqualTo(1);
    assertThat(courseSearch.at("/groups/0/items/0/id").asString())
        .isEqualTo(selectedCourse.toString())
        .isNotEqualTo(actorCourse.toString());
    request(s, get(profile(s, person) + "/courses"), 200);
    request(s, get(profile(s, person) + "/schedules"), 200);

    final var staffLink = represent(s, false, "TEACHER");
    final String staffPerson = staffLink.get("dependentPersonId").asString();
    final var staffContext = request(s, get(profile(s, staffPerson)), 200);
    assertThat(staffContext.get("canEnroll").asBoolean()).isFalse();
    assertThat(staffContext.get("roles")).isEqualTo(json.valueToTree(List.of("Profesor")));
    request(
        s,
        body(
            post(profile(s, staffPerson) + "/enrollment-applications"),
            Map.of("trainingPathId", s.plan().getTrainingPath().getId())),
        403);
  }

  @Test
  void isolatesProfilesInstitutionsAndRevocation() throws Exception {
    final var s = setup();
    final var a = represent(s);
    final var b = represent(s);
    final String personA = a.get("dependentPersonId").asString();
    final String personB = b.get("dependentPersonId").asString();
    final String appId = start(s, personB).get("applicationId").asString();
    assertThat(
            request(s, get(profile(s, personA) + "/search").param("search", "Instrumento"), 200)
                .get("groups")
                .isEmpty())
        .isTrue();
    assertThat(
            request(s, get(profile(s, personB) + "/search").param("search", "Instrumento"), 200)
                .at("/groups/0/items/0/id")
                .asString())
        .isEqualTo(appId);
    request(
        s,
        get(profile(s, UUID.randomUUID().toString()) + "/search").param("search", "Instrumento"),
        403);
    request(
        s,
        get("/api/v1/institutions/" + UUID.randomUUID() + "/self-service/" + personA + "/search")
            .param("search", "Instrumento"),
        403);
    request(s, get(profile(s, personA) + "/enrollment-applications/" + appId), 404);
    request(
        s,
        get(
            profile(s, personA)
                + "/enrollment-applications/"
                + appId
                + "/attachments/requirements"),
        403);
    request(s, get(profile(s, "invalid-person") + "/enrollment-applications"), 400);
    request(
        s,
        get(
            "/api/v1/institutions/invalid-institution/self-service/"
                + personA
                + "/enrollment-applications/"
                + appId
                + "/attachments/requirements"),
        400);
    request(s, get(profile(s, UUID.randomUUID().toString())), 403);
    request(s, get("/api/v1/institutions/" + UUID.randomUUID() + "/self-service/" + personA), 403);
    request(
        s,
        body(
            post(
                profile(s, personA)
                    + "/enrollment-applications/"
                    + appId
                    + "/attachments/"
                    + UUID.randomUUID()
                    + "/review"),
            Map.of("status", "ACCEPTED")),
        404);
    request(s, delete(base(s) + "/representations/" + personA), 204);
    request(s, get(profile(s, personA)), 403);
    request(s, get(profile(s, personA) + "/search").param("search", "Instrumento"), 403);
    request(s, get(profile(s, personB)), 200);
  }

  @Test
  void activatesDisablesAndReenablesOwnAccessWithoutLiftingInstitutionBlock() throws Exception {
    final var s = setup();
    final var link = represent(s, true);
    final String person = link.get("dependentPersonId").asString();
    final String access =
        base(s) + "/representations/" + link.get("personGuardianId").asString() + "/own-access";
    assertThat(
            tx(
                () ->
                    users
                        .findByPerson_IdAndInstitution_Id(
                            UUID.fromString(person), s.institution().getId())
                        .isEmpty()))
        .isTrue();
    final var invitation =
        events.stream(PersonAccessInvited.class)
            .filter(event -> event.personId().toString().equals(person))
            .findFirst()
            .orElseThrow();
    request(s, body(patch(access), Map.of("enabled", false)), 200);
    mvc.perform(
            body(
                post("/api/v1/auth/own-access/activate"),
                Map.of(
                    "token",
                    invitation.token(),
                    "password",
                    PASSWORD,
                    "confirmPassword",
                    PASSWORD)))
        .andExpect(status().isBadRequest());
    request(
        s, body(patch(access), Map.of("enabled", true, "email", "dummy-person@example.test")), 200);
    final var replacement =
        events.stream(PersonAccessInvited.class)
            .filter(
                event ->
                    event.personId().toString().equals(person)
                        && !event.token().equals(invitation.token()))
            .findFirst()
            .orElseThrow();
    tx(
        () -> {
          entityManager
              .find(Person.class, UUID.fromString(person))
              .updateContact("institution-corrected@example.test", null);
          return null;
        });
    mvc.perform(
            body(
                post("/api/v1/auth/own-access/activate"),
                Map.of(
                    "token",
                    replacement.token(),
                    "password",
                    PASSWORD,
                    "confirmPassword",
                    PASSWORD)))
        .andExpect(status().isBadRequest());
    assertThat(tx(() -> entityManager.find(Person.class, UUID.fromString(person)).getEmail()))
        .isEqualTo("institution-corrected@example.test");
    assertThat(
            tx(
                () ->
                    users
                        .findByPerson_IdAndInstitution_Id(
                            UUID.fromString(person), s.institution().getId())
                        .isEmpty()))
        .isTrue();
    request(s, body(patch(access), Map.of("enabled", true, "email", "ignored@example.test")), 200);
    final var corrected =
        events.stream(PersonAccessInvited.class)
            .filter(
                event ->
                    event.personId().toString().equals(person)
                        && event.email().equals("institution-corrected@example.test"))
            .findFirst()
            .orElseThrow();
    final var activation =
        Map.of("token", corrected.token(), "password", PASSWORD, "confirmPassword", PASSWORD);
    mvc.perform(body(post("/api/v1/auth/own-access/activate"), activation))
        .andExpect(status().isNoContent());
    mvc.perform(body(post("/api/v1/auth/own-access/activate"), activation))
        .andExpect(status().isBadRequest());
    final String document =
        tx(
            () ->
                users
                    .findByPerson_IdAndInstitution_Id(
                        UUID.fromString(person), s.institution().getId())
                    .orElseThrow()
                    .getDocumentNumber());
    tx(
        () -> {
          assignRoles.execute(
              entityManager.find(Person.class, UUID.fromString(person)),
              SystemRoleCode.INSTITUTIONAL_AUTHORITY);
          return null;
        });
    final var login = login(s, document);
    final String token = login.get("tokens").get("accessToken").asString();
    final String refresh = login.get("tokens").get("refreshToken").asString();
    request(s, body(patch(access), Map.of("enabled", false)), 200);
    final var disabledAttempt =
        mvc.perform(
                body(
                    post("/api/v1/auth/login/identify"),
                    Map.of("institutionId", s.institution().getId(), "documentNumber", document)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    mvc.perform(
            body(
                post("/api/v1/auth/login/password"),
                Map.of(
                    "loginAttemptId",
                    json.readTree(disabledAttempt.getContentAsString())
                        .get("loginAttemptId")
                        .asString(),
                    "password",
                    PASSWORD,
                    "rememberMe",
                    false)))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
    mvc.perform(body(post("/api/v1/auth/refresh"), Map.of("refreshToken", refresh)))
        .andExpect(status().isUnauthorized());
    request(s, get(profile(s, person)), 200);
    request(s, body(patch(access), Map.of("enabled", true)), 200);
    login(s, document);
    tx(
        () -> {
          users
              .findByPerson_IdAndInstitution_Id(UUID.fromString(person), s.institution().getId())
              .orElseThrow()
              .updateAccess(false);
          return null;
        });
    request(s, body(patch(access), Map.of("enabled", true)), 400);
    tx(
        () -> {
          users
              .findByPerson_IdAndInstitution_Id(UUID.fromString(person), s.institution().getId())
              .orElseThrow()
              .updateAccess(true);
          return null;
        });
    request(s, delete(base(s) + "/representations/" + person), 204);
    login(s, document);
    request(s, get(profile(s, person)), 403);
    tx(
        () -> {
          users
              .findByPerson_IdAndInstitution_Id(UUID.fromString(person), s.institution().getId())
              .orElseThrow()
              .updateAccess(false);
          return null;
        });
    final var blockedLink =
        request(
            s,
            body(
                post(base(s) + "/representations"),
                Map.of(
                    "documentNumber",
                    document,
                    "firstName",
                    "Maria",
                    "lastName",
                    "Delgado",
                    "birthDate",
                    "1956-01-01",
                    "relationship",
                    "OTHER",
                    "isPrimaryContact",
                    true,
                    "ownAccessRequested",
                    true,
                    "email",
                    "dummy-person@example.test")),
            201);
    request(
        s,
        body(
            post(
                base(s)
                    + "/guardian-links/"
                    + blockedLink.get("personGuardianId").asString()
                    + "/approve"),
            Map.of()),
        200);
    request(s, get(profile(s, person)), 200);
    final var managed = request(s, get(base(s) + "/representations"), 200);
    assertThat(managed.get(0).get("ownAccessStatus").asString()).isEqualTo("INSTITUTION_BLOCKED");
    assertPrivilegedInvitationsRequireInstitution();
  }

  private void assertPrivilegedInvitationsRequireInstitution() throws Exception {
    for (final String role : List.of("TEACHER", "INSTITUTIONAL_AUTHORITY", "CUSTOM_STAFF")) {
      final var s = setup();
      final var pending = represent(s, true);
      final UUID personId = UUID.fromString(pending.get("dependentPersonId").asString());
      final String access =
          base(s)
              + "/representations/"
              + pending.get("personGuardianId").asString()
              + "/own-access";
      final var invitation =
          events.stream(PersonAccessInvited.class)
              .filter(event -> event.personId().equals(personId))
              .findFirst()
              .orElseThrow();
      assignPrivilegedRole(s, personId, role);
      mvc.perform(
              body(
                  post("/api/v1/auth/own-access/activate"),
                  Map.of(
                      "token",
                      invitation.token(),
                      "password",
                      PASSWORD,
                      "confirmPassword",
                      PASSWORD)))
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("message")
                  .value(
                      ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionMessages
                          .OWN_ACCESS_INSTITUTION_REQUIRED));
      request(
          s,
          body(patch(access), Map.of("enabled", true, "email", "representative@example.test")),
          400);
      assertThat(
              tx(
                  () ->
                      users
                          .findByPerson_IdAndInstitution_Id(personId, s.institution().getId())
                          .isEmpty()))
          .isTrue();
      assertThat(
              request(s, get(base(s) + "/representations"), 200)
                  .get(0)
                  .get("ownAccessStatus")
                  .asString())
          .isEqualTo("INSTITUTION_REQUIRED");
      request(s, get(profile(s, personId.toString())), 200);
      request(s, body(patch(access), Map.of("enabled", false)), 200);
      assertInstitutionalInitialAccess(s, personId, role);

      final var alreadyPrivileged = setup();
      final var approved = represent(alreadyPrivileged, true, role);
      final String approvedPerson = approved.get("dependentPersonId").asString();
      assertThat(
              events.stream(PersonAccessInvited.class)
                  .noneMatch(event -> event.personId().toString().equals(approvedPerson)))
          .isTrue();
      assertThat(
              request(alreadyPrivileged, get(base(alreadyPrivileged) + "/representations"), 200)
                  .get(0)
                  .get("ownAccessStatus")
                  .asString())
          .isEqualTo("INSTITUTION_REQUIRED");
      request(alreadyPrivileged, get(profile(alreadyPrivileged, approvedPerson)), 200);
    }
  }

  private void assertInstitutionalInitialAccess(Scenario s, UUID personId, String role)
      throws Exception {
    final String path = base(s) + "/people/" + personId + "/access-invitations";
    final var email = Map.of("email", "institution-approved@example.test");
    final var initialRoles = request(s, get(profile(s, personId.toString())), 200).get("roles");
    request(s, body(post(path), email), 200);
    final var first = latestInvitation(personId);
    // Verify the real list contract by document, not a test-only identifier filter.
    final var document = tx(() -> entityManager.find(Person.class, personId).getDocumentNumber());
    final var row =
        request(s, get(base(s) + "/people?search=" + document), 200).get("items").get(0);
    assertThat(row.get("hasAccount").asBoolean()).isFalse();
    assertThat(row.get("accessInvitationPending").asBoolean()).isTrue();
    final var stored =
        tx(
            () ->
                entityManager
                    .createQuery(
                        "select i from PersonAccessInvitation i where i.person.id = :person and i.consumedAt is null",
                        PersonAccessInvitation.class)
                    .setParameter("person", personId)
                    .getSingleResult());
    assertThat(stored.isInstitutional()).isTrue();
    final var audit =
        tx(
            () ->
                entityManager
                    .createQuery(
                        "select a from AuditEvent a where a.entityId = :invitation and a.action = :action",
                        AuditEvent.class)
                    .setParameter("invitation", stored.getId())
                    .setParameter("action", AuditAction.PERSON_OWN_ACCESS_INVITED)
                    .getSingleResult());
    assertThat(audit.getActorPersonId()).isEqualTo(s.tutor().getId());
    assertThat(audit.getSubjectPersonId()).isEqualTo(personId);
    assertThat(stored.getTokenHash()).hasSize(44).isNotEqualTo(first.token());
    assertThat(stored.getExpiresAt())
        .isAfter(Instant.now().plus(23, ChronoUnit.HOURS))
        .isBefore(Instant.now().plus(25, ChronoUnit.HOURS));
    if (role.equals("INSTITUTIONAL_AUTHORITY")) {
      grantProvisioningOnly(s);
      request(s, body(post(path), email), 403);
      activate(first, 400);
      tx(
          () -> {
            assignRoles.execute(
                entityManager.find(Person.class, s.tutor().getId()),
                SystemRoleCode.INSTITUTIONAL_AUTHORITY);
            return null;
          });
    }
    request(s, body(post(path), email), 200);
    final var replacement = latestInvitation(personId);
    assertThat(replacement.token()).isNotEqualTo(first.token());
    activate(first, 400);
    request(s, delete(path), 204);
    activate(replacement, 400);
    request(s, body(post(path), email), 200);
    final var revokedIssuer = latestInvitation(personId);
    tx(
        () -> {
          assignRoles.execute(
              entityManager.find(Person.class, s.tutor().getId()), SystemRoleCode.APPLICANT);
          return null;
        });
    activate(revokedIssuer, 400);
    request(s, body(post(path), email), 403);
    tx(
        () -> {
          assignRoles.execute(
              entityManager.find(Person.class, s.tutor().getId()),
              SystemRoleCode.INSTITUTIONAL_AUTHORITY);
          return null;
        });
    final var foreign = setup();
    assertThatThrownBy(
            () ->
                tx(
                    () ->
                        entityManager
                            .createNativeQuery(
                                "update person_access_invitations set institutional_issuer_person_id = :issuer where invitation_id = :invitation")
                            .setParameter("issuer", foreign.tutor().getId())
                            .setParameter("invitation", stored.getId())
                            .executeUpdate()))
        .hasStackTraceContaining("person_access_invitation_institutional_issuer_fk");
    assertThatThrownBy(
            () ->
                tx(
                    () ->
                        entityManager
                            .createNativeQuery(
                                "update person_access_invitations set institutional_issuer_person_id = null where invitation_id = :invitation")
                            .setParameter("invitation", stored.getId())
                            .executeUpdate()))
        .hasStackTraceContaining("person_access_invitation_origin_check");
    request(foreign, body(post(path), email), 403);
    final var subjectActor =
        new UsernamePasswordAuthenticationToken(
            JwtAuthenticatedUser.builder()
                .userId(UUID.randomUUID())
                .personId(personId)
                .institutionId(s.institution().getId())
                .documentNumber(document)
                .build(),
            null,
            List.of());
    mvc.perform(body(post(path), email).with(authentication(subjectActor)))
        .andExpect(status().isForbidden());
    request(s, body(post(path), email), 200);
    final var approved = latestInvitation(personId);
    assertThat(request(s, get(profile(s, personId.toString())), 200).get("roles"))
        .isEqualTo(initialRoles);
    // An institutional invitation does not depend on a representative retaining their link.
    request(s, delete(base(s) + "/representations/" + personId), 204);
    activate(approved, 204);
    activate(approved, 400);
    final var signedIn = login(s, document);
    final var response =
        mvc.perform(
                get("/api/v1/auth/me")
                    .header(
                        "Authorization",
                        "Bearer " + signedIn.get("tokens").get("accessToken").asString()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    final var me = json.readTree(response.getContentAsString()).get("user");
    assertThat(me.get("roles")).isEqualTo(initialRoles);
    assertThat(me.get("personId").asString()).isEqualTo(personId.toString());
    if (!role.equals("TEACHER")) {
      assertThat(me.get("permissions").toString())
          .contains(PermissionCode.INSTITUTION_PERSON_CREATE.getCode());
    } else {
      assertThat(me.get("permissions").toString())
          .doesNotContain(PermissionCode.INSTITUTION_PERSON_CREATE.getCode());
    }
    final var activatedRow =
        request(s, get(base(s) + "/people?search=" + document), 200).get("items").get(0);
    assertThat(activatedRow.get("hasAccount").asBoolean()).isTrue();
    assertThat(activatedRow.get("accessInvitationPending").asBoolean()).isFalse();
    request(
        s, body(patch(base(s) + "/people/" + personId + "/status"), Map.of("enabled", false)), 204);
    request(s, body(post(path), email), 400);
    request(s, delete(path), 400);
    assertThat(
            tx(
                () ->
                    users
                        .findByPerson_IdAndInstitution_Id(personId, s.institution().getId())
                        .orElseThrow()
                        .isAccessEnabled()))
        .isFalse();
    assertThat(
            tx(
                () ->
                    encoder.matches(
                        PASSWORD,
                        users
                            .findByPerson_IdAndInstitution_Id(personId, s.institution().getId())
                            .orElseThrow()
                            .getPassword())))
        .isTrue();
  }

  private void grantProvisioningOnly(Scenario s) {
    tx(
        () -> {
          final var person = entityManager.find(Person.class, s.tutor().getId());
          assignRoles.execute(person, SystemRoleCode.APPLICANT);
          final var role =
              Role.customInstitutional(
                  "PROVISIONER", "Altas institucionales", person.getInstitution());
          entityManager.persist(role);
          for (final var code :
              PermissionCode.withRequiredPermissions(
                  java.util.Set.of(
                      PermissionCode.INSTITUTION_PERSON_CREATE,
                      PermissionCode.INSTITUTION_USER_STATUS_UPDATE,
                      PermissionCode.INSTITUTION_ROLE_ASSIGN))) {
            final var permission =
                entityManager
                    .createQuery(
                        "select p from Permission p where p.code = :code", Permission.class)
                    .setParameter("code", code.getCode())
                    .getSingleResult();
            entityManager.persist(RolePermission.of(role, permission));
          }
          assignRoles.execute(person, role, false);
          return null;
        });
  }

  private PersonAccessInvited latestInvitation(UUID personId) {
    return events.stream(PersonAccessInvited.class)
        .filter(event -> event.personId().equals(personId))
        .reduce((previous, current) -> current)
        .orElseThrow();
  }

  private void activate(PersonAccessInvited invitation, int expectedStatus) throws Exception {
    mvc.perform(
            body(
                post("/api/v1/auth/own-access/activate"),
                Map.of(
                    "token",
                    invitation.token(),
                    "password",
                    PASSWORD,
                    "confirmPassword",
                    PASSWORD)))
        .andExpect(status().is(expectedStatus));
  }

  private void assignPrivilegedRole(Scenario s, UUID personId, String code) {
    tx(
        () -> {
          final var person = entityManager.find(Person.class, personId);
          if (code.equals("CUSTOM_STAFF")) {
            final var role =
                Role.customInstitutional(code, "Personal autorizado", person.getInstitution());
            entityManager.persist(role);
            final var permission =
                entityManager
                    .createQuery(
                        "select p from Permission p where p.code = :code", Permission.class)
                    .setParameter("code", PermissionCode.INSTITUTION_PERSON_CREATE.getCode())
                    .getSingleResult();
            entityManager.persist(RolePermission.of(role, permission));
            assignRoles.execute(person, role, false);
          } else {
            assignRoles.execute(person, SystemRoleCode.valueOf(code));
          }
          return null;
        });
  }

  private JsonNode login(Scenario s, String document) throws Exception {
    final var identify =
        mvc.perform(
                body(
                    post("/api/v1/auth/login/identify"),
                    Map.of("institutionId", s.institution().getId(), "documentNumber", document)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    final String attempt =
        json.readTree(identify.getContentAsString()).get("loginAttemptId").asString();
    final var response =
        mvc.perform(
                body(
                    post("/api/v1/auth/login/password"),
                    Map.of("loginAttemptId", attempt, "password", PASSWORD, "rememberMe", false)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    return json.readTree(response.getContentAsString());
  }

  private Scenario scenario() {
    final String suffix = UUID.randomUUID().toString().substring(0, 8);
    final Institution institution =
        InstitutionalTestData.createInstitution(entityManager, "guardian-" + suffix);
    // Dependents are granted the applicant role, which must exist for the institution.
    institutionRoleProvisioner.provision(institution);
    final Person tutor = person(institution);
    final TrainingPath trainingPath =
        InstitutionalTestData.persist(
            entityManager, TrainingPath.create(institution, "Instrumento " + suffix, null));
    final DocumentDefinition document = DocumentDefinition.create(institution);
    document.update("DNI " + suffix, "Frente y dorso", List.of("application/pdf"), true);
    InstitutionalTestData.persist(entityManager, document);
    final TrainingPathDocumentRequirement requirement =
        TrainingPathDocumentRequirement.create(trainingPath, document);
    requirement.update(DocumentRequirementLevel.AT_SUBMISSION, 1, true, null);
    InstitutionalTestData.persist(entityManager, requirement);
    final StudyPlan plan =
        StudyPlan.create(institution, trainingPath, "Piano " + suffix, LocalDate.now(), null);
    plan.activate();
    InstitutionalTestData.persist(entityManager, plan);
    final AcademicYear year =
        InstitutionalTestData.persist(
            entityManager,
            AcademicYear.create(
                institution,
                2026,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 1, 1)));
    year.transitionTo(AcademicYearStatus.ACTIVE);
    final EnrollmentPeriod period =
        InstitutionalTestData.persist(
            entityManager,
            EnrollmentPeriod.builder()
                .institution(institution)
                .academicYear(year)
                .name("Periodo " + suffix)
                .startDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .endDate(Instant.now().plus(30, ChronoUnit.DAYS))
                .status(EnrollmentPeriodStatus.OPEN)
                .build());
    final AcademicSpace space =
        InstitutionalTestData.persist(
            entityManager,
            AcademicSpace.create(
                institution,
                "Espacio " + suffix,
                "",
                AcademicSpaceType.SUBJECT,
                AcademicSpaceFormat.INDIVIDUAL));
    final StudyPlanSpace planSpace =
        InstitutionalTestData.persist(
            entityManager,
            StudyPlanSpace.create(
                institution,
                plan,
                space,
                null,
                RequirementType.REQUIRED,
                1,
                ApprovalMode.FINAL_EXAM));
    InstitutionalTestData.persist(entityManager, Course.create(institution, planSpace, year));
    entityManager.flush();
    period.markScopeConfigured();
    final var offering = EnrollmentPeriodOffering.create(period, plan);
    offering.selectLevels(List.of(), true);
    period.getOfferings().add(offering);
    entityManager.persist(offering);
    entityManager.flush();

    return new Scenario(institution, tutor, plan, year);
  }

  private Person person(final Institution institution) {
    final Person person =
        InstitutionalTestData.persist(
            entityManager, InstitutionalTestData.person(institution, docNumber()));
    entityManager.flush();

    return person;
  }

  private UUID enrollForSearch(Scenario s, String personId) {
    return tx(
        () -> {
          final var person = entityManager.getReference(Person.class, UUID.fromString(personId));
          final var student =
              Student.builder()
                  .institution(s.institution())
                  .person(person)
                  .enrollmentDate(LocalDate.now())
                  .build();
          entityManager.persist(student);
          final var course =
              entityManager
                  .createQuery(
                      "select c from Course c where c.institution.id = :institution", Course.class)
                  .setParameter("institution", s.institution().getId())
                  .getSingleResult();
          final var courseClass =
              CourseClass.create(
                  s.institution(), course, personId.equals(s.tutor().getId().toString()) ? 2 : 1);
          entityManager.persist(courseClass);
          final var enrollment =
              CourseEnrollment.create(
                  s.institution(),
                  student,
                  course,
                  courseClass,
                  CourseEnrollmentSource.MANUAL,
                  null,
                  Instant.now());
          entityManager.persist(enrollment);
          entityManager.flush();
          return enrollment.getId();
        });
  }

  private String docNumber() {
    return String.format("%08d", Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000));
  }

  private record Scenario(
      Institution institution, Person tutor, StudyPlan plan, AcademicYear year) {}
}
