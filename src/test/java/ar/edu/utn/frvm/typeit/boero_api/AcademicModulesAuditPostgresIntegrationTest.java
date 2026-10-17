package ar.edu.utn.frvm.typeit.boero_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.ApprovalMode;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseDay;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.RequiredCondition;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.RequirementStage;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.RequirementType;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.StudyPlanStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.InvalidAcademicStateException;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.AcademicYearStatusRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CourseClassDayRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CourseClassRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CourseClassScheduleRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CourseStatusRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CreateAcademicLevelRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CreateCourseRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CreatePrerequisiteRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CreateStudyPlanRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CreateStudyPlanSpaceRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CreateStudyPlanVersionRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.ReplaceCourseClassesRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.StudyPlanStatusRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.UpdateAcademicLevelRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.UpdatePrerequisiteRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.UpdateStudyPlanRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.UpdateStudyPlanSpaceRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.AcademicLifecycleService;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.CreateAcademicLevelUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.CreateCourseUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.CreatePrerequisiteUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.CreateStudyPlanSpaceUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.CreateStudyPlanUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.CreateStudyPlanVersionUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.DeleteAcademicLevelUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.DeletePrerequisiteUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.DeleteStudyPlanSpaceUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.ReplaceCourseClassesUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.UpdateAcademicLevelUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.UpdateAcademicYearStatusUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.UpdateCourseStatusUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.UpdatePrerequisiteUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.UpdateStudyPlanSpaceUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.UpdateStudyPlanStatusUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.UpdateStudyPlanUseCase;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AssignPersonSystemRoleUseCase;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionRoleProvisioner;
import ar.edu.utn.frvm.typeit.boero_api.common.mail.MailSender;
import ar.edu.utn.frvm.typeit.boero_api.common.storage.StorageService;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.boero_api.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@IntegrationTest
@AutoConfigureMockMvc
class AcademicModulesAuditPostgresIntegrationTest extends DatabaseMigrationTestSupport {
  @Autowired CreateStudyPlanUseCase createPlan;
  @Autowired UpdateStudyPlanUseCase updatePlan;
  @Autowired UpdateStudyPlanStatusUseCase planStatus;
  @Autowired CreateStudyPlanVersionUseCase createVersion;
  @Autowired CreateAcademicLevelUseCase createLevel;
  @Autowired UpdateAcademicLevelUseCase updateLevel;
  @Autowired DeleteAcademicLevelUseCase deleteLevel;
  @Autowired CreateStudyPlanSpaceUseCase createSpace;
  @Autowired UpdateStudyPlanSpaceUseCase updateSpace;
  @Autowired DeleteStudyPlanSpaceUseCase deleteSpace;
  @Autowired CreatePrerequisiteUseCase createPrerequisite;
  @Autowired UpdatePrerequisiteUseCase updatePrerequisite;
  @Autowired DeletePrerequisiteUseCase deletePrerequisite;
  @Autowired CreateCourseUseCase createCourse;
  @Autowired ReplaceCourseClassesUseCase replaceClasses;
  @Autowired UpdateCourseStatusUseCase courseStatus;
  @Autowired UpdateAcademicYearStatusUseCase yearStatus;
  @Autowired AcademicLifecycleService lifecycle;
  @Autowired InstitutionRoleProvisioner provisionRoles;
  @Autowired AssignPersonSystemRoleUseCase assignRole;
  @Autowired InstitutionRepository institutions;
  @Autowired PersonRepository people;
  @Autowired TransactionTemplate transaction;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockitoBean MailSender mailSender;
  @MockitoSpyBean StorageService storageService;

  @Test
  void auditsAcademicModulesAndRelatedTeachersWithoutDuplicatingLifecycleEvents() throws Exception {
    authenticatePlatformAdministrator();
    final var admin = requireNonNull(SecurityContextHolder.getContext().getAuthentication());
    final UUID institution = UUID.randomUUID(), path = UUID.randomUUID(), space = UUID.randomUUID();
    final UUID year = UUID.randomUUID();
    final UUID firstTeacher = UUID.randomUUID(), secondTeacher = UUID.randomUUID();
    fixtures.insertTestInstitution(institution);
    transaction.executeWithoutResult(
        status -> provisionRoles.provision(institutions.findById(institution).orElseThrow()));
    fixtures.insertTrainingPath(path, institution, "Trayecto auditoría académica");
    fixtures.insertAcademicSpace(space, institution, "Armonía auditada", "GRUPAL");
    fixtures.insertAcademicYear(year, institution, 2098);
    final var createdPlan =
        createPlan.execute(
            institution,
            path,
            new CreateStudyPlanRequest("Plan auditado", LocalDate.of(2020, 1, 1), null));
    final UUID plan = createdPlan.id();
    final UUID placement = verifyCurriculumAudit(institution, plan, space);
    final var planChange =
        new UpdateStudyPlanRequest("Plan auditado actualizado", LocalDate.of(2020, 1, 1), null);
    updatePlan.execute(institution, plan, planChange);
    updatePlan.execute(institution, plan, planChange);
    final UUID level =
        createLevel.execute(institution, plan, new CreateAcademicLevelRequest(1, null)).id();
    final var levelChange = new UpdateAcademicLevelRequest(2, "Descripción privada del nivel");
    updateLevel.execute(institution, level, levelChange);
    updateLevel.execute(institution, level, levelChange);
    updateLevel.execute(
        institution, level, new UpdateAcademicLevelRequest(2, "Otra descripción privada"));
    deleteLevel.execute(institution, level);
    assertThat(count(level, "STUDY_PLAN_LEVEL_CREATED")).isEqualTo(1);
    assertThat(count(level, "STUDY_PLAN_LEVEL_UPDATED")).isEqualTo(2);
    assertThat(count(level, "STUDY_PLAN_LEVEL_DELETED")).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT details::text FROM audit_events WHERE entity_id = ?", String.class, level))
        .allSatisfy(details -> assertThat(details).doesNotContain("privada"));
    planStatus.execute(institution, plan, new StudyPlanStatusRequest(StudyPlanStatus.ACTIVE, null));
    assertThatThrownBy(
            () ->
                planStatus.execute(
                    institution, plan, new StudyPlanStatusRequest(StudyPlanStatus.ACTIVE, null)))
        .isInstanceOf(InvalidAcademicStateException.class);
    assertThat(count(plan, "STUDY_PLAN_CREATED")).isEqualTo(1);
    assertThat(count(plan, "STUDY_PLAN_UPDATED")).isEqualTo(1);
    assertThat(count(plan, "STUDY_PLAN_STATUS_CHANGED")).isEqualTo(1);

    for (final var teacher : List.of(firstTeacher, secondTeacher)) {
      fixtures.insertPerson(teacher, institution, fixtures.randomDocumentNumber(), false);
      transaction.executeWithoutResult(
          status ->
              assignRole.execute(
                  people.findById(teacher).orElseThrow(), SystemRoleCode.TEACHER, false));
    }
    final var originalClasses = classes(firstTeacher, CourseDay.MONDAY);
    final var createdCourse =
        createCourse.execute(
            institution,
            new CreateCourseRequest(placement, null, null, null, year, originalClasses));
    final UUID course = createdCourse.id();
    replaceClasses.execute(institution, course, new ReplaceCourseClassesRequest(originalClasses));
    assertThat(count(course, "COURSE_CLASSES_CHANGED")).isZero();
    replaceClasses.execute(
        institution,
        course,
        new ReplaceCourseClassesRequest(classes(secondTeacher, CourseDay.TUESDAY)));
    assertThat(count(course, "COURSE_CREATED")).isEqualTo(1);
    assertThat(count(course, "COURSE_CLASSES_CHANGED")).isEqualTo(1);
    courseStatus.execute(institution, course, new CourseStatusRequest(CourseStatus.INACTIVE));
    courseStatus.execute(institution, course, new CourseStatusRequest(CourseStatus.INACTIVE));
    assertThat(count(course, "COURSE_STATUS_CHANGED")).isEqualTo(1);

    lifecycle.deleteCourse(institution, course, null);
    lifecycle.deleteCourse(institution, course, null);
    lifecycle.restoreCourse(institution, course, null);
    lifecycle.restoreCourse(institution, course, null);
    assertThat(count(course, "COURSE_DELETED")).isEqualTo(1);
    assertThat(count(course, "COURSE_RESTORED")).isEqualTo(1);
    final UUID version =
        createVersion
            .execute(
                institution,
                plan,
                new CreateStudyPlanVersionRequest(
                    "Plan auditado actualizado", LocalDate.of(2027, 1, 1), null))
            .id();
    assertThat(count(version, "STUDY_PLAN_VERSION_CREATED")).isEqualTo(1);
    assertThat(count(version, "STUDY_PLAN_CREATED")).isZero();
    lifecycle.deleteStudyPlan(institution, version, null);
    lifecycle.restoreStudyPlan(institution, version, null);
    assertThat(count(version, "STUDY_PLAN_DELETED")).isEqualTo(1);
    assertThat(count(version, "STUDY_PLAN_RESTORED")).isEqualTo(1);

    yearStatus.execute(institution, year, new AcademicYearStatusRequest(AcademicYearStatus.CLOSED));
    yearStatus.execute(institution, year, new AcademicYearStatusRequest(AcademicYearStatus.CLOSED));
    assertThat(count(course, "COURSE_STATUS_CHANGED")).isEqualTo(2);

    final UUID rolledBack =
        requireNonNull(
            transaction.execute(
                status -> {
                  final UUID id =
                      createPlan
                          .execute(
                              institution,
                              path,
                              new CreateStudyPlanRequest("Plan que revierte", null, null))
                          .id();
                  status.setRollbackOnly();
                  return id;
                }));
    assertThat(count(rolledBack, "STUDY_PLAN_CREATED")).isZero();
    final var related =
        json.readTree(
            mvc.perform(
                    get("/api/v1/admin/audit-events")
                        .with(authentication(admin))
                        .param("institutionId", institution.toString())
                        .param("module", "COURSES")
                        .param("personId", firstTeacher.toString())
                        .param("search", "armonia")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(related.get("totalItems").asInt()).isEqualTo(2);
    assertThat(related.at("/items/0/actorKind").asString()).isEqualTo("PLATFORM");
    assertThat(related.at("/items/0/subjectPersonId").isNull()).isTrue();
    final var option =
        json.readTree(
            mvc.perform(
                    get("/api/v1/admin/audit-events/people")
                        .with(authentication(admin))
                        .param("personId", firstTeacher.toString())
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(option.at("/items/0/personId").asString()).isEqualTo(firstTeacher.toString());
  }

  private List<CourseClassRequest> classes(final UUID teacher, final CourseDay day) {
    return List.of(
        new CourseClassRequest(
            List.of(teacher),
            List.of(
                new CourseClassDayRequest(
                    day,
                    20,
                    null,
                    List.of(
                        new CourseClassScheduleRequest(
                            LocalTime.of(18, 0), LocalTime.of(19, 0)))))));
  }

  private UUID verifyCurriculumAudit(final UUID institution, final UUID plan, final UUID space) {
    final UUID placement =
        createSpace
            .execute(
                institution,
                plan,
                new CreateStudyPlanSpaceRequest(
                    space, null, RequirementType.OPTIONAL, 1, ApprovalMode.FINAL_EXAM, List.of()))
            .id();
    final var spaceChange =
        new UpdateStudyPlanSpaceRequest(
            space, null, RequirementType.REQUIRED, 1, ApprovalMode.FINAL_EXAM, List.of());
    updateSpace.execute(institution, placement, spaceChange);
    updateSpace.execute(institution, placement, spaceChange);
    assertThat(count(placement, "STUDY_PLAN_SPACE_CREATED")).isEqualTo(1);
    assertThat(count(placement, "STUDY_PLAN_SPACE_UPDATED")).isEqualTo(1);

    final UUID requiredSpace = UUID.randomUUID();
    fixtures.insertAcademicSpace(requiredSpace, institution, "Lectura auditada", "GRUPAL");
    final UUID requiredPlacement =
        createSpace
            .execute(
                institution,
                plan,
                new CreateStudyPlanSpaceRequest(
                    requiredSpace,
                    null,
                    RequirementType.REQUIRED,
                    2,
                    ApprovalMode.FINAL_EXAM,
                    List.of()))
            .id();
    final UUID prerequisite =
        createPrerequisite
            .execute(
                institution,
                placement,
                new CreatePrerequisiteRequest(
                    requiredPlacement, RequirementStage.TO_ENROLL, RequiredCondition.REGULAR))
            .id();
    final var prerequisiteChange =
        new UpdatePrerequisiteRequest(
            requiredPlacement, RequirementStage.TO_ENROLL, RequiredCondition.PASSED);
    updatePrerequisite.execute(institution, prerequisite, prerequisiteChange);
    updatePrerequisite.execute(institution, prerequisite, prerequisiteChange);
    deletePrerequisite.execute(institution, prerequisite);
    deleteSpace.execute(institution, requiredPlacement);
    assertThat(count(prerequisite, "STUDY_PLAN_PREREQUISITE_CREATED")).isEqualTo(1);
    assertThat(count(prerequisite, "STUDY_PLAN_PREREQUISITE_UPDATED")).isEqualTo(1);
    assertThat(count(prerequisite, "STUDY_PLAN_PREREQUISITE_DELETED")).isEqualTo(1);
    assertThat(count(requiredPlacement, "STUDY_PLAN_SPACE_DELETED")).isEqualTo(1);
    return placement;
  }

  private long count(final UUID id, final String action) {
    return requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_events WHERE entity_id = ? AND action = ?",
            Long.class,
            id,
            action));
  }
}
