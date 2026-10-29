package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClass;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListAcademicSelectionOptionsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.AccessScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.PermissionDelegationNotAllowedException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.RoleRevocationNotAllowedException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.ScopedResourceNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.AssignRoleRequest;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.InstitutionRoleRequest;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.ReplacePersonRolesRequest;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorityResolver;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorizationCacheInvalidator;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.CachedAuthoritySnapshotResolver;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionRoleManagementService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ReplacePersonRolesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseEnrollmentSource;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.CourseEnrollmentService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentAttachmentService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.GetEnrollmentApplicationUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.ListEnrollmentApplicationsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.ListEnrollmentPeriodLevelsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Student;
import ar.edu.utn.frvm.typeit.bemol_api.search.SearchService;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class RoleScopePostgresIntegrationTest extends DatabaseMigrationTestSupport {
  @Autowired TrainingPathRepository paths;
  @Autowired CachedAuthoritySnapshotResolver snapshots;
  @Autowired AuthorityResolver authorities;
  @Autowired AcademicAccessGuard guard;
  @Autowired AuthorizationCacheInvalidator invalidator;
  @Autowired PlatformTransactionManager transactionManager;
  @Autowired ReplacePersonRolesUseCase replaceRoles;
  @Autowired IsSessionActiveUseCase activeSession;
  @Autowired SearchService search;
  @Autowired InstitutionRoleManagementService roles;
  @Autowired ListAcademicSelectionOptionsUseCase options;

  @Autowired ListEnrollmentPeriodLevelsUseCase periodLevels;
  @Autowired jakarta.persistence.EntityManager entityManager;
  @Autowired ListEnrollmentApplicationsUseCase applicationList;
  @Autowired GetEnrollmentApplicationUseCase applicationDetail;
  @Autowired EnrollmentAttachmentService attachments;
  @Autowired CourseEnrollmentService enrollments;

  @AfterEach
  void clearPrincipal() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void filtersBeforePaginationAndDeniesDirectAccessToOtherPaths() {
    UUID institution = fixtures.firstInstitutionId(),
        person = UUID.randomUUID(),
        allowed = UUID.randomUUID(),
        hidden = UUID.randomUUID();
    fixtures.insertPerson(person, institution, "99110001", false);
    fixtures.insertTrainingPath(allowed, institution);
    fixtures.insertTrainingPath(hidden, institution);
    UUID assignment = assignment(person, institution, PermissionCode.TRAINING_PATH_READ);
    transaction()
        .executeWithoutResult(
            status -> {
              jdbcTemplate.update(
                  "UPDATE person_role_assignments SET access_scope = 'TRAINING_PATHS' WHERE person_role_assignment_id = ?",
                  assignment);
              jdbcTemplate.update(
                  "INSERT INTO person_role_assignment_training_paths (person_role_assignment_id, training_path_id) VALUES (?, ?)",
                  assignment,
                  allowed);
            });
    authenticate(person, institution);
    var page = paths.findByFilters(institution, null, null, false, PageRequest.of(0, 1));
    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent()).extracting(p -> p.getId()).containsExactly(allowed);
    var searchResults =
        search.institutionalSummary(
            institution, person, "Migration", 1, Set.of(PermissionCode.TRAINING_PATH_READ));
    assertThat(searchResults.groups())
        .flatExtracting(group -> group.items())
        .extracting(result -> result.id())
        .containsExactly(allowed);
    guard.require(
        PermissionCode.TRAINING_PATH_READ, institution, ScopedResource.TRAINING_PATH, allowed);
    assertThatThrownBy(
            () ->
                guard.require(
                    PermissionCode.TRAINING_PATH_READ,
                    institution,
                    ScopedResource.TRAINING_PATH,
                    hidden))
        .isInstanceOf(ScopedResourceNotFoundException.class);
    assertThatThrownBy(
            () ->
                guard.require(
                    PermissionCode.TRAINING_PATH_UPDATE,
                    institution,
                    ScopedResource.TRAINING_PATH,
                    allowed))
        .isInstanceOf(ScopedResourceNotFoundException.class);
  }

  @Test
  void scopeOnlyChangeInvalidatesLoadedAuthoritiesAfterCommit() {
    UUID institution = fixtures.firstInstitutionId(),
        person = UUID.randomUUID(),
        allowed = UUID.randomUUID();
    fixtures.insertPerson(person, institution, "99110002", false);
    fixtures.insertTrainingPath(allowed, institution);
    UUID assignment = assignment(person, institution, PermissionCode.TRAINING_PATH_READ);
    assertThat(
            requireNonNull(
                    authorities
                        .resolvePersonAuthorities(person, institution)
                        .permissionScopes()
                        .get(PermissionCode.TRAINING_PATH_READ))
                .institutional())
        .isTrue();
    transaction()
        .executeWithoutResult(
            status -> {
              jdbcTemplate.update(
                  "UPDATE person_role_assignments SET access_scope = 'TRAINING_PATHS' WHERE person_role_assignment_id = ?",
                  assignment);
              jdbcTemplate.update(
                  "INSERT INTO person_role_assignment_training_paths (person_role_assignment_id, training_path_id) VALUES (?, ?)",
                  assignment,
                  allowed);
              invalidator.evictPerson(person, institution);
            });
    var access =
        requireNonNull(
            authorities
                .resolvePersonAuthorities(person, institution)
                .permissionScopes()
                .get(PermissionCode.TRAINING_PATH_READ));
    assertThat(requireNonNull(access).institutional()).isFalse();
    assertThat(access.trainingPathIds()).containsExactly(allowed);
  }

  @Test
  void rejectsEmptySelectionAndCrossInstitutionRelationsWithoutLosingOriginalAssignment() {
    UUID institution = fixtures.firstInstitutionId(), person = UUID.randomUUID();
    fixtures.insertPerson(person, institution, "99110003", false);
    UUID assignment = assignment(person, institution, PermissionCode.TRAINING_PATH_READ);
    assertThatThrownBy(
            () ->
                transaction()
                    .executeWithoutResult(
                        status ->
                            jdbcTemplate.update(
                                "UPDATE person_role_assignments SET access_scope = 'TRAINING_PATHS' WHERE person_role_assignment_id = ?",
                                assignment)))
        .isInstanceOf(RuntimeException.class);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT access_scope FROM person_role_assignments WHERE person_role_assignment_id = ?",
                String.class,
                assignment))
        .isEqualTo("INSTITUTION");
    UUID otherInstitution = UUID.randomUUID(), foreignPath = UUID.randomUUID();
    fixtures.insertTestInstitution(otherInstitution);
    fixtures.insertTrainingPath(foreignPath, otherInstitution);
    UUID foreignRole = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO roles (role_id, code, name, scope, is_system, institution_id, created_at, updated_at) VALUES (?, ?, 'Foreign role', 'INSTITUTION', false, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        foreignRole,
        "FOREIGN_" + foreignRole,
        otherInstitution);
    assertThatThrownBy(
            () ->
                transaction()
                    .executeWithoutResult(
                        status ->
                            jdbcTemplate.update(
                                "UPDATE person_role_assignments SET role_id = ? WHERE person_role_assignment_id = ?",
                                foreignRole,
                                assignment)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("person_role_assignments_role_institution_fk");

    assertThatThrownBy(
            () ->
                transaction()
                    .executeWithoutResult(
                        status -> {
                          jdbcTemplate.update(
                              "UPDATE person_role_assignments SET access_scope = 'TRAINING_PATHS' WHERE person_role_assignment_id = ?",
                              assignment);
                          jdbcTemplate.update(
                              "INSERT INTO person_role_assignment_training_paths (person_role_assignment_id, training_path_id) VALUES (?, ?)",
                              assignment,
                              foreignPath);
                        }))
        .isInstanceOf(RuntimeException.class);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM person_role_assignment_training_paths WHERE person_role_assignment_id = ?",
                Long.class,
                assignment))
        .isZero();
  }

  @Test
  void replacingOnlyTheScopeUsesJpaAndInvalidatesCachedAuthorities() {
    UUID institution = fixtures.firstInstitutionId(),
        actor = UUID.randomUUID(),
        person = UUID.randomUUID(),
        path = UUID.randomUUID();
    fixtures.insertPerson(actor, institution, "99110004", false);
    fixtures.insertPerson(person, institution, "99110005", false);
    fixtures.insertTrainingPath(path, institution);
    UUID actorAssignment = assignment(actor, institution, PermissionCode.TRAINING_PATH_READ);
    jdbcTemplate.update(
        "INSERT INTO role_permissions (role_id, permission_id) SELECT a.role_id, p.permission_id FROM person_role_assignments a CROSS JOIN permissions p WHERE a.person_role_assignment_id = ? AND p.code IN (?, ?)",
        actorAssignment,
        PermissionCode.INSTITUTION_ROLE_ASSIGN.getCode(),
        PermissionCode.INSTITUTION_ROLE_REVOKE.getCode());
    UUID userId = UUID.randomUUID(), sessionId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (user_id, person_id, institution_id, password, enabled, created_at, updated_at) VALUES (?, ?, ?, 'test-only', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        userId,
        person,
        institution);
    jdbcTemplate.update(
        "INSERT INTO user_sessions (user_session_id, user_id, active, remember_me, started_at) VALUES (?, ?, true, false, CURRENT_TIMESTAMP)",
        sessionId,
        userId);
    assertThat(activeSession.execute(sessionId)).isTrue();
    UUID targetAssignment = assignment(person, institution, PermissionCode.TRAINING_PATH_READ);
    UUID role =
        jdbcTemplate.queryForObject(
            "SELECT role_id FROM person_role_assignments WHERE person_role_assignment_id = ?",
            UUID.class,
            targetAssignment);
    assertThat(
            requireNonNull(
                    authorities
                        .resolvePersonAuthorities(person, institution)
                        .permissionScopes()
                        .get(PermissionCode.TRAINING_PATH_READ))
                .institutional())
        .isTrue();
    authenticate(actor, institution);
    var result =
        replaceRoles.execute(
            institution,
            person,
            new ReplacePersonRolesRequest(
                List.of(
                    new AssignRoleRequest(
                        requireNonNull(role), AccessScope.TRAINING_PATHS, Set.of(path)))),
            false,
            Set.of());
    assertThat(activeSession.execute(sessionId)).isFalse();
    assertThat(result)
        .singleElement()
        .satisfies(
            assignment -> {
              assertThat(assignment.trainingPathIds()).containsExactly(path);
              assertThat(assignment.trainingPathNames()).containsKey(path);
            });
    assertThat(
            requireNonNull(
                    authorities
                        .resolvePersonAuthorities(person, institution)
                        .permissionScopes()
                        .get(PermissionCode.TRAINING_PATH_READ))
                .trainingPathIds())
        .containsExactly(path);
  }

  @Test
  void courseCreatorCanSelectOnlyItsPlansAndMinimalSharedCatalogOptions() {
    UUID institution = fixtures.firstInstitutionId(),
        person = UUID.randomUUID(),
        allowed = UUID.randomUUID(),
        hidden = UUID.randomUUID();
    fixtures.insertPerson(person, institution, "99110006", false);
    fixtures.insertTrainingPath(allowed, institution);
    fixtures.insertTrainingPath(hidden, institution);
    UUID plan = UUID.randomUUID();
    fixtures.insertStudyPlan(plan, institution, allowed);
    UUID hiddenPlan = UUID.randomUUID();
    fixtures.insertStudyPlan(hiddenPlan, institution, hidden);
    UUID assignment = assignment(person, institution, PermissionCode.COURSE_CREATE);
    transaction()
        .executeWithoutResult(
            tx -> {
              jdbcTemplate.update(
                  "UPDATE person_role_assignments SET access_scope = 'TRAINING_PATHS' WHERE person_role_assignment_id = ?",
                  assignment);
              jdbcTemplate.update(
                  "INSERT INTO person_role_assignment_training_paths (person_role_assignment_id, training_path_id) VALUES (?, ?)",
                  assignment,
                  allowed);
            });
    authenticate(person, institution);
    var page =
        options.execute(
            institution, "study-plans", null, null, null, null, null, PageRequest.of(0, 1));
    assertThat(page.totalItems()).isEqualTo(1);
    assignment(person, institution, PermissionCode.STUDY_PLAN_READ);
    selectPath(assignment(person, institution, PermissionCode.ENROLLMENT_PERIOD_CREATE), allowed);
    invalidator.evictPerson(person, institution);
    assertThat(
            options
                .execute(
                    institution,
                    "study-plans",
                    null,
                    null,
                    null,
                    null,
                    null,
                    PageRequest.of(0, 10),
                    PermissionCode.COURSE_CREATE)
                .items())
        .extracting(item -> item.id())
        .containsExactly(plan);
    assertThat(periodLevels.execute(institution, plan, PermissionCode.ENROLLMENT_PERIOD_CREATE))
        .isEmpty();
    assertThatThrownBy(
            () ->
                periodLevels.execute(
                    institution, hiddenPlan, PermissionCode.ENROLLMENT_PERIOD_CREATE))
        .isInstanceOf(ScopedResourceNotFoundException.class);

    assertThat(page.items()).extracting(item -> item.id()).containsExactly(plan);
    assertThat(
            options
                .execute(
                    institution,
                    "study-plans",
                    null,
                    null,
                    null,
                    hidden,
                    null,
                    PageRequest.of(0, 1),
                    PermissionCode.COURSE_CREATE)
                .totalItems())
        .isZero();
    assertThatThrownBy(
            () ->
                options.execute(
                    institution,
                    "academic-spaces",
                    null,
                    null,
                    null,
                    null,
                    null,
                    PageRequest.of(0, 1)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void roleEditsCannotEscalateItsInstitutionalAssignmentsFromLimitedAuthority() {
    UUID institution = fixtures.firstInstitutionId(),
        actor = UUID.randomUUID(),
        target = UUID.randomUUID(),
        path = UUID.randomUUID();
    fixtures.insertPerson(actor, institution, "99110007", false);
    fixtures.insertPerson(target, institution, "99110008", false);
    fixtures.insertTrainingPath(path, institution);
    assignment(actor, institution, PermissionCode.INSTITUTION_ROLE_UPDATE);
    UUID actorRead = assignment(actor, institution, PermissionCode.TRAINING_PATH_READ);
    UUID actorUpdate = assignment(actor, institution, PermissionCode.TRAINING_PATH_UPDATE);
    selectPath(actorRead, path);
    selectPath(actorUpdate, path);
    UUID targetAssignment = assignment(target, institution, PermissionCode.TRAINING_PATH_READ);
    UUID roleId = roleId(targetAssignment);
    var request =
        new InstitutionRoleRequest(
            "Scoped role edit " + roleId,
            Set.of(
                PermissionCode.TRAINING_PATH_READ.getCode(),
                PermissionCode.TRAINING_PATH_UPDATE.getCode()));
    authenticate(actor, institution);
    assertThatThrownBy(() -> roles.update(institution, roleId, request, actor, Set.of()))
        .isInstanceOf(PermissionDelegationNotAllowedException.class);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM role_permissions WHERE role_id = ?", Long.class, roleId))
        .isEqualTo(1);
    selectPath(targetAssignment, path);
    assertThat(roles.update(institution, roleId, request, actor, Set.of()).permissions())
        .contains(PermissionCode.TRAINING_PATH_UPDATE.getCode());
  }

  @Test
  void aWaitingScopeChangeRechecksRevocationPermissionAfterTheConcurrentCommit() throws Exception {
    UUID institution = fixtures.firstInstitutionId(),
        actor = UUID.randomUUID(),
        target = UUID.randomUUID(),
        path = UUID.randomUUID();
    fixtures.insertPerson(actor, institution, "99110009", false);
    fixtures.insertPerson(target, institution, "99110010", false);
    fixtures.insertTrainingPath(path, institution);
    assignment(actor, institution, PermissionCode.TRAINING_PATH_READ);
    UUID revokeAssignment = assignment(actor, institution, PermissionCode.INSTITUTION_ROLE_REVOKE);
    UUID targetAssignment = assignment(target, institution, PermissionCode.TRAINING_PATH_READ);
    UUID targetRole = roleId(targetAssignment);
    // Prime the snapshot before another transaction removes the operation permission.
    assertThat(authorities.resolvePersonAuthorities(actor, institution).permissions())
        .contains(PermissionCode.INSTITUTION_ROLE_REVOKE);
    var request =
        new ReplacePersonRolesRequest(
            List.of(new AssignRoleRequest(targetRole, AccessScope.TRAINING_PATHS, Set.of(path))));
    var started = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var waiting =
          transaction()
              .execute(
                  tx -> {
                    jdbcTemplate.queryForObject(
                        "SELECT institution_id FROM institutions WHERE institution_id = ? FOR UPDATE",
                        UUID.class,
                        institution);
                    var future =
                        executor.submit(
                            () -> {
                              authenticate(actor, institution);
                              started.countDown();
                              try {
                                return replaceRoles.execute(
                                    institution,
                                    target,
                                    request,
                                    false,
                                    Set.of(PermissionCode.INSTITUTION_ROLE_REVOKE));
                              } finally {
                                SecurityContextHolder.clearContext();
                              }
                            });
                    try {
                      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException exception) {
                      Thread.currentThread().interrupt();
                      throw new IllegalStateException(exception);
                    }
                    assertThatThrownBy(() -> future.get(100, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                    jdbcTemplate.update(
                        "DELETE FROM role_permissions WHERE role_id = ?", roleId(revokeAssignment));
                    return future;
                  });
      assertThatThrownBy(() -> waiting.get(10, TimeUnit.SECONDS))
          .hasCauseInstanceOf(RoleRevocationNotAllowedException.class);
    }
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT access_scope FROM person_role_assignments WHERE person_role_assignment_id = ?",
                String.class,
                targetAssignment))
        .isEqualTo("INSTITUTION");
  }

  @Test
  void preceptorCannotReadForeignApplicationsAttachmentsOrEnrollmentsAndOwnerKeepsOwnAccess() {
    UUID institution = UUID.randomUUID(), actor = UUID.randomUUID(), applicant = UUID.randomUUID();
    fixtures.insertTestInstitution(institution);
    UUID allowed = UUID.randomUUID(), hidden = UUID.randomUUID(), application = UUID.randomUUID();
    fixtures.insertPerson(actor, institution, "99110011", false);
    fixtures.insertPerson(applicant, institution, "99110012", false);
    fixtures.insertTrainingPath(allowed, institution, "Scope Profesorado");
    fixtures.insertTrainingPath(hidden, institution, "Scope Tecnicatura");
    selectPath(assignment(actor, institution, PermissionCode.ENROLLMENT_APPLICATION_READ), allowed);
    selectPath(assignment(actor, institution, PermissionCode.COURSE_ENROLLMENT_READ), allowed);
    UUID year = UUID.randomUUID(),
        plan = UUID.randomUUID(),
        space = UUID.randomUUID(),
        course = UUID.randomUUID(),
        courseClass = UUID.randomUUID();
    fixtures.insertAcademicYear(year, institution, 2098);
    jdbcTemplate.update(
        "INSERT INTO enrollment_applications (enrollment_application_id, institution_id, applicant_person_id, training_path_id, academic_year_id, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        application,
        institution,
        applicant,
        hidden,
        year);
    fixtures.insertStudyPlan(plan, institution, hidden);
    fixtures.insertAcademicSpace(space, institution);
    fixtures.insertCourse(course, institution, plan, space, year);
    fixtures.insertCourseClass(courseClass, institution, course);
    UUID enrollmentId =
        transaction()
            .execute(
                tx -> {
                  var tenant = entityManager.find(Institution.class, institution);
                  var person = entityManager.find(Person.class, applicant);
                  var student =
                      Student.builder()
                          .institution(tenant)
                          .person(person)
                          .fileNumber("SCOPE-TEST")
                          .enrollmentDate(java.time.LocalDate.now())
                          .build();
                  entityManager.persist(student);
                  var enrollment =
                      CourseEnrollment.create(
                          tenant,
                          student,
                          entityManager.find(Course.class, course),
                          entityManager.find(CourseClass.class, courseClass),
                          CourseEnrollmentSource.MANUAL,
                          null,
                          java.time.Instant.now());
                  entityManager.persist(enrollment);
                  return enrollment.getId();
                });
    authenticate(actor, institution);
    assertThat(
            applicationList
                .execute(institution, null, hidden, false, false, null, PageRequest.of(0, 1))
                .getTotalElements())
        .isZero();
    assertThatThrownBy(() -> applicationDetail.execute(institution, application))
        .isInstanceOf(ScopedResourceNotFoundException.class);
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThatThrownBy(() -> attachments.listAttachments(application, authentication))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () -> attachments.getAttachmentContent(application, UUID.randomUUID(), authentication))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(
            enrollments
                .listInstitutional(institution, null, null, PageRequest.of(0, 1))
                .totalItems())
        .isZero();
    assertThatThrownBy(() -> enrollments.get(institution, enrollmentId))
        .isInstanceOf(ScopedResourceNotFoundException.class);
    assertThatThrownBy(() -> enrollments.history(institution, enrollmentId))
        .isInstanceOf(ScopedResourceNotFoundException.class);
    assignment(applicant, institution, PermissionCode.OWN_ENROLLMENT_APPLICATION_READ);
    authenticate(applicant, institution);
    assertThat(
            attachments.listAttachments(
                application, SecurityContextHolder.getContext().getAuthentication()))
        .isEmpty();
    assertThat(
            enrollments.listOwn(institution, applicant, null, null, PageRequest.of(0, 1)).items())
        .extracting(item -> item.id())
        .containsExactly(enrollmentId);
  }

  private UUID roleId(UUID assignment) {
    return requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT role_id FROM person_role_assignments WHERE person_role_assignment_id = ?",
            UUID.class,
            assignment));
  }

  private void selectPath(UUID assignment, UUID path) {
    transaction()
        .executeWithoutResult(
            tx -> {
              jdbcTemplate.update(
                  "UPDATE person_role_assignments SET access_scope = 'TRAINING_PATHS' WHERE person_role_assignment_id = ?",
                  assignment);
              jdbcTemplate.update(
                  "INSERT INTO person_role_assignment_training_paths (person_role_assignment_id, training_path_id) VALUES (?, ?)",
                  assignment,
                  path);
            });
  }

  private UUID assignment(UUID person, UUID institution, PermissionCode permission) {
    UUID role = UUID.randomUUID(), assignment = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO roles (role_id, code, name, scope, is_system, institution_id, created_at, updated_at) VALUES (?, ?, ?, 'INSTITUTION', false, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        role,
        "SCOPE_" + role,
        "Scoped test " + role,
        institution);
    jdbcTemplate.update(
        "INSERT INTO role_permissions (role_id, permission_id) SELECT ?, permission_id FROM permissions WHERE code = ?",
        role,
        permission.getCode());
    jdbcTemplate.update(
        "INSERT INTO person_role_assignments (person_role_assignment_id, person_id, role_id, institution_id, created_at, updated_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        assignment,
        person,
        role,
        institution);
    return assignment;
  }

  private void authenticate(UUID person, UUID institution) {
    var principal =
        JwtAuthenticatedUser.builder()
            .userId(UUID.randomUUID())
            .personId(person)
            .institutionId(institution)
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
  }

  private TransactionTemplate transaction() {
    return new TransactionTemplate(transactionManager);
  }
}
