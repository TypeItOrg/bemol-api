package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.boero_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.interfaces.AuditEventRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionRoleProvisioner;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentPeriod;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentPeriodStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentApplicationNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollmentApplicationResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollmentAttachmentResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollmentDraftData;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.ResponsibleDto;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.StartEnrollmentApplicationRequest;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.UpdateEnrollmentDraftRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.DependentAlreadyLinkedException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.GuardianLinkAlreadyResolvedException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.GuardianLinkNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.UnauthorizedGuardianshipException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.CreateGuardianDependentRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianDependentResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.ListGuardianDependentsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.RegisterGuardianDependentUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.ResolveGuardianLinkUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.UnlinkGuardianDependentUseCase;
import ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData;
import ar.edu.utn.frvm.typeit.boero_api.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Exercises the guardian flow against a real Postgres migrated with Flyway: the access rules live
 * in JPQL ({@code EXISTS} over {@code person_guardians}) and pessimistic locks, which mocked
 * repositories cannot verify.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@IntegrationTest
class EnrollmentApplicationGuardianshipPostgresIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"));

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @Autowired private EnrollmentApplicationService service;
  @Autowired private RegisterGuardianDependentUseCase registerDependent;
  @Autowired private ListGuardianDependentsUseCase listDependents;
  @Autowired private UnlinkGuardianDependentUseCase unlinkDependent;
  @Autowired private ResolveGuardianLinkUseCase resolveLink;
  @Autowired private ListMyEnrollmentApplicationsUseCase listMine;
  @Autowired private GetMyEnrollmentApplicationUseCase getMine;
  @Autowired private PersonGuardianRepository personGuardianRepository;
  @Autowired private EnrollmentAttachmentService attachmentService;
  @Autowired private EntityManager entityManager;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private InstitutionRoleProvisioner institutionRoleProvisioner;

  private static final Path STORAGE_DIR = createStorageDir();

  private static Path createStorageDir() {
    try {
      return Files.createTempDirectory("boero-guardianship-storage");
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  @DynamicPropertySource
  static void infrastructureProperties(final DynamicPropertyRegistry registry) {
    registry.add("app.storage.enrollment.base-dir", STORAGE_DIR::toString);
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("spring.flyway.enabled", () -> true);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/dev");
    registry.add("spring.flyway.sql-migration-prefix", () -> "");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
  }

  @Test
  @Transactional
  @DisplayName("Should audit the tutor acting for the dependent")
  void auditsActionsOnBehalf() {
    final Scenario scenario = scenario();
    final GuardianDependentResponse dependent = registerDependent(scenario);

    service.startOrGetApplication(
        scenario.institution().getId(),
        scenario.tutor().getId(),
        startRequest(scenario, dependent.dependentPersonId()));
    entityManager.flush();

    final List<AuditEvent> events =
        auditEventRepository
            .search(
                scenario.institution().getId(),
                dependent.dependentPersonId(),
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 10))
            .getContent();

    assertThat(events)
        .extracting(AuditEvent::getAction)
        .containsExactlyInAnyOrder(
            AuditAction.GUARDIAN_LINK_REQUESTED,
            AuditAction.GUARDIAN_LINK_APPROVED,
            AuditAction.ENROLLMENT_APPLICATION_STARTED);
    assertThat(events)
        .filteredOn(event -> event.getAction() != AuditAction.GUARDIAN_LINK_APPROVED)
        .allSatisfy(
            event -> {
              assertThat(event.getActorPersonId()).isEqualTo(scenario.tutor().getId());
              assertThat(event.isActedOnBehalf()).isTrue();
            });
    // The institution, not the tutor, is the actor of the approval.
    assertThat(events)
        .filteredOn(event -> event.getAction() == AuditAction.GUARDIAN_LINK_APPROVED)
        .allSatisfy(
            event -> assertThat(event.getActorPersonId()).isNotEqualTo(scenario.tutor().getId()));
  }

  @Test
  @Transactional
  @DisplayName("Should reject updating an audit event")
  void rejectsUpdatingAuditEvents() {
    final Scenario scenario = scenario();
    registerDependent(scenario);
    entityManager.flush();
    assertThat(countAuditEvents(scenario)).isPositive();

    assertThatThrownBy(
            () ->
                entityManager
                    .createNativeQuery(
                        "UPDATE audit_events SET action = 'ENROLLMENT_APPLICATION_SUBMITTED' "
                            + "WHERE institution_id = :institutionId")
                    .setParameter("institutionId", scenario.institution().getId())
                    .executeUpdate())
        .rootCause()
        .hasMessageContaining("append-only");
  }

  @Test
  @Transactional
  @DisplayName("Should reject deleting an audit event")
  void rejectsDeletingAuditEvents() {
    final Scenario scenario = scenario();
    registerDependent(scenario);
    entityManager.flush();
    assertThat(countAuditEvents(scenario)).isPositive();

    assertThatThrownBy(
            () ->
                entityManager
                    .createNativeQuery(
                        "DELETE FROM audit_events WHERE institution_id = :institutionId")
                    .setParameter("institutionId", scenario.institution().getId())
                    .executeUpdate())
        .rootCause()
        .hasMessageContaining("append-only");
  }

  private long countAuditEvents(final Scenario scenario) {
    return ((Number)
            entityManager
                .createNativeQuery(
                    "SELECT COUNT(*) FROM audit_events WHERE institution_id = :institutionId")
                .setParameter("institutionId", scenario.institution().getId())
                .getSingleResult())
        .longValue();
  }

  @Test
  @Transactional
  @DisplayName("Should let a tutor start, edit, list and lose access to a dependent's application")
  void tutorActsForDependentWhileLinked() {
    final Scenario scenario = scenario();
    final GuardianDependentResponse dependent = registerDependent(scenario);

    final EnrollmentApplicationResponse started =
        service.startOrGetApplication(
            scenario.institution().getId(),
            scenario.tutor().getId(),
            startRequest(scenario, dependent.dependentPersonId()));
    entityManager.flush();

    assertThat(started.personId()).isEqualTo(dependent.dependentPersonId());
    assertThat(started.submittedByPersonId()).isEqualTo(scenario.tutor().getId());
    assertThat(started.data().getResponsible().getFullName()).isEqualTo("Ana Garcia");
    assertThat(started.data().getResponsible().getDocumentNumber())
        .isEqualTo(scenario.tutor().getDocumentNumber());

    final EnrollmentApplicationResponse edited =
        service.updateDraft(
            scenario.tutor().getId(),
            started.applicationId(),
            UpdateEnrollmentDraftRequest.builder()
                .data(
                    EnrollmentDraftData.builder()
                        .responsible(ResponsibleDto.builder().phoneNumber("3534112233").build())
                        .build())
                .build());
    assertThat(edited.data().getResponsible().getPhoneNumber()).isEqualTo("3534112233");

    assertThat(
            listMine
                .execute(
                    scenario.institution().getId(),
                    scenario.tutor().getId(),
                    null,
                    null,
                    null,
                    PageRequest.of(0, 10))
                .getContent())
        .extracting(EnrollmentApplicationResponse::applicationId)
        .containsExactly(started.applicationId());
    assertThat(
            listMine
                .execute(
                    scenario.institution().getId(),
                    scenario.tutor().getId(),
                    UUID.randomUUID(),
                    null,
                    null,
                    PageRequest.of(0, 10))
                .getContent())
        .isEmpty();
    assertThat(
            getMine
                .execute(
                    scenario.institution().getId(),
                    scenario.tutor().getId(),
                    started.applicationId())
                .applicationId())
        .isEqualTo(started.applicationId());
    assertThat(
            service
                .getApplicationById(null, scenario.tutor().getId(), started.applicationId())
                .applicationId())
        .isEqualTo(started.applicationId());
    assertThat(
            listDependents
                .execute(scenario.institution().getId(), scenario.tutor().getId())
                .getFirst()
                .activeApplicationsCount())
        .isEqualTo(1);

    unlinkDependent.execute(
        scenario.institution().getId(), scenario.tutor().getId(), dependent.dependentPersonId());
    entityManager.flush();

    assertThatThrownBy(
            () ->
                getMine.execute(
                    scenario.institution().getId(),
                    scenario.tutor().getId(),
                    started.applicationId()))
        .isInstanceOf(EnrollmentApplicationNotFoundException.class);
    assertThatThrownBy(
            () ->
                service.updateDraft(
                    scenario.tutor().getId(),
                    started.applicationId(),
                    UpdateEnrollmentDraftRequest.builder().build()))
        .isInstanceOf(EnrollmentApplicationNotFoundException.class);
    assertThat(
            listMine
                .execute(
                    scenario.institution().getId(),
                    scenario.tutor().getId(),
                    null,
                    null,
                    null,
                    PageRequest.of(0, 10))
                .getContent())
        .isEmpty();
  }

  @Test
  @Transactional
  @DisplayName("Should let the other tutor of the same dependent see the application")
  void otherTutorOfTheSameDependentSeesTheApplication() {
    final Scenario scenario = scenario();
    final GuardianDependentResponse dependent = registerDependent(scenario);
    final EnrollmentApplicationResponse started =
        service.startOrGetApplication(
            scenario.institution().getId(),
            scenario.tutor().getId(),
            startRequest(scenario, dependent.dependentPersonId()));
    final Person otherTutor = person(scenario.institution());
    personGuardianRepository.saveAndFlush(
        PersonGuardian.builder()
            .institution(scenario.institution())
            .tutorPerson(otherTutor)
            .dependentPerson(
                entityManager.getReference(Person.class, dependent.dependentPersonId()))
            .relationship(GuardianRelationship.MOTHER)
            .status(GuardianLinkStatus.ACTIVE)
            .build());

    assertThat(
            listMine
                .execute(
                    scenario.institution().getId(),
                    otherTutor.getId(),
                    dependent.dependentPersonId(),
                    null,
                    null,
                    PageRequest.of(0, 10))
                .getContent())
        .extracting(EnrollmentApplicationResponse::applicationId)
        .containsExactly(started.applicationId());
    assertThat(
            getMine
                .execute(
                    scenario.institution().getId(), otherTutor.getId(), started.applicationId())
                .applicationId())
        .isEqualTo(started.applicationId());
  }

  @Test
  @Transactional
  @DisplayName("Should keep another child's application out of reach of an unrelated tutor")
  void unrelatedTutorCannotReachTheApplication() {
    final Scenario scenario = scenario();
    final GuardianDependentResponse dependent = registerDependent(scenario);
    final EnrollmentApplicationResponse started =
        service.startOrGetApplication(
            scenario.institution().getId(),
            scenario.tutor().getId(),
            startRequest(scenario, dependent.dependentPersonId()));
    final Person stranger = person(scenario.institution());

    assertThatThrownBy(
            () ->
                service.startOrGetApplication(
                    scenario.institution().getId(),
                    stranger.getId(),
                    startRequest(scenario, dependent.dependentPersonId())))
        .isInstanceOfSatisfying(
            UnauthorizedGuardianshipException.class,
            exception -> assertThat(exception.code()).isEqualTo("GUARDIANSHIP_UNAUTHORIZED"));
    assertThatThrownBy(
            () ->
                getMine.execute(
                    scenario.institution().getId(), stranger.getId(), started.applicationId()))
        .isInstanceOf(EnrollmentApplicationNotFoundException.class);
    assertThatThrownBy(
            () ->
                service.updateDraft(
                    stranger.getId(),
                    started.applicationId(),
                    UpdateEnrollmentDraftRequest.builder().build()))
        .isInstanceOf(EnrollmentApplicationNotFoundException.class);
    assertThatThrownBy(
            () -> service.getApplicationById(null, stranger.getId(), started.applicationId()))
        .isInstanceOf(EnrollmentApplicationNotFoundException.class);
  }

  @Test
  @Transactional
  @DisplayName("Should let a tutor submit and manage attachments of a dependent's application")
  void tutorSubmitsAndAttachesForDependent() {
    final Scenario scenario = scenario();
    final GuardianDependentResponse dependent = registerDependent(scenario);
    final EnrollmentApplicationResponse started =
        service.startOrGetApplication(
            scenario.institution().getId(),
            scenario.tutor().getId(),
            startRequest(scenario, dependent.dependentPersonId()));
    final Authentication tutor = authentication(scenario, scenario.tutor());

    final EnrollmentAttachmentResponse uploaded =
        attachmentService.uploadAttachment(started.applicationId(), pdf(), "DNI_FRONT", tutor);
    assertThat(attachmentService.listAttachments(started.applicationId(), tutor))
        .extracting(EnrollmentAttachmentResponse::id)
        .containsExactly(uploaded.id());
    attachmentService.deleteAttachment(started.applicationId(), uploaded.id(), tutor);
    assertThat(attachmentService.listAttachments(started.applicationId(), tutor)).isEmpty();

    // The draft is incomplete, so a validation error proves the tutor got past the access check
    // (a stranger gets "not found" instead, see unrelatedTutorCannotSubmitOrTouchAttachments).
    assertThatThrownBy(
            () -> service.submitApplication(scenario.tutor().getId(), started.applicationId()))
        .isInstanceOf(EnrollmentValidationException.class);
  }

  @Test
  @Transactional
  @DisplayName("Should keep an unrelated tutor from submitting or touching attachments")
  void unrelatedTutorCannotSubmitOrTouchAttachments() {
    final Scenario scenario = scenario();
    final GuardianDependentResponse dependent = registerDependent(scenario);
    final EnrollmentApplicationResponse started =
        service.startOrGetApplication(
            scenario.institution().getId(),
            scenario.tutor().getId(),
            startRequest(scenario, dependent.dependentPersonId()));
    final Person stranger = person(scenario.institution());
    final Authentication strangerAuth = authentication(scenario, stranger);

    assertThatThrownBy(() -> service.submitApplication(stranger.getId(), started.applicationId()))
        .isInstanceOf(EnrollmentApplicationNotFoundException.class);
    assertThatThrownBy(
            () ->
                attachmentService.uploadAttachment(
                    started.applicationId(), pdf(), "DNI_FRONT", strangerAuth))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () -> attachmentService.listAttachments(started.applicationId(), strangerAuth))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @Transactional
  @DisplayName("Should keep a tutor from representing a person while the link is pending")
  void pendingLinkCannotRepresent() {
    final Scenario scenario = scenario();
    final GuardianDependentResponse pending = requestDependent(scenario, docNumber());

    assertThat(pending.status()).isEqualTo(GuardianLinkStatus.PENDING);
    assertThatThrownBy(
            () ->
                service.startOrGetApplication(
                    scenario.institution().getId(),
                    scenario.tutor().getId(),
                    startRequest(scenario, pending.dependentPersonId())))
        .isInstanceOf(UnauthorizedGuardianshipException.class);
  }

  @Test
  @Transactional
  @DisplayName("Should keep a rejected tutor out, allow asking again and block duplicate requests")
  void rejectedLinkCannotRepresentButCanBeRequestedAgain() {
    final Scenario scenario = scenario();
    final String document = docNumber();
    final GuardianDependentResponse first = requestDependent(scenario, document);

    assertThatThrownBy(() -> requestDependent(scenario, document))
        .isInstanceOf(DependentAlreadyLinkedException.class);

    resolveLink.reject(
        scenario.institution().getId(),
        person(scenario.institution()).getId(),
        first.personGuardianId());
    entityManager.flush();

    assertThatThrownBy(
            () ->
                service.startOrGetApplication(
                    scenario.institution().getId(),
                    scenario.tutor().getId(),
                    startRequest(scenario, first.dependentPersonId())))
        .isInstanceOf(UnauthorizedGuardianshipException.class);

    final GuardianDependentResponse second = requestDependent(scenario, document);
    entityManager.flush();

    assertThat(second.status()).isEqualTo(GuardianLinkStatus.PENDING);
    assertThat(second.personGuardianId()).isNotEqualTo(first.personGuardianId());
  }

  @Test
  @Transactional
  @DisplayName("Should not let another institution resolve a request, nor resolve it twice")
  void resolutionIsScopedAndOneShot() {
    final Scenario scenario = scenario();
    final Scenario otherInstitution = scenario();
    final GuardianDependentResponse pending = requestDependent(scenario, docNumber());
    final UUID reviewerId = person(scenario.institution()).getId();

    assertThatThrownBy(
            () ->
                resolveLink.approve(
                    otherInstitution.institution().getId(),
                    otherInstitution.tutor().getId(),
                    pending.personGuardianId()))
        .isInstanceOf(GuardianLinkNotFoundException.class);

    resolveLink.approve(scenario.institution().getId(), reviewerId, pending.personGuardianId());
    entityManager.flush();

    assertThatThrownBy(
            () ->
                resolveLink.reject(
                    scenario.institution().getId(), reviewerId, pending.personGuardianId()))
        .isInstanceOf(GuardianLinkAlreadyResolvedException.class);
    assertThat(
            service
                .startOrGetApplication(
                    scenario.institution().getId(),
                    scenario.tutor().getId(),
                    startRequest(scenario, pending.dependentPersonId()))
                .personId())
        .isEqualTo(pending.dependentPersonId());
  }

  @Test
  @Transactional
  @DisplayName("Should keep the self-service flow when no applicant is given")
  void startWithoutApplicantKeepsSelfService() {
    final Scenario scenario = scenario();

    final EnrollmentApplicationResponse started =
        service.startOrGetApplication(
            scenario.institution().getId(), scenario.tutor().getId(), startRequest(scenario, null));

    assertThat(started.personId()).isEqualTo(scenario.tutor().getId());
    assertThat(started.submittedByPersonId()).isEqualTo(scenario.tutor().getId());
    assertThat(started.data().getResponsible().getFullName()).isNull();
  }

  private Authentication authentication(final Scenario scenario, final Person person) {
    final JwtAuthenticatedUser principal =
        JwtAuthenticatedUser.builder()
            .userId(UUID.randomUUID())
            .personId(person.getId())
            .documentNumber(person.getDocumentNumber())
            .institutionId(scenario.institution().getId())
            .build();

    return new UsernamePasswordAuthenticationToken(principal, null, List.of());
  }

  private MockMultipartFile pdf() {
    return new MockMultipartFile("file", "dni.pdf", "application/pdf", "%PDF-1.4".getBytes());
  }

  /** Registers a dependent and has the institution approve the link, so the tutor can act. */
  private GuardianDependentResponse registerDependent(final Scenario scenario) {
    final GuardianDependentResponse requested = requestDependent(scenario, docNumber());
    resolveLink.approve(
        scenario.institution().getId(),
        person(scenario.institution()).getId(),
        requested.personGuardianId());

    return requested;
  }

  /** Registers a dependent whose link stays pending until the institution resolves it. */
  private GuardianDependentResponse requestDependent(
      final Scenario scenario, final String documentNumber) {
    return registerDependent.execute(
        scenario.institution().getId(),
        scenario.tutor().getId(),
        new CreateGuardianDependentRequest(
            documentNumber,
            "Mateo",
            "Gonzalez",
            LocalDate.now().minusYears(8),
            GuardianRelationship.FATHER,
            true));
  }

  private StartEnrollmentApplicationRequest startRequest(
      final Scenario scenario, final UUID applicantPersonId) {
    return StartEnrollmentApplicationRequest.builder()
        .studyPlanId(scenario.plan().getId())
        .academicYearId(scenario.year().getId())
        .applicantPersonId(applicantPersonId)
        .build();
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
    final StudyPlan plan =
        StudyPlan.create(institution, trainingPath, "Piano " + suffix, LocalDate.now(), null);
    plan.activate();
    InstitutionalTestData.persist(entityManager, plan);
    final AcademicYear year =
        InstitutionalTestData.persist(
            entityManager,
            AcademicYear.create(institution, 2026, null, null, LocalDate.of(2026, 1, 1)));
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

  private String docNumber() {
    return String.format("%08d", Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000));
  }

  private record Scenario(
      Institution institution, Person tutor, StudyPlan plan, AcademicYear year) {}
}
