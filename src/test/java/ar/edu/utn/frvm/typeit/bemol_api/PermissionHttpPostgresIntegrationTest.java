package ar.edu.utn.frvm.typeit.bemol_api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Instrument;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.UserSession;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.InstitutionalAccessTokenInput;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplicationCourse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplicationSpace;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPeriod;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPeriodOffering;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentPeriodStatus;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class PermissionHttpPostgresIntegrationTest extends DatabaseMigrationTestSupport {
  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactions;
  @Autowired private MockMvc http;
  @Autowired private JwtService jwt;

  @ParameterizedTest(name = "Application detail with course-read scope {0}")
  @CsvSource({
    "NONE,false",
    "INSTITUTION,true",
    "ALLOWED_PATH,true",
    "OTHER_PATH,false",
    "OWNER,true",
    "APPROVER_ONLY,false"
  })
  void applicationDetailMustRespectTheSeparateCourseReadPermission(
      String access, boolean canReadCourses) throws Exception {
    final UUID institution = UUID.randomUUID();
    final UUID reviewer = UUID.randomUUID();
    final UUID applicant = UUID.randomUUID();
    final UUID path = UUID.randomUUID();
    final UUID plan = UUID.randomUUID();
    final UUID year = UUID.randomUUID();
    final UUID space = UUID.randomUUID();
    final UUID course = UUID.randomUUID();
    fixtures.insertTestInstitution(institution);
    fixtures.insertPerson(reviewer, institution, "99191001", false);
    fixtures.insertPerson(applicant, institution, "99191002", false);
    fixtures.insertTrainingPath(path, institution);
    fixtures.insertAcademicYear(year, institution, 2098);
    fixtures.insertStudyPlan(plan, institution, path);
    jdbcTemplate.update(
        "UPDATE study_plans SET status = 'ACTIVE', effective_from = DATE '2026-01-01' WHERE"
            + " study_plan_id = ?",
        plan);
    fixtures.insertAcademicSpace(space, institution);
    fixtures.insertCourse(course, institution, plan, space, year);
    final UUID legacySpace = UUID.randomUUID(),
        legacyPlacement = UUID.randomUUID(),
        instrument = UUID.randomUUID();
    fixtures.insertAcademicSpace(legacySpace, institution);
    jdbcTemplate.update(
        "UPDATE academic_spaces SET instrumental = true WHERE academic_space_id = ?", legacySpace);
    jdbcTemplate.update(
        "INSERT INTO study_plan_spaces (study_plan_space_id, institution_id, study_plan_id,"
            + " academic_space_id, requirement_type, display_order, approval_mode, created_at,"
            + " updated_at) VALUES (?, ?, ?, ?, 'REQUIRED', 2, 'PROMOTION', CURRENT_TIMESTAMP,"
            + " CURRENT_TIMESTAMP)",
        legacyPlacement,
        institution,
        plan,
        legacySpace);
    fixtures.insertInstrument(instrument, institution, "Instrumento histórico");
    grant(reviewer, institution, PermissionCode.ENROLLMENT_APPLICATION_READ);
    if (access.equals("APPROVER_ONLY")) {
      grant(reviewer, institution, PermissionCode.ENROLLMENT_APPLICATION_APPROVE);
    } else if (!access.equals("NONE") && !access.equals("OWNER")) {
      final UUID role =
          grant(reviewer, institution, PermissionCode.ENROLLMENT_APPLICATION_COURSE_READ);
      if (!access.equals("INSTITUTION")) {
        final UUID selectedPath = access.equals("ALLOWED_PATH") ? path : UUID.randomUUID();
        if (access.equals("OTHER_PATH")) {
          fixtures.insertTrainingPath(selectedPath, institution);
        }
        new TransactionTemplate(transactions)
            .executeWithoutResult(
                tx -> {
                  jdbcTemplate.update(
                      "UPDATE person_role_assignments SET access_scope = 'TRAINING_PATHS' WHERE"
                          + " role_id = ?",
                      role);
                  jdbcTemplate.update(
                      "INSERT INTO person_role_assignment_training_paths"
                          + " (person_role_assignment_id, training_path_id) SELECT"
                          + " person_role_assignment_id, ? FROM person_role_assignments WHERE"
                          + " role_id = ?",
                      selectedPath,
                      role);
                });
      }
    }
    final UUID application =
        new TransactionTemplate(transactions)
            .execute(
                tx -> {
                  final var tenant = entityManager.find(Institution.class, institution);
                  final var period =
                      EnrollmentPeriod.builder()
                          .institution(tenant)
                          .academicYear(entityManager.find(AcademicYear.class, year))
                          .name("Permission audit")
                          .startDate(Instant.now().minusSeconds(3600))
                          .endDate(Instant.now().plusSeconds(3600))
                          .status(EnrollmentPeriodStatus.OPEN)
                          .build();
                  entityManager.persist(period);
                  entityManager.flush();
                  period.markScopeConfigured();
                  final var offering =
                      EnrollmentPeriodOffering.create(
                          period, entityManager.find(StudyPlan.class, plan));
                  offering.selectLevels(List.of(), true);
                  period.getOfferings().add(offering);
                  entityManager.persist(offering);
                  entityManager.flush();
                  final var request =
                      EnrollmentApplication.builder()
                          .institution(tenant)
                          .applicantPerson(entityManager.find(Person.class, applicant))
                          .trainingPathId(path)
                          .studyPlan(entityManager.find(StudyPlan.class, plan))
                          .academicYear(entityManager.find(AcademicYear.class, year))
                          .enrollmentPeriod(period)
                          .status(EnrollmentApplicationStatus.DRAFT)
                          .build();
                  entityManager.persist(request);
                  final var selection =
                      EnrollmentApplicationCourse.create(
                          tenant, request, entityManager.find(Course.class, course), null);
                  request.getCourseSelections().add(selection);
                  entityManager.persist(selection);
                  final var legacy =
                      EnrollmentApplicationSpace.builder()
                          .enrollmentApplication(request)
                          .studyPlanSpace(entityManager.find(StudyPlanSpace.class, legacyPlacement))
                          .instrument(entityManager.find(Instrument.class, instrument))
                          .build();
                  request.getSelectedSpaces().add(legacy);
                  entityManager.persist(legacy);
                  return request.getId();
                });
    if (access.equals("OWNER")) {
      grant(applicant, institution, PermissionCode.OWN_ENROLLMENT_APPLICATION_READ);
    }
    final UUID subject = access.equals("OWNER") ? applicant : reviewer;
    if (access.equals("APPROVER_ONLY")) {
      jdbcTemplate.update(
          "UPDATE enrollment_applications SET status = 'SUBMITTED' WHERE enrollment_application_id"
              + " = ?",
          application);
    }
    final String token =
        token(subject, institution, access.equals("OWNER") ? "99191002" : "99191001");
    final String endpoint =
        access.equals("OWNER")
            ? "/api/v1/institutions/"
                + institution
                + "/self-service/"
                + applicant
                + "/enrollment-applications/"
                + application
            : "/api/v1/institutions/" + institution + "/enrollment-applications/" + application;

    http.perform(get(endpoint).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canConfirm").value(access.equals("APPROVER_ONLY")))
        .andExpect(jsonPath("$.courses.length()").value(canReadCourses ? 1 : 0))
        .andExpect(jsonPath("$.data.courses.length()").value(canReadCourses ? 1 : 0))
        .andExpect(jsonPath("$.spaces.length()").value(canReadCourses ? 1 : 0))
        .andExpect(
            jsonPath("$.data.academicSpaceSelection.studyPlanSpaceIds.length()")
                .value(canReadCourses ? 1 : 0))
        .andExpect(
            jsonPath("$.data.instrumentSelection.studyPlanSpaceInstrumentIds.length()")
                .value(canReadCourses ? 1 : 0));
    if (!access.equals("OWNER")) {
      http.perform(
              get("/api/v1/enrollment-applications/{id}", application)
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.canConfirm").value(access.equals("APPROVER_ONLY")))
          .andExpect(jsonPath("$.courses.length()").value(canReadCourses ? 1 : 0))
          .andExpect(jsonPath("$.data.courses.length()").value(canReadCourses ? 1 : 0))
          .andExpect(jsonPath("$.spaces.length()").value(canReadCourses ? 1 : 0))
          .andExpect(
              jsonPath("$.data.academicSpaceSelection.studyPlanSpaceIds.length()")
                  .value(canReadCourses ? 1 : 0))
          .andExpect(
              jsonPath("$.data.instrumentSelection.studyPlanSpaceInstrumentIds.length()")
                  .value(canReadCourses ? 1 : 0));
    }
    if (access.equals("APPROVER_ONLY")) {
      http.perform(
              post(
                      "/api/v1/institutions/{institution}/enrollment-applications/{application}/approve",
                      institution,
                      application)
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("APPROVED"))
          .andExpect(jsonPath("$.canReadAttachments").value(false))
          .andExpect(jsonPath("$.courses.length()").value(0))
          .andExpect(jsonPath("$.data.courses.length()").value(0));
    }
  }

  @Test
  void roleOptionsRemainTenantBoundAndDoNotRequireRoleAdministration() throws Exception {
    final UUID institution = UUID.randomUUID(),
        foreignInstitution = UUID.randomUUID(),
        person = UUID.randomUUID();
    fixtures.insertTestInstitution(institution);
    fixtures.insertTestInstitution(foreignInstitution);
    fixtures.insertPerson(person, institution, "99191003", false);
    final UUID role = grant(person, institution, PermissionCode.INSTITUTION_PERSON_READ_ANY);
    final UUID foreignPerson = UUID.randomUUID();
    fixtures.insertPerson(foreignPerson, foreignInstitution, "99191004", false);
    final UUID foreignRole =
        grant(foreignPerson, foreignInstitution, PermissionCode.INSTITUTION_PERSON_READ_ANY);
    final String token = token(person, institution, "99191003");
    final String options = "/api/v1/institutions/{institution}/role-options/{role}";

    http.perform(
            get(options, institution, role).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(role.toString()));
    http.perform(
            get(options, institution, foreignRole)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound());
    http.perform(
            get(options, foreignInstitution, foreignRole)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden());
    http.perform(get(options, institution, role)).andExpect(status().isUnauthorized());
    http.perform(
            get("/api/v1/admin/institutions/{institution}/role-options", institution)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  private String token(UUID person, UUID institution, String document) {
    return new TransactionTemplate(transactions)
        .execute(
            tx -> {
              final var user =
                  User.builder()
                      .institution(entityManager.find(Institution.class, institution))
                      .person(entityManager.find(Person.class, person))
                      .password("test-password-hash")
                      .build();
              entityManager.persist(user);
              final var session =
                  UserSession.builder()
                      .userId(user.getId())
                      .user(user)
                      .ipAddress("127.0.0.1")
                      .userAgent("Permission integration test")
                      .build();
              entityManager.persist(session);
              return jwt.generateAccessToken(
                  new InstitutionalAccessTokenInput(
                      user.getId(), person, institution, document, session.getId()));
            });
  }

  private UUID grant(UUID person, UUID institution, PermissionCode permission) {
    final UUID role = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO roles (role_id, code, name, scope, is_system, institution_id, created_at,"
            + " updated_at) VALUES (?, ?, ?, 'INSTITUTION', false, ?, CURRENT_TIMESTAMP,"
            + " CURRENT_TIMESTAMP)",
        role,
        "HTTP_" + role,
        "HTTP " + role,
        institution);
    jdbcTemplate.update(
        "INSERT INTO role_permissions (role_id, permission_id) SELECT ?, permission_id FROM"
            + " permissions WHERE code = ?",
        role,
        permission.getCode());
    jdbcTemplate.update(
        "INSERT INTO person_role_assignments (person_role_assignment_id, person_id, role_id,"
            + " institution_id, created_at, updated_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP,"
            + " CURRENT_TIMESTAMP)",
        UUID.randomUUID(),
        person,
        role,
        institution);
    return role;
  }
}
