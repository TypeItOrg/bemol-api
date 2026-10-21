package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClass;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClassDay;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClassSchedule;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClassTeacher;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Shift;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.CourseDay;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.CourseStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.ActiveStatusRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseClassDayRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseClassRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseClassScheduleRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseStatusRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateCourseRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateShiftRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.ReplaceCourseClassesRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateShiftRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.AcademicLifecycleService;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreateCourseUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreateShiftUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ReplaceCourseClassesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateCourseStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateShiftStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateShiftUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.audit.envers.EnversAuditTransaction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.AuditHistoryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.GlobalAuditEventResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.GlobalAuditEventsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AssignPersonSystemRoleUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionRoleProvisioner;
import ar.edu.utn.frvm.typeit.bemol_api.common.logging.RequestLoggingFilter;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailSender;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import ar.edu.utn.frvm.typeit.bemol_api.support.PostgresTestDatabase;
import jakarta.persistence.EntityManager;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.query.AuditEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.properties.hibernate.integration.envers.enabled=true")
class EnversPilotPostgresIntegrationTest extends DatabaseMigrationTestSupport {
  private static final PostgresTestDatabase DATABASE =
      new PostgresTestDatabase("postgres:18-alpine", "envers_pilot");

  @DynamicPropertySource
  static void pilotDatabaseProperties(final DynamicPropertyRegistry registry) {
    // Configure the pool without competing with the inherited shared datasource URL.
    registry.add("spring.datasource.hikari.jdbc-url", DATABASE::getJdbcUrl);
  }

  @Autowired EntityManager em;
  @Autowired MockMvc mvc;
  @Autowired GlobalAuditEventsUseCase audit;
  @Autowired ObjectMapper json;
  @Autowired TransactionTemplate transaction;
  @Autowired CreateShiftUseCase createShift;
  @Autowired UpdateShiftUseCase updateShift;
  @Autowired UpdateShiftStatusUseCase shiftStatus;
  @Autowired AcademicLifecycleService lifecycle;
  @Autowired CreateCourseUseCase createCourse;
  @Autowired ReplaceCourseClassesUseCase replaceClasses;
  @Autowired UpdateCourseStatusUseCase courseStatus;
  @Autowired InstitutionRoleProvisioner provisionRoles;
  @Autowired AssignPersonSystemRoleUseCase assignRole;
  @Autowired InstitutionRepository institutions;
  @Autowired PersonRepository people;
  @MockitoBean MailSender mailSender;

  @AfterEach
  void clearRequestId() {
    MDC.remove(RequestLoggingFilter.MDC_REQUEST_ID_KEY);
  }

  @Test
  void preservesShiftValuesActorAndSoftDeletionWithoutAuditingPrivateFieldsOrRollbacks() {
    assertThat(jdbcTemplate.queryForObject("SELECT current_database()", String.class))
        .isEqualTo("envers_pilot");
    authenticatePlatformAdministrator();
    MDC.put(RequestLoggingFilter.MDC_REQUEST_ID_KEY, "envers-pilot-turnos");
    final UUID institution = UUID.randomUUID();
    fixtures.insertTestInstitution(institution);
    final UUID shift =
        createShift
            .execute(institution, new CreateShiftRequest("Mañana", "Texto privado inicial"))
            .id();
    updateShift.execute(
        institution, shift, new UpdateShiftRequest("Matutino", "Texto privado inicial"));
    updateShift.execute(
        institution, shift, new UpdateShiftRequest("Matutino", "Texto privado inicial"));
    updateShift.execute(
        institution, shift, new UpdateShiftRequest("Matutino", "Otro texto privado"));
    final var revisions = revisions(Shift.class, shift);
    assertThat(revisions).hasSize(2);
    assertThat(snapshot(Shift.class, shift, revisions.getFirst()).getName()).isEqualTo("Mañana");
    final var updated = snapshot(Shift.class, shift, revisions.getLast());
    assertThat(updated.getName()).isEqualTo("Matutino");
    assertThat(updated.getDescription()).isNull();
    assertThat(updated.getInstitution().getId()).isEqualTo(institution);
    final var metadata =
        jdbcTemplate.queryForMap(
            "SELECT * FROM envers_poc_revisions WHERE id = ?", revisions.getLast());
    assertThat(metadata.get("actor_kind")).isEqualTo("PLATFORM");
    assertThat(metadata.get("actor_platform_account_id")).isNotNull();
    assertThat(metadata.get("request_id")).isEqualTo("envers-pilot-turnos");
    assertThat(metadata.get("occurred_at_epoch_ms")).isInstanceOf(Long.class);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT name_mod FROM envers_poc_shifts_aud WHERE shift_id = ? AND rev = ?",
                Boolean.class,
                shift,
                revisions.getLast()))
        .isTrue();
    assertThat(globalActions(shift))
        .containsExactlyInAnyOrder("SHIFT_CREATED", "SHIFT_UPDATED", "SHIFT_UPDATED");

    final var renameHistory = history(shift, revisions.getLast());
    assertThat(renameHistory.revision()).isEqualTo(revisions.getLast().longValue());
    assertThat(renameHistory.changes()).hasSize(1);
    assertThat(renameHistory.changes().getFirst().previousKnown()).isTrue();
    assertThat(renameHistory.changes().getFirst().fields())
        .singleElement()
        .satisfies(
            field -> {
              assertThat(field.field()).isEqualTo("name");
              assertThat(field.before()).isEqualTo("Mañana");
              assertThat(field.after()).isEqualTo("Matutino");
            });
    assertThat(json.writeValueAsString(renameHistory))
        .doesNotContain("Texto privado", "description");
    final var privateOnly = lastDetail(shift, "SHIFT_UPDATED");
    assertThat(privateOnly.history()).isNull();
    assertThat(privateOnly.details().get("changedFields")).isEqualTo(List.of("description"));

    transaction.executeWithoutResult(
        state -> {
          updateShift.execute(
              institution, shift, new UpdateShiftRequest("No debe quedar", "Texto privado"));
          em.flush();
          state.setRollbackOnly();
        });
    assertThat(revisions(Shift.class, shift)).hasSize(2);
    assertThat(globalActions(shift)).hasSize(3);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT name FROM shifts WHERE shift_id = ?", String.class, shift))
        .isEqualTo("Matutino");
    shiftStatus.execute(institution, shift, new ActiveStatusRequest(false));
    lifecycle.deleteShift(institution, shift, null);
    final var deletedRevision = revisions(Shift.class, shift).getLast();
    assertThat(snapshot(Shift.class, shift, deletedRevision).getDeletedAt()).isNotNull();
    lifecycle.restoreShift(institution, shift, null);
    assertThat(snapshot(Shift.class, shift, revisions(Shift.class, shift).getLast()).getDeletedAt())
        .isNull();

    final UUID person = UUID.randomUUID();
    fixtures.insertPerson(person, institution, fixtures.randomDocumentNumber(), false);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new TestingAuthenticationToken(
                new JwtAuthenticatedUser(
                    UUID.randomUUID(), person, "12345678", institution, UUID.randomUUID(), "test"),
                "",
                "ROLE_INSTITUTIONAL_USER"));
    // Dirty checking is sufficient; no repository.save or functional recorder call.
    transaction.executeWithoutResult(
        state -> em.find(Shift.class, shift).update("Cambio automático", "No se conserva"));
    final var institutional =
        jdbcTemplate.queryForMap(
            "SELECT * FROM envers_poc_revisions WHERE id = ?",
            revisions(Shift.class, shift).getLast());
    assertThat(institutional.get("actor_kind")).isEqualTo("INSTITUTION");
    assertThat(institutional.get("actor_person_id")).isEqualTo(person);
    SecurityContextHolder.clearContext();
    transaction.executeWithoutResult(
        state -> em.find(Shift.class, shift).update("Cambio de sistema", "No se conserva"));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT actor_kind FROM envers_poc_revisions WHERE id = ?",
                String.class,
                revisions(Shift.class, shift).getLast()))
        .isEqualTo("ANONYMOUS");
    final UUID legacyShift = UUID.randomUUID();
    fixtures.insertShift(legacyShift, institution, "Previo al piloto", true);
    authenticatePlatformAdministrator();
    updateShift.execute(institution, legacyShift, new UpdateShiftRequest("Primera revisión", null));
    final var legacyRevisions = revisions(Shift.class, legacyShift);
    assertThat(legacyRevisions).hasSize(1);
    assertThat(snapshot(Shift.class, legacyShift, legacyRevisions.getFirst()).getName())
        .isEqualTo("Primera revisión");
    final var legacyHistory = history(legacyShift, legacyRevisions.getFirst());
    assertThat(legacyHistory.changes())
        .singleElement()
        .satisfies(
            change -> {
              assertThat(change.previousKnown()).isFalse();
              assertThat(change.fields())
                  .filteredOn(field -> field.field().equals("name"))
                  .singleElement()
                  .satisfies(
                      field -> {
                        assertThat(field.before()).isNull();
                        assertThat(field.after()).isEqualTo("Primera revisión");
                      });
            });
    assertThat(history(shift, revisions.getLast())).isEqualTo(renameHistory);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name IN ('envers_poc_shifts_aud', 'envers_poc_courses_aud', 'envers_poc_course_classes_aud', 'envers_poc_course_class_teachers_aud', 'envers_poc_course_class_days_aud', 'envers_poc_course_class_schedules_aud')",
                String.class))
        .doesNotContain(
            "description",
            "first_name",
            "last_name",
            "email",
            "document_number",
            "password",
            "created_at",
            "updated_at");
  }

  @Test
  void preservesCompleteCourseClassHistoryAcrossReplacementRollbackAndSemanticNoop() {
    authenticatePlatformAdministrator();
    final UUID institution = UUID.randomUUID(), path = UUID.randomUUID(), plan = UUID.randomUUID();
    final UUID space = UUID.randomUUID(), placement = UUID.randomUUID(), year = UUID.randomUUID();
    final UUID firstTeacher = UUID.randomUUID(), secondTeacher = UUID.randomUUID();
    fixtures.insertTestInstitution(institution);
    transaction.executeWithoutResult(
        state -> provisionRoles.provision(institutions.findById(institution).orElseThrow()));
    fixtures.insertTrainingPath(path, institution);
    fixtures.insertStudyPlan(plan, institution, path);
    jdbcTemplate.update(
        "UPDATE study_plans SET status = 'ACTIVE', effective_from = DATE '2026-01-01' WHERE study_plan_id = ?",
        plan);
    fixtures.insertAcademicSpace(space, institution, "Armonía", "GRUPAL");
    fixtures.insertStudyPlanSpace(placement, institution, plan, space);
    fixtures.insertAcademicYear(year, institution, 2026);
    for (final UUID teacher : List.of(firstTeacher, secondTeacher)) {
      fixtures.insertPerson(teacher, institution, fixtures.randomDocumentNumber(), false);
      transaction.executeWithoutResult(
          state ->
              assignRole.execute(
                  people.findById(teacher).orElseThrow(), SystemRoleCode.TEACHER, false));
    }
    final var created =
        createCourse.execute(
            institution,
            new CreateCourseRequest(placement, null, null, null, year, classes(firstTeacher, 10)));
    final UUID course = created.id(), originalClass = created.classes().getFirst().id();
    final Number initialRevision = revisions(Course.class, course).getFirst();
    final var originalTeacher =
        atRevision(CourseClassTeacher.class, initialRevision, "courseClass", originalClass)
            .getFirst();
    final var originalDay =
        atRevision(CourseClassDay.class, initialRevision, "courseClass", originalClass).getFirst();
    final var originalSchedule = classSchedules(originalClass, initialRevision).getFirst();
    assertThat(courseAuditRows(initialRevision)).isEqualTo(5);
    assertThat(atRevision(CourseClassTeacher.class, initialRevision, "courseClass", originalClass))
        .extracting(value -> value.getPerson().getId())
        .containsExactly(firstTeacher);
    assertThat(classSchedules(originalClass, initialRevision))
        .extracting(value -> value.getStartTime())
        .containsExactly(LocalTime.of(10, 0));
    final var changed =
        replaceClasses.execute(
            institution, course, new ReplaceCourseClassesRequest(classes(secondTeacher, 11)));
    final UUID changedClass = changed.classes().getFirst().id();
    final Number changedRevision = revisions(CourseClass.class, changedClass).getFirst();
    assertThat(courseAuditRows(changedRevision)).isEqualTo(8);
    assertThat(atRevision(CourseClassTeacher.class, changedRevision, "courseClass", changedClass))
        .extracting(value -> value.getPerson().getId())
        .containsExactly(secondTeacher);
    assertThat(classSchedules(changedClass, changedRevision))
        .extracting(value -> value.getStartTime())
        .containsExactly(LocalTime.of(11, 0));
    final var changesHistory = history(course, changedRevision);
    assertThat(changesHistory.changes()).hasSize(8);
    assertThat(changesHistory.changes())
        .filteredOn(change -> change.operation().equals("DELETED"))
        .hasSize(4);
    assertThat(changesHistory.changes())
        .filteredOn(
            change ->
                change.entity().equals("Horario de clase") && change.operation().equals("DELETED"))
        .singleElement()
        .satisfies(
            change ->
                assertThat(change.fields())
                    .filteredOn(field -> field.field().equals("start_time"))
                    .singleElement()
                    .satisfies(
                        field -> {
                          assertThat(field.before()).isEqualTo("10:00:00");
                          assertThat(field.after()).isNull();
                        }));
    assertThat(changesHistory.changes())
        .filteredOn(
            change ->
                change.entity().equals("Horario de clase") && change.operation().equals("CREATED"))
        .singleElement()
        .satisfies(
            change ->
                assertThat(change.fields())
                    .filteredOn(field -> field.field().equals("start_time"))
                    .singleElement()
                    .satisfies(
                        field -> {
                          assertThat(field.before()).isNull();
                          assertThat(field.after()).isEqualTo("11:00:00");
                        }));
    assertThat(revisions(Course.class, course)).hasSize(1);
    assertThat(changed.classes()).hasSize(1);
    assertThat(atRevision(CourseClass.class, changedRevision, "course", course))
        .extracting(value -> value.getId())
        .containsExactly(changedClass);
    assertThat(atRevision(CourseClassTeacher.class, changedRevision, "courseClass", originalClass))
        .isEmpty();
    assertThat(atRevision(CourseClassDay.class, changedRevision, "courseClass", originalClass))
        .isEmpty();
    assertThat(atRevision(CourseClassSchedule.class, changedRevision, "day", originalDay.getId()))
        .isEmpty();
    assertThat(deletedSnapshot(CourseClass.class, originalClass, changedRevision).getClassNumber())
        .isEqualTo(1);
    assertThat(
            deletedSnapshot(CourseClassTeacher.class, originalTeacher.getId(), changedRevision)
                .getPerson()
                .getId())
        .isEqualTo(firstTeacher);
    final var deletedDay =
        deletedSnapshot(CourseClassDay.class, originalDay.getId(), changedRevision);
    assertThat(deletedDay.getDayOfWeek()).isEqualTo(CourseDay.MONDAY);
    assertThat(deletedDay.getCapacity()).isEqualTo(20);
    final var deletedSchedule =
        deletedSnapshot(CourseClassSchedule.class, originalSchedule.getId(), changedRevision);
    assertThat(deletedSchedule.getStartTime()).isEqualTo(LocalTime.of(10, 0));
    assertThat(deletedSchedule.getEndTime()).isEqualTo(LocalTime.of(11, 0));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM envers_poc_course_classes_aud WHERE course_class_id = ? AND revtype = 2",
                Long.class,
                originalClass))
        .isEqualTo(1);

    final Long revisionCount =
        jdbcTemplate.queryForObject("SELECT count(*) FROM envers_poc_revisions", Long.class);
    transaction.executeWithoutResult(
        state -> {
          replaceClasses.execute(
              institution, course, new ReplaceCourseClassesRequest(classes(firstTeacher, 12)));
          em.flush();
          state.setRollbackOnly();
        });
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM envers_poc_revisions", Long.class))
        .isEqualTo(revisionCount);
    assertThat(revisions(CourseClass.class, changedClass)).containsExactly(changedRevision);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT course_class_id FROM course_classes WHERE course_id = ?",
                UUID.class,
                course))
        .containsExactly(changedClass);
    assertThat(classSchedules(changedClass, changedRevision))
        .extracting(value -> value.getStartTime())
        .containsExactly(LocalTime.of(11, 0));
    assertThat(globalActions(course))
        .containsExactlyInAnyOrder("COURSE_CREATED", "COURSE_CLASSES_CHANGED");

    final var repeated =
        replaceClasses.execute(
            institution, course, new ReplaceCourseClassesRequest(classes(secondTeacher, 11)));
    final UUID currentClass = repeated.classes().getFirst().id();
    final Number repeatedRevision = revisions(CourseClass.class, currentClass).getFirst();
    assertThat(repeatedRevision.longValue()).isGreaterThan(changedRevision.longValue());
    assertThat(atRevision(CourseClass.class, repeatedRevision, "course", course))
        .extracting(value -> value.getId())
        .containsExactly(currentClass);
    assertThat(courseAuditRows(repeatedRevision)).isEqualTo(8);
    // Older snapshots must still reconstruct the tree that existed at each revision.
    assertThat(atRevision(CourseClass.class, initialRevision, "course", course))
        .extracting(value -> value.getId())
        .containsExactly(originalClass);
    assertThat(atRevision(CourseClass.class, changedRevision, "course", course))
        .extracting(value -> value.getId())
        .containsExactly(changedClass);
    assertThat(classSchedules(originalClass, initialRevision))
        .extracting(value -> value.getStartTime())
        .containsExactly(LocalTime.of(10, 0));
    assertThat(globalActions(course))
        .containsExactlyInAnyOrder("COURSE_CREATED", "COURSE_CLASSES_CHANGED");
    courseStatus.execute(institution, course, new CourseStatusRequest(CourseStatus.INACTIVE));
    assertThat(snapshot(Course.class, course, initialRevision).getStatus())
        .isEqualTo(CourseStatus.ACTIVE);
    assertThat(
            snapshot(Course.class, course, revisions(Course.class, course).getLast()).getStatus())
        .isEqualTo(CourseStatus.INACTIVE);

    assertThat(globalActions(course))
        .containsExactlyInAnyOrder(
            "COURSE_CREATED", "COURSE_CLASSES_CHANGED", "COURSE_STATUS_CHANGED");
  }

  @Test
  void correlatesTransactionsWithoutMixingRequestsNestedCommitsOrRolledBackEvents()
      throws Exception {
    authenticatePlatformAdministrator();
    final UUID institution = UUID.randomUUID();
    fixtures.insertTestInstitution(institution);
    MDC.put(RequestLoggingFilter.MDC_REQUEST_ID_KEY, "reused-request");
    final UUID first =
        createShift.execute(institution, new CreateShiftRequest("Primero", null)).id();
    final UUID second =
        createShift.execute(institution, new CreateShiftRequest("Segundo", null)).id();
    final var independent =
        new TransactionTemplate(requireNonNull(transaction.getTransactionManager()));
    independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transaction.executeWithoutResult(
        state -> {
          final var outerId = EnversAuditTransaction.currentId();
          updateShift.execute(institution, first, new UpdateShiftRequest("No se conserva", null));
          em.flush();
          independent.executeWithoutResult(
              inner -> {
                assertThat(EnversAuditTransaction.currentId()).isNotEqualTo(outerId);
                updateShift.execute(
                    institution, second, new UpdateShiftRequest("Cambio confirmado", null));
              });
          assertThat(EnversAuditTransaction.currentId()).isEqualTo(outerId);
          state.setRollbackOnly();
        });
    assertThat(globalActions(first)).containsExactly("SHIFT_CREATED");
    final var confirmed = lastDetail(second, "SHIFT_UPDATED");
    assertThat(confirmed.history()).isNotNull();
    final var confirmedHistory = requireNonNull(confirmed.history());
    mvc.perform(
            get("/api/v1/admin/audit-events/{id}", confirmed.id())
                .with(
                    authentication(
                        requireNonNull(SecurityContextHolder.getContext().getAuthentication()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.history.revision").value(confirmedHistory.revision()))
        .andExpect(jsonPath("$.history.changes.length()").value(1))
        .andExpect(jsonPath("$.history.changes[0].fields[0].before").value("Segundo"))
        .andExpect(jsonPath("$.history.changes[0].fields[0].after").value("Cambio confirmado"));
    authenticatePlatformAdministrator();

    assertThat(confirmedHistory.changes())
        .singleElement()
        .satisfies(
            change -> {
              assertThat(change.entityId()).isEqualTo(second.toString());
              assertThat(change.fields())
                  .singleElement()
                  .satisfies(
                      field -> {
                        assertThat(field.before()).isEqualTo("Segundo");
                        assertThat(field.after()).isEqualTo("Cambio confirmado");
                      });
            });
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM envers_poc_revisions r JOIN audit_events e USING(transaction_id) WHERE e.audit_event_id = ?",
                Long.class,
                confirmed.id()))
        .isEqualTo(1);
    transaction.executeWithoutResult(
        state -> {
          updateShift.execute(institution, first, new UpdateShiftRequest("Uno", null));
          updateShift.execute(institution, second, new UpdateShiftRequest("Dos", null));
        });
    final var firstHistory = requireNonNull(lastDetail(first, "SHIFT_UPDATED").history());
    final var secondHistory = requireNonNull(lastDetail(second, "SHIFT_UPDATED").history());
    assertThat(firstHistory.revision()).isEqualTo(secondHistory.revision());
    assertThat(firstHistory.changes())
        .extracting(change -> change.entityId())
        .containsExactly(first.toString());
    assertThat(secondHistory.changes())
        .extracting(change -> change.entityId())
        .containsExactly(second.toString());
    assertThat(EnversAuditTransaction.currentId()).isNull();
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

  private AuditHistoryResponse history(final UUID id, final Number revision) {
    final UUID event =
        requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT e.audit_event_id FROM audit_events e JOIN envers_poc_revisions r USING(transaction_id) WHERE e.entity_id = ? AND r.id = ? ORDER BY e.audit_event_id LIMIT 1",
                UUID.class,
                id,
                revision));
    return requireNonNull(audit.get(event).history());
  }

  private List<CourseClassRequest> classes(final UUID teacher, final int hour) {
    return List.of(
        new CourseClassRequest(
            List.of(teacher),
            List.of(
                new CourseClassDayRequest(
                    CourseDay.MONDAY,
                    20,
                    null,
                    List.of(
                        new CourseClassScheduleRequest(
                            LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0)))))));
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
              final List<?> values =
                  AuditReaderFactory.get(em)
                      .createQuery()
                      .forEntitiesAtRevision(type, revision)
                      .add(AuditEntity.relatedId(relation).eq(id))
                      .getResultList();
              return values.stream().map(value -> type.cast(value)).toList();
            }));
  }

  private List<CourseClassSchedule> classSchedules(final UUID classId, final Number revision) {
    return requireNonNull(
        transaction.execute(
            state ->
                atRevision(CourseClassDay.class, revision, "courseClass", classId).stream()
                    .flatMap(
                        day ->
                            atRevision(CourseClassSchedule.class, revision, "day", day.getId())
                                .stream())
                    .toList()));
  }

  private long courseAuditRows(final Number revision) {
    return requireNonNull(
        jdbcTemplate.queryForObject(
            """
        SELECT sum(row_count) FROM (
          SELECT count(*) AS row_count FROM envers_poc_courses_aud WHERE rev = ?
          UNION ALL SELECT count(*) FROM envers_poc_course_classes_aud WHERE rev = ?
          UNION ALL SELECT count(*) FROM envers_poc_course_class_teachers_aud WHERE rev = ?
          UNION ALL SELECT count(*) FROM envers_poc_course_class_days_aud WHERE rev = ?
          UNION ALL SELECT count(*) FROM envers_poc_course_class_schedules_aud WHERE rev = ?
        ) history
        """,
            Long.class,
            revision,
            revision,
            revision,
            revision,
            revision));
  }

  private List<String> globalActions(final UUID id) {
    return jdbcTemplate
        .queryForList("SELECT action FROM audit_events WHERE entity_id = ?", String.class, id)
        .stream()
        .map(value -> requireNonNull(value))
        .toList();
  }
}
