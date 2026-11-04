package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.InstitutionalAccountManagementUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.PermissionDelegationNotAllowedException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.RoleNotAssignableException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.CreatePersonRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.CreatePersonUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@IntegrationTest
class InitialRoleDelegationPostgresIntegrationTest extends DatabaseMigrationTestSupport {
  @Autowired CreatePersonUseCase createPerson;
  @MockitoBean InstitutionalAccountManagementUseCase invitations;

  @Test
  void rejectsAuthorityGrantWithoutPersistingAnAccount() {
    final UUID institutionId = fixtures.firstInstitutionId();
    authenticateOperator(institutionId, "99185001", Set.of(PermissionCode.INSTITUTION_ROLE_ASSIGN));

    assertThatThrownBy(
            () ->
                createPerson.execute(
                    institutionId, request("99185002", SystemRoleCode.INSTITUTIONAL_AUTHORITY)))
        .isInstanceOf(RoleNotAssignableException.class);

    assertPersonAbsent(institutionId, "99185002");
  }

  @Test
  void rejectsUndelegableSystemRoleWithoutPersistingAnAccount() {
    final UUID institutionId = fixtures.firstInstitutionId();
    authenticateOperator(institutionId, "99185003", Set.of(PermissionCode.INSTITUTION_ROLE_ASSIGN));

    assertThatThrownBy(
            () -> createPerson.execute(institutionId, request("99185004", SystemRoleCode.STUDENT)))
        .isInstanceOf(PermissionDelegationNotAllowedException.class);

    assertPersonAbsent(institutionId, "99185004");
  }

  @Test
  void permitsDefaultApplicantWithoutRoleAssignmentPermission() {
    final UUID institutionId = fixtures.firstInstitutionId();
    authenticateOperator(institutionId, "99185005", Set.of());

    final var person = createPerson.execute(institutionId, request("99185006", null));

    assertAssignment(person.personId(), SystemRoleCode.APPLICANT);
  }

  @Test
  void permitsInstitutionWideRoleWhenEveryPermissionCanBeDelegated() {
    final UUID institutionId = fixtures.firstInstitutionId();
    final var permissions = new java.util.HashSet<PermissionCode>();
    permissions.add(PermissionCode.INSTITUTION_ROLE_ASSIGN);
    jdbcTemplate
        .queryForList(
            "SELECT permission.code FROM role_permissions assigned JOIN roles role ON role.role_id = assigned.role_id JOIN permissions permission ON permission.permission_id = assigned.permission_id WHERE role.institution_id = ? AND role.code = 'STUDENT'",
            String.class,
            institutionId)
        .stream()
        .map(PermissionCode::fromCode)
        .forEach(permissions::add);
    authenticateOperator(institutionId, "99185007", permissions);

    final var person =
        createPerson.execute(institutionId, request("99185008", SystemRoleCode.STUDENT));

    assertAssignment(person.personId(), SystemRoleCode.STUDENT);
  }

  @Test
  void preservesPlatformAdministratorAuthorityGrant() {
    final UUID institutionId = fixtures.firstInstitutionId();
    authenticatePlatformAdministrator();

    final var person =
        createPerson.execute(
            institutionId, request("99185009", SystemRoleCode.INSTITUTIONAL_AUTHORITY));

    assertAssignment(person.personId(), SystemRoleCode.INSTITUTIONAL_AUTHORITY);
  }

  private void authenticateOperator(
      final UUID institutionId,
      final String documentNumber,
      final Set<PermissionCode> additionalPermissions) {
    final UUID personId = UUID.randomUUID();
    final UUID roleId = UUID.randomUUID();
    fixtures.insertPerson(personId, institutionId, documentNumber, false);
    jdbcTemplate.update(
        "INSERT INTO roles (role_id, code, name, scope, is_system, institution_id, created_at, updated_at) VALUES (?, ?, ?, 'INSTITUTION', false, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        roleId,
        "INITIAL_" + roleId,
        "Initial role operator " + roleId,
        institutionId);
    final var permissions = new java.util.HashSet<>(additionalPermissions);
    permissions.add(PermissionCode.INSTITUTION_PERSON_CREATE);
    permissions.add(PermissionCode.INSTITUTION_PERSON_READ_ANY);
    for (final var permission : PermissionCode.withRequiredPermissions(permissions)) {
      jdbcTemplate.update(
          "INSERT INTO role_permissions (role_id, permission_id) SELECT ?, permission_id FROM permissions WHERE code = ?",
          roleId,
          permission.getCode());
    }
    jdbcTemplate.update(
        "INSERT INTO person_role_assignments (person_role_assignment_id, person_id, role_id, institution_id, created_at, updated_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        UUID.randomUUID(),
        personId,
        roleId,
        institutionId);
    final var principal =
        JwtAuthenticatedUser.builder()
            .userId(personId)
            .personId(personId)
            .institutionId(institutionId)
            .documentNumber(documentNumber)
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken(principal, ""));
  }

  private void assertPersonAbsent(final UUID institutionId, final String documentNumber) {
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM people WHERE institution_id = ? AND document_number = ?",
                Long.class,
                institutionId,
                documentNumber))
        .isZero();
  }

  private void assertAssignment(final UUID personId, final SystemRoleCode role) {
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT role.code FROM person_role_assignments assignment JOIN roles role ON role.role_id = assignment.role_id WHERE assignment.person_id = ? AND assignment.access_scope = 'INSTITUTION'",
                String.class,
                personId))
        .containsExactly(role.name());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE person_id = ?", Long.class, personId))
        .isZero();
    final UUID institutionId =
        requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT institution_id FROM people WHERE person_id = ?", UUID.class, personId));
    final String email =
        requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT email FROM people WHERE person_id = ?", String.class, personId));
    verify(invitations).invite(institutionId, personId, email);
  }

  private CreatePersonRequest request(
      final String documentNumber, final @Nullable SystemRoleCode role) {
    return new CreatePersonRequest(
        "Ana",
        "Garcia",
        documentNumber,
        documentNumber + "@example.test",
        "03534100000",
        LocalDate.of(2000, 1, 1),
        role,
        requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT country_id FROM countries ORDER BY country_id LIMIT 1", UUID.class)));
  }
}
