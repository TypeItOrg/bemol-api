package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.*;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.*;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.*;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.*;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.GlobalAuditEventResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.GlobalAuditEventsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.*;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.*;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.*;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.*;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailSender;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.*;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.*;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.*;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.CreateEnrollmentPeriodUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.UpdateEnrollmentPeriodStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.UpdateEnrollmentPeriodUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.*;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.*;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.*;
import ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import ar.edu.utn.frvm.typeit.bemol_api.support.PostgresTestDatabase;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.query.AuditEntity;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@IntegrationTest
@org.springframework.test.context.event.RecordApplicationEvents
@TestPropertySource(properties = "spring.jpa.properties.hibernate.integration.envers.enabled=true")
class EnversCoveragePostgresIntegrationTest extends DatabaseMigrationTestSupport {
  private static final PostgresTestDatabase DATABASE =
      new PostgresTestDatabase("postgres:18-alpine", "envers_coverage");
  // The PostgreSQL scope constraint uses its real CURRENT_TIMESTAMP, not a Spring test clock.
  private static final Instant START =
      Instant.now()
          .minusSeconds(86_400)
          .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
          .plusNanos(123_456_789);
  private static final Instant END = START.plusSeconds(172_800);

  @DynamicPropertySource
  static void pilotDatabaseProperties(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.hikari.jdbc-url", DATABASE::getJdbcUrl);
  }

  @Autowired EntityManager em;
  @Autowired GlobalAuditEventsUseCase audit;
  @Autowired AuditEventRecorder recorder;
  @Autowired ObjectMapper json;
  @Autowired TransactionTemplate transaction;
  @MockitoBean MailSender mailSender;
  @Autowired CreateAcademicSpaceUseCase createSpace;
  @Autowired UpdateAcademicSpaceUseCase updateSpace;
  @Autowired CreateAcademicLevelUseCase createLevel;
  @Autowired UpdateAcademicLevelUseCase updateLevel;
  @Autowired DeleteAcademicLevelUseCase deleteLevel;
  @Autowired DocumentCatalogUseCase documents;
  @Autowired InstitutionRoleManagementService roles;
  @Autowired InstitutionRoleProvisioner provisionRoles;
  @Autowired ReplacePersonRolesUseCase replaceRoles;
  @Autowired CreatePersonUseCase createPerson;
  @Autowired ar.edu.utn.frvm.typeit.bemol_api.auth.services.ActivatePersonOwnAccessUseCase activate;
  @Autowired org.springframework.test.context.event.ApplicationEvents applicationEvents;
  @Autowired UpdatePersonByAdminUseCase updatePerson;
  @Autowired DeletePersonUseCase deletePerson;
  @Autowired CreateEnrollmentPeriodUseCase createPeriod;
  @Autowired UpdateEnrollmentPeriodUseCase updatePeriod;
  @Autowired UpdateEnrollmentPeriodStatusUseCase periodStatus;

  @Test
  void coversBusinessEntitiesCatalogsJsonAndRoleScopesWithoutPrivateSnapshots() {
    verifyCoverageAndPrivacy();
    authenticatePlatformAdministrator();
    final UUID institution = newInstitution();
    final var ids =
        requireNonNull(
            transaction.execute(
                state -> {
                  final var owner = em.find(Institution.class, institution);
                  final var first =
                      persist(TrainingPath.create(owner, "Primer trayecto", "Descripción privada"));
                  final var second = persist(TrainingPath.create(owner, "Segundo trayecto", null));
                  final var plan =
                      persist(StudyPlan.create(owner, first, "Plan inicial", null, null));
                  return List.of(first.getId(), second.getId(), plan.getId());
                }));
    final UUID space =
        createSpace
            .execute(
                institution,
                new CreateAcademicSpaceRequest(
                    "Armonía inicial",
                    "Descripción privada",
                    AcademicSpaceType.SUBJECT,
                    AcademicSpaceFormat.GRUPAL))
            .id();
    final var rename =
        new UpdateAcademicSpaceRequest(
            "Armonía actualizada",
            "Otra descripción privada",
            AcademicSpaceType.SUBJECT,
            AcademicSpaceFormat.GRUPAL);
    updateSpace.execute(institution, space, rename);
    updateSpace.execute(institution, space, rename);
    final var spaceRevisions = revisions(AcademicSpace.class, space);
    assertThat(spaceRevisions).hasSize(2);
    assertThat(snapshot(AcademicSpace.class, space, spaceRevisions.getFirst()).getName())
        .isEqualTo("Armonía inicial");
    assertThat(snapshot(AcademicSpace.class, space, spaceRevisions.getLast()).getName())
        .isEqualTo("Armonía actualizada");
    assertThat(snapshot(AcademicSpace.class, space, spaceRevisions.getLast()).getDescription())
        .isNull();
    assertThat(eventCount(space, "ACADEMIC_SPACE_UPDATED")).isEqualTo(1);

    transaction.executeWithoutResult(
        state -> em.find(Institution.class, institution).changePublicSubdomain("envers-relations"));
    transaction.executeWithoutResult(
        state ->
            assertThat(
                    AuditReaderFactory.get(em)
                        .find(AcademicSpace.class, space, spaceRevisions.getFirst())
                        .getInstitution()
                        .getPublicSubdomain())
                .isNull());

    final UUID level =
        createLevel
            .execute(institution, ids.get(2), new CreateAcademicLevelRequest(1, "Privado"))
            .id();
    updateLevel.execute(institution, level, new UpdateAcademicLevelRequest(2, "Más privado"));
    deleteLevel.execute(institution, level);
    final var levelRevisions = revisions(AcademicLevel.class, level);
    assertThat(levelRevisions).hasSize(3);
    assertThat(
            deletedSnapshot(AcademicLevel.class, level, levelRevisions.getLast()).getDisplayOrder())
        .isEqualTo(2);
    assertThat(eventCount(level, "STUDY_PLAN_LEVEL_DELETED")).isEqualTo(1);
    final var levelHistory =
        requireNonNull(lastDetail(level, "STUDY_PLAN_LEVEL_DELETED").history());
    assertThat(levelHistory.changes())
        .singleElement()
        .satisfies(
            change -> {
              assertThat(change.operation()).isEqualTo("DELETED");
              assertThat(change.previousKnown()).isTrue();
              assertThat(change.fields()).allSatisfy(field -> assertThat(field.after()).isNull());
              assertThat(change.fields())
                  .filteredOn(field -> field.field().equals("display_order"))
                  .singleElement()
                  .satisfies(field -> assertThat(field.before()).isEqualTo("2"));
            });

    final var document =
        documents
            .save(
                institution,
                null,
                new DocumentDefinitionRequest(
                    "Documento inicial",
                    "Instrucciones privadas",
                    List.of("application/pdf"),
                    true,
                    null,
                    List.of(),
                    null))
            .document();
    documents.save(
        institution,
        document.id(),
        new DocumentDefinitionRequest(
            "Documento final",
            "Instrucciones finales privadas",
            List.of("image/png", "application/pdf"),
            true,
            document.revision(),
            List.of(),
            null));
    final var documentRevisions = revisions(DocumentDefinition.class, document.id());
    assertThat(
            snapshot(DocumentDefinition.class, document.id(), documentRevisions.getFirst())
                .getAllowedFormats())
        .containsExactly("application/pdf");
    assertThat(
            snapshot(DocumentDefinition.class, document.id(), documentRevisions.getLast())
                .getAllowedFormats())
        .containsExactly("application/pdf", "image/png");
    assertThat(
            snapshot(DocumentDefinition.class, document.id(), documentRevisions.getLast())
                .getInstructions())
        .isNull();

    final var documentHistory =
        requireNonNull(lastDetail(document.id(), "DOCUMENT_DEFINITION_UPDATED").history());
    assertThat(documentHistory.changes())
        .singleElement()
        .satisfies(
            change ->
                assertThat(change.fields())
                    .filteredOn(field -> field.field().equals("allowed_formats"))
                    .singleElement()
                    .satisfies(
                        field -> {
                          assertThat(field.valueType()).isEqualTo("JSON");
                          assertThat(json.readTree(requireNonNull(field.before())))
                              .isEqualTo(json.readTree("[\"application/pdf\"]"));
                          assertThat(json.readTree(requireNonNull(field.after())))
                              .isEqualTo(json.readTree("[\"application/pdf\",\"image/png\"]"));
                        }));
    assertThat(json.writeValueAsString(documentHistory))
        .doesNotContain("Instrucciones", "instructions");

    final UUID person =
        createPerson
            .execute(
                institution,
                new CreatePersonRequest(
                    "Persona",
                    "Privada",
                    fixtures.randomDocumentNumber(),
                    UUID.randomUUID() + "@example.test",
                    null,
                    LocalDate.of(2000, 1, 1),
                    SystemRoleCode.APPLICANT))
            .personId();
    final String activationToken =
        applicationEvents.stream(
                ar.edu.utn.frvm.typeit.bemol_api.auth.events.PersonAccessInvited.class)
            .filter(event -> event.personId().equals(person))
            .findFirst()
            .orElseThrow()
            .token();
    activate.execute(
        new ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.ActivateOwnAccessRequest(
            activationToken, "DummyPassword2026!", "DummyPassword2026!"));
    updatePerson.execute(
        institution,
        person,
        new UpdatePersonByAdminRequest("Nombre Privado", null, null, null, null));
    assertThat(revisions(Person.class, person)).hasSize(1);
    assertThat(eventCount(person, "PERSON_UPDATED")).isEqualTo(1);
    final UUID role =
        roles
            .createAsPlatformAdmin(
                institution,
                new InstitutionRoleRequest(
                    "Rol inicial",
                    Set.of(
                        PermissionCode.TRAINING_PATH_READ.getCode(),
                        PermissionCode.ACADEMIC_SPACE_READ.getCode())))
            .id();
    final Number roleCreated = revisions(Role.class, role).getFirst();
    replaceRoles.execute(institution, person, roleRequest(role, ids.get(0)), true, Set.of());
    final UUID assignment =
        requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT person_role_assignment_id FROM person_role_assignments WHERE person_id = ? AND role_id = ?",
                UUID.class,
                person,
                role));
    final Number firstScope = revisions(PersonRoleAssignment.class, assignment).getFirst();
    replaceRoles.execute(institution, person, roleRequest(role, ids.get(1)), true, Set.of());
    final Number secondScope = revisions(PersonRoleAssignment.class, assignment).getLast();
    assertThat(pathsAt(assignment, firstScope)).containsExactly(ids.get(0));
    assertThat(pathsAt(assignment, secondScope)).containsExactly(ids.get(1));
    roles.updateAsPlatformAdmin(
        institution,
        role,
        new InstitutionRoleRequest(
            "Rol final", Set.of(PermissionCode.TRAINING_PATH_READ.getCode())));
    final Number roleUpdated = revisions(Role.class, role).getLast();
    assertThat(permissionCodesAt(role, roleCreated))
        .containsExactlyInAnyOrder(
            PermissionCode.TRAINING_PATH_READ.getCode(),
            PermissionCode.ACADEMIC_SPACE_READ.getCode());
    assertThat(permissionCodesAt(role, roleUpdated))
        .containsExactly(PermissionCode.TRAINING_PATH_READ.getCode());
    assertThat(eventCount(person, "PERSON_ROLES_CHANGED")).isEqualTo(2);
    final var roleDetail = lastDetail(role, "ROLE_UPDATED");
    final var roleHistory = requireNonNull(roleDetail.history());
    assertThat(roleHistory.changes())
        .filteredOn(change -> change.entity().equals("Rol"))
        .singleElement()
        .satisfies(
            change ->
                assertThat(change.fields())
                    .singleElement()
                    .satisfies(
                        field -> {
                          assertThat(field.before()).isEqualTo("Rol inicial");
                          assertThat(field.after()).isEqualTo("Rol final");
                        }));
    assertThat(roleHistory.changes())
        .filteredOn(change -> change.entity().equals("Permiso del rol"))
        .singleElement()
        .satisfies(
            change -> {
              assertThat(change.operation()).isEqualTo("DELETED");
              assertThat(change.fields())
                  .singleElement()
                  .satisfies(
                      field -> {
                        assertThat(field.field()).isEqualTo("permission_id");
                        assertThat(field.before()).isNotNull();
                        assertThat(field.after()).isNull();
                      });
            });
    final var assignmentHistory =
        requireNonNull(lastDetail(person, "PERSON_ROLES_CHANGED").history());
    assertThat(assignmentHistory.changes())
        .filteredOn(change -> change.entity().equals("Trayecto de asignación de rol"))
        .hasSize(2)
        .satisfies(
            changes -> {
              assertThat(changes)
                  .filteredOn(change -> change.operation().equals("DELETED"))
                  .singleElement()
                  .satisfies(
                      change ->
                          assertThat(change.fields().getFirst().before())
                              .isEqualTo(ids.get(0).toString()));
              assertThat(changes)
                  .filteredOn(change -> change.operation().equals("CREATED"))
                  .singleElement()
                  .satisfies(
                      change ->
                          assertThat(change.fields().getFirst().after())
                              .isEqualTo(ids.get(1).toString()));
            });
    assertThat(lastDetail(person, "PERSON_UPDATED").history()).isNull();
    assertThat(json.writeValueAsString(assignmentHistory))
        .doesNotContain("Nombre Privado", "DummyPassword", "email", "first_name");

    final UUID user =
        requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE person_id = ?", UUID.class, person));
    deletePerson.execute(institution, person);
    assertThat(
            snapshot(Person.class, person, revisions(Person.class, person).getLast()).isDeleted())
        .isTrue();
    final var userState = snapshot(User.class, user, revisions(User.class, user).getLast());
    assertThat(userState.isAccessEnabled()).isFalse();
    assertThat(userState.getPassword()).isNull();
    assertThat(
            snapshot(Person.class, person, revisions(Person.class, person).getLast())
                .getFirstName())
        .isNull();
  }

  @Test
  void recordsOrphanRemovalsAndDraftScopeCleanupAtomicallyWithPrivateAttachmentMetadata() {
    authenticatePlatformAdministrator();
    final var f = scopeFixture();
    final var reduced =
        new UpdateEnrollmentPeriodRequest(
            "Período",
            START,
            END,
            List.of(
                new EnrollmentPeriodOfferingRequest(
                    f.plan().getId(), List.of(f.level().getId()), false),
                new EnrollmentPeriodOfferingRequest(f.otherPlan().getId(), List.of(), true)));
    final var beforeTimestamp = applicationTimestamp(f.removed().getId());
    final var beforeRevisions =
        jdbcTemplate.queryForObject("SELECT count(*) FROM envers_poc_revisions", Long.class);
    transaction.executeWithoutResult(
        state -> {
          updatePeriod.execute(f.institution(), f.period(), reduced);
          em.flush();
          state.setRollbackOnly();
        });
    assertThat(selectionExists(f.removedSelection())).isTrue();
    assertThat(applicationTimestamp(f.removed().getId())).isEqualTo(beforeTimestamp);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM envers_poc_revisions", Long.class))
        .isEqualTo(beforeRevisions);
    assertThat(eventCount(f.period(), "ENROLLMENT_PERIOD_UPDATED")).isZero();
    updatePeriod.execute(f.institution(), f.period(), reduced);
    assertThat(selectionExists(f.removedSelection())).isFalse();
    assertThat(selectionExists(f.keptSelection())).isTrue();
    final var revisionsBeforeNoop =
        jdbcTemplate.queryForObject("SELECT count(*) FROM envers_poc_revisions", Long.class);
    updatePeriod.execute(f.institution(), f.period(), reduced);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM envers_poc_revisions", Long.class))
        .isEqualTo(revisionsBeforeNoop);
    assertThat(eventCount(f.period(), "ENROLLMENT_PERIOD_UPDATED")).isEqualTo(1);
    final Number removal =
        revisions(EnrollmentApplicationCourse.class, f.removedSelection()).getLast();
    assertThat(
            deletedSnapshot(EnrollmentApplicationCourse.class, f.removedSelection(), removal)
                .getCourse()
                .getId())
        .isEqualTo(f.removedCourse());
    assertThat(
            atRevision(
                EnrollmentApplicationCourse.class,
                removal,
                "enrollmentApplication",
                f.removed().getId()))
        .isEmpty();
    assertThat(revisions(EnrollmentPeriodOfferingLevel.class, f.removedLevel())).hasSize(2);
    assertThat(applicationTimestamp(f.removed().getId())).isAfter(beforeTimestamp);

    updatePeriod.execute(
        f.institution(),
        f.period(),
        new UpdateEnrollmentPeriodRequest(
            "Período",
            START,
            END,
            List.of(new EnrollmentPeriodOfferingRequest(f.otherPlan().getId(), List.of(), true))));
    assertThat(selectionExists(f.keptSelection())).isFalse();
    assertThat(revisions(EnrollmentPeriodOffering.class, f.removedOffering())).hasSize(2);
    assertThat(eventCount(f.period(), "ENROLLMENT_PERIOD_UPDATED")).isEqualTo(2);
    transaction.executeWithoutResult(
        state -> em.find(EnrollmentApplication.class, f.other().getId()).submit());
    final Number lastPeriod = revisions(EnrollmentPeriod.class, f.period()).getLast();
    assertThatThrownBy(
            () ->
                updatePeriod.execute(
                    f.institution(),
                    f.period(),
                    new UpdateEnrollmentPeriodRequest(
                        "Rechazado",
                        START,
                        END,
                        List.of(
                            new EnrollmentPeriodOfferingRequest(
                                f.plan().getId(), List.of(f.level().getId()), true)))))
        .isInstanceOf(EnrollmentValidationException.class);
    assertThat(revisions(EnrollmentPeriod.class, f.period()).getLast()).isEqualTo(lastPeriod);
    assertThat(selectionExists(f.otherSelection())).isTrue();
    assertThat(eventCount(f.period(), "ENROLLMENT_PERIOD_UPDATED")).isEqualTo(2);

    final UUID attachment =
        requireNonNull(
            transaction.execute(
                state -> {
                  final var application = em.find(EnrollmentApplication.class, f.other().getId());
                  final var document = DocumentDefinition.create(application.getInstitution());
                  document.update(
                      "Constancia", "Instrucciones privadas", List.of("application/pdf"), true);
                  persist(document);
                  final var request =
                      persist(
                          EnrollmentDocumentRequest.create(
                              application,
                              "Motivo privado",
                              Instant.now(),
                              UUID.randomUUID(),
                              "PLATFORM",
                              "Nombre privado"));
                  final var requirement =
                      persist(
                          EnrollmentDocumentRequirement.additional(
                              application,
                              document,
                              request,
                              DocumentRequirementLevel.BEFORE_CONFIRMATION,
                              1));
                  final var file =
                      persist(
                          EnrollmentAttachment.builder()
                              .enrollmentApplication(application)
                              .requirement(requirement)
                              .uploadedBy(UUID.randomUUID())
                              .uploaderType("PLATFORM")
                              .originalFileName("Archivo privado.pdf")
                              .storagePath("privado/archivo.pdf")
                              .contentType("application/pdf")
                              .fileSize(100L)
                              .build());
                  return file.getId();
                }));
    transaction.executeWithoutResult(
        state -> {
          final var file = em.find(EnrollmentAttachment.class, attachment);
          file.review(
              DocumentReviewStatus.OBSERVED,
              "Observación privada",
              UUID.randomUUID(),
              "PLATFORM",
              Instant.now());
          recorder.recordCurrentActor(
              file.getEnrollmentApplication().getInstitution(),
              file.getEnrollmentApplication().getApplicantPerson().getId(),
              AuditAction.ENROLLMENT_ATTACHMENT_REVIEWED,
              AuditEntityType.ENROLLMENT_ATTACHMENT,
              attachment,
              AuditOrigin.PLATFORM_ADMINISTRATION,
              Map.of());
        });
    final var fileState =
        snapshot(
            EnrollmentAttachment.class,
            attachment,
            revisions(EnrollmentAttachment.class, attachment).getLast());
    assertThat(fileState.getReviewStatus()).isEqualTo(DocumentReviewStatus.OBSERVED);
    assertThat(fileState.getContentType()).isEqualTo("application/pdf");
    assertThat(fileState.getObservation()).isNull();
    assertThat(fileState.getStoragePath()).isNull();
    assertThat(fileState.getOriginalFileName()).isNull();
    final var attachmentHistory =
        requireNonNull(lastDetail(attachment, "ENROLLMENT_ATTACHMENT_REVIEWED").history());
    assertThat(attachmentHistory.changes())
        .singleElement()
        .satisfies(
            change ->
                assertThat(change.fields())
                    .filteredOn(field -> field.field().equals("review_status"))
                    .singleElement()
                    .satisfies(
                        field -> {
                          assertThat(field.before()).isEqualTo("PENDING_REVIEW");
                          assertThat(field.after()).isEqualTo("OBSERVED");
                        }));
    assertThat(json.writeValueAsString(attachmentHistory))
        .doesNotContain(
            "Archivo privado",
            "privado/archivo",
            "Observación privada",
            "original_file_name",
            "storage_path",
            "observation");
  }

  private GlobalAuditEventResponse lastDetail(final UUID id, final String action) {
    return audit.get(
        requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT audit_event_id FROM audit_events WHERE entity_id = ? AND action = ? ORDER BY occurred_at DESC, audit_event_id DESC LIMIT 1",
                UUID.class,
                id,
                action)));
  }

  private UUID newInstitution() {
    return requireNonNull(
        transaction.execute(
            state -> {
              final var institution =
                  InstitutionalTestData.createInstitution(em, "envers-" + UUID.randomUUID());
              provisionRoles.provision(institution);
              return institution.getId();
            }));
  }

  private ScopeFixture scopeFixture() {
    final UUID institutionId = newInstitution();
    final var context =
        requireNonNull(
            transaction.execute(
                state -> {
                  final var institution = em.find(Institution.class, institutionId);
                  final var path = persist(TrainingPath.create(institution, "Trayecto", null));
                  final var plan =
                      persist(
                          StudyPlan.create(
                              institution, path, "Plan", LocalDate.of(2026, 1, 1), null));
                  final var otherPlan =
                      persist(
                          StudyPlan.create(
                              institution, path, "Otro plan", LocalDate.of(2026, 1, 1), null));
                  plan.activate();
                  otherPlan.activate();
                  final var level = persist(AcademicLevel.create(plan, "Nivel 1", 1, null));
                  final var year =
                      persist(
                          AcademicYear.create(
                              institution,
                              2026,
                              LocalDate.of(2026, 1, 1),
                              LocalDate.of(2026, 12, 31),
                              LocalDate.of(2026, 1, 1)));
                  year.transitionTo(AcademicYearStatus.ACTIVE);
                  return new ScopeContext(institutionId, path, plan, otherPlan, level, year);
                }));
    final UUID period =
        createPeriod
            .execute(
                institutionId,
                new CreateEnrollmentPeriodRequest(
                    context.year().getId(),
                    "Período",
                    START,
                    END,
                    List.of(
                        new EnrollmentPeriodOfferingRequest(
                            context.plan().getId(), List.of(context.level().getId()), true),
                        new EnrollmentPeriodOfferingRequest(
                            context.otherPlan().getId(), List.of(), true))))
            .id();
    periodStatus.execute(
        institutionId, period, new EnrollmentPeriodStatusRequest(EnrollmentPeriodStatus.OPEN));
    return requireNonNull(
        transaction.execute(
            state -> {
              final var institution = em.find(Institution.class, institutionId);
              final var actualPeriod = em.find(EnrollmentPeriod.class, period);
              final var removed = draft(institution, context, actualPeriod);
              final var kept = draft(institution, context, actualPeriod);
              final var other = draft(institution, context, actualPeriod);
              final var removedCourse =
                  fixtureCourse(institution, context.plan(), null, context.year());
              final var keptCourse =
                  fixtureCourse(institution, context.plan(), context.level(), context.year());
              final var otherCourse =
                  fixtureCourse(institution, context.otherPlan(), null, context.year());
              final var removedSelection =
                  persist(
                      EnrollmentApplicationCourse.create(
                          institution, removed, removedCourse, null));
              final var keptSelection =
                  persist(EnrollmentApplicationCourse.create(institution, kept, keptCourse, null));
              final var otherSelection =
                  persist(
                      EnrollmentApplicationCourse.create(institution, other, otherCourse, null));
              final var offering =
                  actualPeriod.getOfferings().stream()
                      .filter(value -> value.getStudyPlan().getId().equals(context.plan().getId()))
                      .findFirst()
                      .orElseThrow();
              final var unassigned =
                  offering.getLevels().stream()
                      .filter(value -> value.levelId() == null)
                      .findFirst()
                      .orElseThrow();
              return new ScopeFixture(
                  institutionId,
                  context.plan(),
                  context.otherPlan(),
                  context.level(),
                  period,
                  removed,
                  kept,
                  other,
                  removedSelection.getId(),
                  keptSelection.getId(),
                  otherSelection.getId(),
                  removedCourse.getId(),
                  offering.getId(),
                  unassigned.getId());
            }));
  }

  private EnrollmentApplication draft(
      final Institution institution, final ScopeContext f, final EnrollmentPeriod period) {
    final var person =
        persist(InstitutionalTestData.person(institution, fixtures.randomDocumentNumber()));
    return persist(
        EnrollmentApplication.createForTrainingPath(
            institution, person, f.path(), f.year(), period));
  }

  private Course fixtureCourse(
      final Institution institution,
      final StudyPlan plan,
      final @Nullable AcademicLevel level,
      final AcademicYear year) {
    final var space =
        persist(
            AcademicSpace.create(
                institution,
                "Espacio " + UUID.randomUUID(),
                null,
                AcademicSpaceType.SUBJECT,
                AcademicSpaceFormat.GRUPAL));
    final var placement =
        persist(
            StudyPlanSpace.create(
                institution,
                plan,
                space,
                level,
                RequirementType.REQUIRED,
                1,
                ApprovalMode.PROMOTION));
    return persist(Course.create(institution, placement, year));
  }

  private record ScopeContext(
      UUID institution,
      TrainingPath path,
      StudyPlan plan,
      StudyPlan otherPlan,
      AcademicLevel level,
      AcademicYear year) {}

  private record ScopeFixture(
      UUID institution,
      StudyPlan plan,
      StudyPlan otherPlan,
      AcademicLevel level,
      UUID period,
      EnrollmentApplication removed,
      EnrollmentApplication kept,
      EnrollmentApplication other,
      UUID removedSelection,
      UUID keptSelection,
      UUID otherSelection,
      UUID removedCourse,
      UUID removedOffering,
      UUID removedLevel) {}

  private <T> T persist(final T entity) {
    em.persist(entity);
    return entity;
  }

  private Timestamp applicationTimestamp(final UUID id) {
    return requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT updated_at FROM enrollment_applications WHERE enrollment_application_id = ?",
            Timestamp.class,
            id));
  }

  private boolean selectionExists(final UUID id) {
    return requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM enrollment_application_courses WHERE enrollment_application_course_id = ?",
                Long.class,
                id))
        == 1;
  }

  private long eventCount(final UUID id, final String action) {
    return requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_events WHERE entity_id = ? AND action = ?",
            Long.class,
            id,
            action));
  }

  private ReplacePersonRolesRequest roleRequest(final UUID role, final UUID path) {
    return new ReplacePersonRolesRequest(
        List.of(new AssignRoleRequest(role, AccessScope.TRAINING_PATHS, Set.of(path))));
  }

  private Set<UUID> pathsAt(final UUID assignment, final Number revision) {
    return requireNonNull(
        transaction.execute(
            state ->
                AuditReaderFactory.get(em)
                    .find(PersonRoleAssignment.class, assignment, revision)
                    .getTrainingPathIds()));
  }

  private Set<String> permissionCodesAt(final UUID role, final Number revision) {
    return requireNonNull(
        transaction.execute(
            state ->
                atRevision(RolePermission.class, revision, "role", role).stream()
                    .map(value -> value.getPermission().getCode())
                    .collect(Collectors.toSet())));
  }

  private List<Number> revisions(final Class<?> type, final UUID id) {
    return requireNonNull(
        transaction.execute(state -> AuditReaderFactory.get(em).getRevisions(type, id)));
  }

  private <T> T snapshot(final Class<T> type, final UUID id, final Number revision) {
    return requireNonNull(
        transaction.execute(state -> AuditReaderFactory.get(em).find(type, id, revision)));
  }

  private <T> T deletedSnapshot(final Class<T> type, final UUID id, final Number revision) {
    return requireNonNull(
        transaction.execute(
            state -> AuditReaderFactory.get(em).find(type, type.getName(), id, revision, true)));
  }

  private <T> List<T> atRevision(
      final Class<T> type, final Number revision, final String relation, final UUID id) {
    return requireNonNull(
        transaction.execute(
            state -> {
              final List<?> rows =
                  AuditReaderFactory.get(em)
                      .createQuery()
                      .forEntitiesAtRevision(type, revision)
                      .add(AuditEntity.relatedId(relation).eq(id))
                      .getResultList();
              return rows.stream().map(row -> type.cast(row)).toList();
            }));
  }

  private void verifyCoverageAndPrivacy() {
    final var expected =
        Set.of(
            "AcademicLevel",
            "AcademicSpace",
            "AcademicYear",
            "ApplicantPreference",
            "Course",
            "CourseClass",
            "CourseClassDay",
            "CourseClassSchedule",
            "CourseClassTeacher",
            "CourseEnrollment",
            "CourseEnrollmentSchedule",
            "DocumentDefinition",
            "EnrollmentApplication",
            "EnrollmentApplicationCourse",
            "EnrollmentApplicationSpace",
            "EnrollmentAttachment",
            "EnrollmentDocumentRequest",
            "EnrollmentDocumentRequirement",
            "EnrollmentPeriod",
            "EnrollmentPeriodOffering",
            "EnrollmentPeriodOfferingLevel",
            "Institution",
            "Instrument",
            "PasskeyCredential",
            "Person",
            "PersonAccessInvitation",
            "PersonGuardian",
            "PersonGuardianAttachment",
            "PersonRoleAssignment",
            "PlatformAccount",
            "PlatformAccountRole",
            "Prerequisite",
            "Role",
            "RolePermission",
            "Shift",
            "Student",
            "StudyPlan",
            "StudyPlanSpace",
            "StudyPlanSpaceInstrument",
            "TrainingPath",
            "TrainingPathDocumentRequirement",
            "User");
    transaction.executeWithoutResult(
        state -> {
          final var reader = AuditReaderFactory.get(em);
          final var actual =
              em.getMetamodel().getEntities().stream()
                  .filter(entity -> reader.isEntityClassAudited(entity.getJavaType()))
                  .map(entity -> entity.getName())
                  .collect(Collectors.toSet());
          assertThat(actual).isEqualTo(expected);
        });
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name LIKE 'envers_poc_%'",
                Long.class))
        .isEqualTo(44);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name LIKE 'envers_poc_%'",
                String.class))
        .doesNotContain(
            "password",
            "token_hash",
            "credential_id",
            "public_key_cose",
            "signature_count",
            "user_handle",
            "webauthn_user_handle",
            "attestation_object",
            "attestation_client_data_json",
            "label",
            "first_name",
            "last_name",
            "document_number",
            "phone_number",
            "email",
            "declared_email",
            "declared_first_name",
            "declared_last_name",
            "street",
            "number",
            "apartment",
            "floor",
            "neighborhood",
            "additional_info",
            "birth_date",
            "original_file_name",
            "storage_path",
            "observation",
            "reason",
            "rejection_reason",
            "resolution_reason_text",
            "action_reason",
            "instructions",
            "specific_instructions",
            "actor_name",
            "description",
            "receives_reasonable_adjustments",
            "adjustment_details",
            "updated_at");
  }
}
