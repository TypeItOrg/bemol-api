package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.AuthenticationSessionIssuer;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.AccessScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.LastPersonRoleRevocationException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.PermissionDelegationNotAllowedException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.AssignRoleRequest;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.ReplacePersonRolesRequest;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AssignPersonSystemRoleUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorityResolver;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionRoleProvisioner;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.PermissionRoleSeed;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ReplacePersonRolesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.RevokePersonRoleUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailSender;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.CreateGuardianDependentRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.GuardianDependentRequestMode;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.RegisterGuardianDependentUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.ResolveGuardianLinkUseCase;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class RepresentativeRolePostgresIntegrationTest extends DatabaseMigrationTestSupport {
  @Autowired EntityManager entityManager;
  @Autowired TransactionTemplate transaction;
  @Autowired PermissionRoleSeed seed;
  @Autowired InstitutionRoleProvisioner provisioner;
  @Autowired AssignPersonSystemRoleUseCase systemRoles;
  @Autowired ReplacePersonRolesUseCase replaceRoles;
  @Autowired RevokePersonRoleUseCase revokeRole;
  @Autowired AuthorityResolver authorities;
  @Autowired UserRepository users;
  @Autowired AuthenticationSessionIssuer sessions;
  @Autowired RegisterGuardianDependentUseCase requests;
  @Autowired ResolveGuardianLinkUseCase reviews;
  @Autowired MockMvc mvc;
  @MockitoBean MailSender mail;

  private UUID institution;

  @BeforeEach
  void prepareInstitution() {
    authenticatePlatformAdministrator();
    institution = UUID.randomUUID();
    fixtures.insertTestInstitution(institution);
    provision();
  }

  @Test
  void seedAddsRepresentativeToExistingInstitutionsWithoutResettingCustomPermissions() {
    final UUID representative = role(SystemRoleCode.REPRESENTATIVE);
    assertThat(permissionCodes(representative))
        .containsExactly(PermissionCode.GUARDIAN_LINK_REQUEST.getCode());
    jdbcTemplate.update("DELETE FROM role_permissions WHERE role_id = ?", representative);
    jdbcTemplate.update("DELETE FROM roles WHERE role_id = ?", representative);

    seed.run(new DefaultApplicationArguments());
    final UUID restored = role(SystemRoleCode.REPRESENTATIVE);
    assertThat(permissionCodes(restored))
        .containsExactly(PermissionCode.GUARDIAN_LINK_REQUEST.getCode());
    final UUID template =
        requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT role_id FROM roles WHERE code = 'REPRESENTATIVE' AND institution_id IS NULL AND scope = 'INSTITUTION'",
                UUID.class));
    assertThat(permissionCodes(template))
        .containsExactly(PermissionCode.GUARDIAN_LINK_REQUEST.getCode());
    jdbcTemplate.update("DELETE FROM role_permissions WHERE role_id = ?", restored);
    grant(restored, PermissionCode.SHIFT_READ);
    provision();
    seed.run(new DefaultApplicationArguments());
    final long roleCount =
        count("SELECT count(*) FROM roles WHERE institution_id = ?", institution);
    final long permissionCount =
        count("SELECT count(*) FROM role_permissions WHERE role_id = ?", restored);
    provision();
    seed.run(new DefaultApplicationArguments());

    assertThat(role(SystemRoleCode.REPRESENTATIVE)).isEqualTo(restored);
    assertThat(permissionCodes(restored)).containsExactly(PermissionCode.SHIFT_READ.getCode());
    assertThat(count("SELECT count(*) FROM roles WHERE institution_id = ?", institution))
        .isEqualTo(roleCount);
    assertThat(count("SELECT count(*) FROM role_permissions WHERE role_id = ?", restored))
        .isEqualTo(permissionCount);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT name FROM roles WHERE role_id = ?", String.class, restored))
        .isEqualTo("Representante");
  }

  @ParameterizedTest
  @EnumSource(
      value = SystemRoleCode.class,
      names = {"APPLICANT", "REPRESENTATIVE"})
  void assignmentsCoexistInEitherOrderAndStudentPromotionRetainsRepresentative(
      final SystemRoleCode first) {
    final UUID person = person();
    assign(person, first);
    assign(
        person,
        first == SystemRoleCode.APPLICANT
            ? SystemRoleCode.REPRESENTATIVE
            : SystemRoleCode.APPLICANT);
    assertThat(assignedCodes(person)).containsExactlyInAnyOrder("APPLICANT", "REPRESENTATIVE");
    assign(person, SystemRoleCode.REPRESENTATIVE);
    assertThat(assignedCodes(person)).containsExactlyInAnyOrder("APPLICANT", "REPRESENTATIVE");

    assign(person, SystemRoleCode.STUDENT);
    assertThat(assignedCodes(person)).containsExactlyInAnyOrder("STUDENT", "REPRESENTATIVE");
    assign(person, SystemRoleCode.TEACHER);
    assign(person, SystemRoleCode.APPLICANT);
    assertThat(assignedCodes(person)).containsExactlyInAnyOrder("APPLICANT", "REPRESENTATIVE");
  }

  @Test
  void replacementPreservesPairButStillNormalizesApplicantWithStaff() {
    final UUID person = person();
    assign(person, SystemRoleCode.APPLICANT);
    replaceRoles.execute(
        institution,
        person,
        desired(SystemRoleCode.APPLICANT, SystemRoleCode.REPRESENTATIVE),
        true,
        Set.of());
    assertThat(assignedCodes(person)).containsExactlyInAnyOrder("APPLICANT", "REPRESENTATIVE");
    replaceRoles.execute(
        institution,
        person,
        desired(SystemRoleCode.APPLICANT, SystemRoleCode.REPRESENTATIVE, SystemRoleCode.TEACHER),
        true,
        Set.of());
    assertThat(assignedCodes(person)).containsExactlyInAnyOrder("TEACHER", "REPRESENTATIVE");
  }

  @Test
  void httpAssignmentDoesNotInventRevocationsAndChecksFreshDelegation() throws Exception {
    final UUID actor = person(), target = person();
    final UUID actorRole =
        customRole(
            actor,
            PermissionCode.INSTITUTION_ROLE_ASSIGN,
            PermissionCode.GUARDIAN_LINK_REQUEST,
            PermissionCode.ACADEMIC_OFFER_READ);
    assign(target, SystemRoleCode.APPLICANT);
    token(target);
    assertThat(authorities.resolvePersonAuthorities(target, institution).permissions())
        .doesNotContain(PermissionCode.GUARDIAN_LINK_REQUEST);
    final String bearer = token(actor);
    mvc.perform(
            post("/api/v1/institutions/{institution}/people/{person}/roles", institution, target)
                .header("Authorization", "Bearer " + bearer)
                .contentType("application/json")
                .content(
                    "{\"roleId\":\""
                        + role(SystemRoleCode.REPRESENTATIVE)
                        + "\",\"accessScope\":\"INSTITUTION\",\"trainingPathIds\":[]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.technicalCode").value("REPRESENTATIVE"))
        .andExpect(jsonPath("$.displayName").value("Representante"));
    assertThat(assignedCodes(target)).containsExactlyInAnyOrder("APPLICANT", "REPRESENTATIVE");
    assertThat(
            count(
                "SELECT count(*) FROM user_sessions WHERE user_id = (SELECT user_id FROM users WHERE person_id = ?) AND active",
                target))
        .isZero();
    assertThat(authorities.resolvePersonAuthorities(target, institution).permissions())
        .contains(PermissionCode.GUARDIAN_LINK_REQUEST);
    mvc.perform(
            put("/api/v1/institutions/{institution}/people/{person}/roles", institution, target)
                .header("Authorization", "Bearer " + bearer)
                .contentType("application/json")
                .content(
                    "{\"assignments\":[{\"roleId\":\""
                        + role(SystemRoleCode.APPLICANT)
                        + "\",\"accessScope\":\"INSTITUTION\",\"trainingPathIds\":[]},{\"roleId\":\""
                        + role(SystemRoleCode.REPRESENTATIVE)
                        + "\",\"accessScope\":\"INSTITUTION\",\"trainingPathIds\":[]}]}"))
        .andExpect(status().isOk());
    final UUID denied = person();
    assign(denied, SystemRoleCode.APPLICANT);
    jdbcTemplate.update(
        "DELETE FROM role_permissions WHERE role_id = ? AND permission_id = (SELECT permission_id FROM permissions WHERE code = ?)",
        actorRole,
        PermissionCode.GUARDIAN_LINK_REQUEST.getCode());
    mvc.perform(
            post("/api/v1/institutions/{institution}/people/{person}/roles", institution, denied)
                .header("Authorization", "Bearer " + bearer)
                .contentType("application/json")
                .content(
                    "{\"roleId\":\""
                        + role(SystemRoleCode.REPRESENTATIVE)
                        + "\",\"accessScope\":\"INSTITUTION\",\"trainingPathIds\":[]}"))
        .andExpect(status().isForbidden());
    assertThat(assignedCodes(denied)).containsExactly("APPLICANT");
  }

  @Test
  void httpCreationAcceptsSingularRepresentativeAndStillDefersUser() throws Exception {
    final UUID creator = person();
    assign(creator, SystemRoleCode.INSTITUTIONAL_AUTHORITY);
    final String document = fixtures.randomDocumentNumber();
    final String bearer = token(creator);
    mvc.perform(
            post("/api/v1/institutions/{institution}/people", institution)
                .header("Authorization", "Bearer " + bearer)
                .contentType("application/json")
                .content(
                    "{\"firstName\":\"Ana\",\"lastName\":\"Garcia\",\"documentNumber\":\""
                        + document
                        + "\",\"email\":\""
                        + document
                        + "@example.test\",\"birthDate\":\"2000-01-01\",\"initialRole\":\"REPRESENTATIVE\"}"))
        .andExpect(status().isCreated());
    final UUID created =
        requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT person_id FROM people WHERE institution_id = ? AND document_number = ?",
                UUID.class,
                institution,
                document));
    assertThat(assignedCodes(created)).containsExactly("REPRESENTATIVE");
    assertThat(count("SELECT count(*) FROM users WHERE person_id = ?", created)).isZero();
    assertThat(
            count(
                "SELECT count(*) FROM person_access_invitations WHERE person_id = ? AND consumed_at IS NULL",
                created))
        .isEqualTo(1);
  }

  @Test
  void representativeCannotEnrollItselfButCanOperateForLinkedStudent() throws Exception {
    final UUID tutor = person(), student = person();
    assign(tutor, SystemRoleCode.REPRESENTATIVE);
    assign(student, SystemRoleCode.STUDENT);
    final String bearer = token(tutor);
    mvc.perform(
            get(
                    "/api/v1/institutions/{institution}/self-service/{person}/enrollment-applications/options/periods",
                    institution,
                    tutor)
                .header("Authorization", "Bearer " + bearer))
        .andExpect(status().isForbidden());
    link(tutor, student);
    jdbcTemplate.update(
        "UPDATE user_sessions SET active_person_id = ? WHERE user_id = (SELECT user_id FROM users WHERE person_id = ?)",
        student,
        tutor);
    mvc.perform(
            get(
                    "/api/v1/institutions/{institution}/self-service/{person}/enrollment-applications/options/periods",
                    institution,
                    student)
                .header("Authorization", "Bearer " + bearer))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/institutions/{institution}/self-service/{person}", institution, student)
                .header("Authorization", "Bearer " + bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.actorPersonId").value(tutor.toString()))
        .andExpect(jsonPath("$.canEnroll").value(true));
    mvc.perform(
            post("/api/v1/institutions/{institution}/representations", institution)
                .header("Authorization", "Bearer " + bearer)
                .contentType("application/json")
                .content(
                    "{\"documentNumber\":\""
                        + fixtures.randomDocumentNumber()
                        + "\",\"firstName\":\"Ana\",\"lastName\":\"Garcia\",\"birthDate\":\"2016-01-01\",\"relationship\":\"LEGAL_GUARDIAN\",\"isPrimaryContact\":false,\"ownAccessRequested\":false,\"requestMode\":\"NEW\"}"))
        .andExpect(status().isCreated());
    assertThat(count("SELECT count(*) FROM person_guardians WHERE tutor_person_id = ?", tutor))
        .isEqualTo(2);
    assertThat(count("SELECT count(*) FROM person_guardians WHERE tutor_person_id = ?", student))
        .isZero();
    final UUID otherInstitution = UUID.randomUUID();
    fixtures.insertTestInstitution(otherInstitution);
    mvc.perform(
            get(
                    "/api/v1/institutions/{institution}/self-service/{person}",
                    otherInstitution,
                    student)
                .header("Authorization", "Bearer " + bearer))
        .andExpect(status().isForbidden());
  }

  @Test
  void approvalKeepsRepresentativeOnlyAndLosingPermissionPreservesExistingLinks() throws Exception {
    final UUID tutor = person(), dependent = person();
    assign(tutor, SystemRoleCode.REPRESENTATIVE);
    assign(dependent, SystemRoleCode.REPRESENTATIVE);
    final String bearer = token(tutor);
    as(tutor);
    final var pending = requests.execute(institution, tutor, existingRequest(dependent));
    authenticatePlatformAdministrator();
    reviews.approveForPlatform(institution, pending.personGuardianId());
    assertThat(assignedCodes(dependent)).containsExactly("REPRESENTATIVE");
    assertThat(assignedCodes(tutor)).containsExactly("REPRESENTATIVE");
    final UUID pendingDependent = person();
    as(tutor);
    final var second = requests.execute(institution, tutor, existingRequest(pendingDependent));
    jdbcTemplate.update(
        "DELETE FROM role_permissions WHERE role_id = ?", role(SystemRoleCode.REPRESENTATIVE));
    assertThatThrownBy(() -> requests.execute(institution, tutor, existingRequest(person())))
        .isInstanceOf(PermissionDelegationNotAllowedException.class);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT status FROM person_guardians WHERE person_guardian_id IN (?, ?)",
                String.class,
                pending.personGuardianId(),
                second.personGuardianId()))
        .containsExactlyInAnyOrder("ACTIVE", "PENDING");
    mvc.perform(
            get("/api/v1/institutions/{institution}/representations", institution)
                .header("Authorization", "Bearer " + bearer))
        .andExpect(status().isOk());
    mvc.perform(
            delete(
                    "/api/v1/institutions/{institution}/representations/requests/{link}",
                    institution,
                    second.personGuardianId())
                .header("Authorization", "Bearer " + bearer))
        .andExpect(status().isNoContent());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM person_guardians WHERE person_guardian_id = ?",
                String.class,
                pending.personGuardianId()))
        .isEqualTo("ACTIVE");
    authenticatePlatformAdministrator();
    assertThatThrownBy(
            () -> revokeRole.execute(institution, tutor, role(SystemRoleCode.REPRESENTATIVE), true))
        .isInstanceOf(LastPersonRoleRevocationException.class);
  }

  @Test
  void selectedRepresentativesPermissionDoesNotAuthorizeTheRootActor() throws Exception {
    final UUID root = person(), selected = person();
    assign(root, SystemRoleCode.APPLICANT);
    assign(selected, SystemRoleCode.REPRESENTATIVE);
    link(root, selected);
    final String bearer = token(root);
    jdbcTemplate.update(
        "UPDATE user_sessions SET active_person_id = ? WHERE user_id = (SELECT user_id FROM users WHERE person_id = ?)",
        selected,
        root);
    mvc.perform(
            post("/api/v1/institutions/{institution}/representations", institution)
                .header("Authorization", "Bearer " + bearer)
                .contentType("application/json")
                .content(
                    "{\"documentNumber\":\""
                        + fixtures.randomDocumentNumber()
                        + "\",\"firstName\":\"Ana\",\"lastName\":\"Garcia\",\"birthDate\":\"2016-01-01\",\"relationship\":\"LEGAL_GUARDIAN\",\"isPrimaryContact\":false,\"ownAccessRequested\":false,\"requestMode\":\"NEW\"}"))
        .andExpect(status().isForbidden());
    assertThat(count("SELECT count(*) FROM person_guardians WHERE tutor_person_id = ?", root))
        .isEqualTo(1);
    assertThat(count("SELECT count(*) FROM person_guardians WHERE tutor_person_id = ?", selected))
        .isZero();
  }

  @Test
  void customPermissionRoleCanRequestWithoutRepresentative() {
    final UUID tutor = person(), dependent = person();
    customRole(tutor, PermissionCode.GUARDIAN_LINK_REQUEST);
    token(tutor);
    as(tutor);

    final var pending = requests.execute(institution, tutor, existingRequest(dependent));

    assertThat(pending.status().name()).isEqualTo("PENDING");
    assertThat(assignedCodes(tutor)).doesNotContain("REPRESENTATIVE");
  }

  private void provision() {
    transaction.executeWithoutResult(
        tx -> provisioner.provision(entityManager.find(Institution.class, institution)));
  }

  private UUID person() {
    final UUID id = UUID.randomUUID();
    fixtures.insertPerson(id, institution, fixtures.randomDocumentNumber(), false);
    return id;
  }

  private void assign(final UUID person, final SystemRoleCode code) {
    transaction.executeWithoutResult(
        tx -> systemRoles.execute(entityManager.find(Person.class, person), code));
  }

  private UUID role(final SystemRoleCode code) {
    return requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT role_id FROM roles WHERE institution_id = ? AND code = ?",
            UUID.class,
            institution,
            code.name()));
  }

  private List<String> permissionCodes(final UUID role) {
    return jdbcTemplate.query(
        "SELECT p.code FROM role_permissions rp JOIN permissions p ON p.permission_id = rp.permission_id WHERE rp.role_id = ?",
        (row, index) -> requireNonNull(row.getString("code")),
        role);
  }

  private List<String> assignedCodes(final UUID person) {
    return jdbcTemplate.query(
        "SELECT r.code FROM person_role_assignments a JOIN roles r ON r.role_id = a.role_id WHERE a.person_id = ? AND a.institution_id = ?",
        (row, index) -> requireNonNull(row.getString("code")),
        person,
        institution);
  }

  private ReplacePersonRolesRequest desired(final SystemRoleCode... codes) {
    return new ReplacePersonRolesRequest(
        java.util.Arrays.stream(codes)
            .map(code -> new AssignRoleRequest(role(code), AccessScope.INSTITUTION, Set.of()))
            .toList());
  }

  private UUID customRole(final UUID person, final PermissionCode... permissions) {
    final UUID role = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO roles(role_id, code, name, scope, is_system, institution_id, created_at, updated_at) VALUES(?, ?, 'Assign only', 'INSTITUTION', false, ?, now(), now())",
        role,
        "REP_TEST_" + role,
        institution);
    for (final var permission : permissions) {
      grant(role, permission);
    }
    jdbcTemplate.update(
        "INSERT INTO person_role_assignments(person_role_assignment_id, person_id, role_id, institution_id, created_at, updated_at) VALUES(?, ?, ?, ?, now(), now())",
        UUID.randomUUID(),
        person,
        role,
        institution);
    return role;
  }

  private void grant(final UUID role, final PermissionCode permission) {
    jdbcTemplate.update(
        "INSERT INTO role_permissions(role_id, permission_id) SELECT ?, permission_id FROM permissions WHERE code = ?",
        role,
        permission.getCode());
  }

  private String token(final UUID person) {
    if (count("SELECT count(*) FROM users WHERE person_id = ?", person) == 0) {
      jdbcTemplate.update(
          "INSERT INTO users(user_id, person_id, institution_id, password, enabled, created_at, updated_at) VALUES(?, ?, ?, 'test-only', true, now(), now())",
          UUID.randomUUID(),
          person,
          institution);
    }
    final var user =
        users
            .findWithPersonAndInstitutionById(
                requireNonNull(
                    jdbcTemplate.queryForObject(
                        "SELECT user_id FROM users WHERE person_id = ?", UUID.class, person)))
            .orElseThrow();
    return sessions
        .issue(user, "127.0.0.1", "Representative test", false, "PASSWORD")
        .tokens()
        .accessToken();
  }

  private void as(final UUID person) {
    final UUID user =
        requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE person_id = ?", UUID.class, person));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new TestingAuthenticationToken(
                new JwtAuthenticatedUser(
                    user,
                    person,
                    fixtures.randomDocumentNumber(),
                    institution,
                    UUID.randomUUID(),
                    "test"),
                "",
                "ROLE_INSTITUTIONAL_USER"));
  }

  private CreateGuardianDependentRequest existingRequest(final UUID dependent) {
    return new CreateGuardianDependentRequest(
        requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT document_number FROM people WHERE person_id = ?", String.class, dependent)),
        null,
        null,
        null,
        GuardianRelationship.LEGAL_GUARDIAN,
        false,
        null,
        false,
        GuardianDependentRequestMode.EXISTING);
  }

  private void link(final UUID tutor, final UUID dependent) {
    jdbcTemplate.update(
        "INSERT INTO person_guardians(person_guardian_id, institution_id, tutor_person_id, dependent_person_id, declared_document_number, request_mode, relationship, status, created_at, updated_at) VALUES(?, ?, ?, ?, ?, 'EXISTING', 'LEGAL_GUARDIAN', 'ACTIVE', now(), now())",
        UUID.randomUUID(),
        institution,
        tutor,
        dependent,
        existingRequest(dependent).documentNumber());
  }

  private long count(final String sql, final Object... args) {
    return requireNonNull(jdbcTemplate.queryForObject(sql, Long.class, args));
  }
}
