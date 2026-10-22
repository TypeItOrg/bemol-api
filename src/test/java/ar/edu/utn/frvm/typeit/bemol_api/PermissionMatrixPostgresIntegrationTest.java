package ar.edu.utn.frvm.typeit.bemol_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.ScopedResourceNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorityResolver;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorizationCacheInvalidator;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ScopedAuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Exercises every catalog entry using persisted grants, rather than mocked authority rows. */
@IntegrationTest
class PermissionMatrixPostgresIntegrationTest extends DatabaseMigrationTestSupport {
  @Autowired private AuthorityResolver authorities;
  @Autowired private AuthorizationService authorization;
  @Autowired private ScopedAuthorizationService scoped;
  @Autowired private AuthorizationCacheInvalidator invalidator;
  @Autowired private PlatformTransactionManager transactions;

  @ParameterizedTest(name = "{0}: absent, institution, paths, union, tenant and revocation")
  @EnumSource(PermissionCode.class)
  void everyPermissionRespectsItsPersistedScope(final PermissionCode permission) {
    final UUID institution = UUID.randomUUID();
    final UUID otherInstitution = UUID.randomUUID();
    final UUID person = UUID.randomUUID();
    final UUID firstPath = UUID.randomUUID();
    final UUID secondPath = UUID.randomUUID();
    final UUID hiddenPath = UUID.randomUUID();
    fixtures.insertTestInstitution(institution);
    fixtures.insertTestInstitution(otherInstitution);
    fixtures.insertPerson(person, institution, "99190001", false);
    fixtures.insertTrainingPath(firstPath, institution);
    fixtures.insertTrainingPath(secondPath, institution);
    fixtures.insertTrainingPath(hiddenPath, institution);
    authenticate(person, institution);

    assertThat(authorization.hasPermission(authentication(), permission)).isFalse();
    assertDenied(permission, institution, firstPath);

    // Single-permission fixtures deliberately avoid implied grants, to detect cross-permission
    // leaks.
    final UUID firstAssignment = grant(person, institution, permission);
    invalidator.evictPerson(person, institution);
    assertThat(authorities.resolveForPerson(person, institution)).containsExactly(permission);
    assertThat(authorization.hasPermission(authentication(), permission)).isTrue();
    assertThatCode(() -> scoped.require(permission, institution, firstPath))
        .doesNotThrowAnyException();
    assertThatCode(() -> scoped.require(permission, institution, hiddenPath))
        .doesNotThrowAnyException();
    assertDenied(permission, otherInstitution, firstPath);
    assertThat(authorities.resolveForPerson(person, otherInstitution)).isEmpty();

    selectPath(firstAssignment, firstPath);
    invalidator.evictPerson(person, institution);
    assertThat(authorization.hasPermission(authentication(), permission))
        .isEqualTo(permission.supportsTrainingPaths());
    if (permission.supportsTrainingPaths()) {
      assertThatCode(() -> scoped.require(permission, institution, firstPath))
          .doesNotThrowAnyException();
      assertThat(scoped.access(permission).trainingPathIds()).containsExactly(firstPath);
    } else {
      assertDenied(permission, institution, firstPath);
      assertThat(authorities.resolveForPerson(person, institution)).isEmpty();
    }
    assertDenied(permission, institution, hiddenPath);
    assertDenied(permission, otherInstitution, firstPath);

    final UUID secondAssignment = grant(person, institution, permission);
    selectPath(secondAssignment, secondPath);
    invalidator.evictPerson(person, institution);
    if (permission.supportsTrainingPaths()) {
      assertThat(scoped.access(permission).trainingPathIds())
          .containsExactlyInAnyOrder(firstPath, secondPath);
      assertThatCode(() -> scoped.require(permission, institution, secondPath))
          .doesNotThrowAnyException();
    }
    assertDenied(permission, institution, hiddenPath);

    // A global grant for another permission must not expand this permission's paths.
    final PermissionCode unrelated =
        permission == PermissionCode.INSTITUTION_PERSON_READ_ANY
            ? PermissionCode.TRAINING_PATH_READ
            : PermissionCode.INSTITUTION_PERSON_READ_ANY;
    grant(person, institution, unrelated);
    invalidator.evictPerson(person, institution);
    assertThat(authorization.hasPermission(authentication(), unrelated)).isTrue();
    assertDenied(permission, institution, hiddenPath);

    final UUID globalAssignment = grant(person, institution, permission);
    invalidator.evictPerson(person, institution);
    assertThat(scoped.access(permission).institutional()).isTrue();
    assertThatCode(() -> scoped.require(permission, institution, hiddenPath))
        .doesNotThrowAnyException();

    // Remove only the global grant: selected grants must remain, without a stale global snapshot.
    jdbcTemplate.update(
        "DELETE FROM person_role_assignments WHERE person_role_assignment_id = ?",
        globalAssignment);
    invalidator.evictPerson(person, institution);
    assertDenied(permission, institution, hiddenPath);

    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              jdbcTemplate.update(
                  "DELETE FROM person_role_assignment_training_paths WHERE"
                      + " person_role_assignment_id IN (?, ?)",
                  firstAssignment,
                  secondAssignment);
              jdbcTemplate.update(
                  "DELETE FROM person_role_assignments WHERE person_role_assignment_id IN (?, ?)",
                  firstAssignment,
                  secondAssignment);
              invalidator.evictPerson(person, institution);
            });
    assertThat(authorization.hasPermission(authentication(), permission)).isFalse();
    assertDenied(permission, institution, firstPath);
    assertDenied(permission, institution, secondPath);

    authenticatePlatformAdministrator();
    assertThat(authorization.hasPermission(authentication(), permission)).isTrue();
    assertThatCode(() -> scoped.require(permission, institution, firstPath))
        .doesNotThrowAnyException();

    SecurityContextHolder.clearContext();
    assertThat(authorization.hasPermission(null, permission)).isFalse();
    assertDenied(permission, institution, firstPath);
  }

  private void assertDenied(PermissionCode permission, UUID institution, UUID path) {
    assertThatThrownBy(() -> scoped.require(permission, institution, path))
        .isInstanceOf(ScopedResourceNotFoundException.class);
  }

  private UUID grant(UUID person, UUID institution, PermissionCode permission) {
    final UUID role = UUID.randomUUID();
    final UUID assignment = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO roles (role_id, code, name, scope, is_system, institution_id, created_at,"
            + " updated_at) VALUES (?, ?, ?, 'INSTITUTION', false, ?, CURRENT_TIMESTAMP,"
            + " CURRENT_TIMESTAMP)",
        role,
        "MATRIX_" + role,
        "Matrix " + role,
        institution);
    assertThat(
            jdbcTemplate.update(
                "INSERT INTO role_permissions (role_id, permission_id) SELECT ?, permission_id FROM"
                    + " permissions WHERE code = ?",
                role,
                permission.getCode()))
        .isEqualTo(1);
    jdbcTemplate.update(
        "INSERT INTO person_role_assignments (person_role_assignment_id, person_id, role_id,"
            + " institution_id, created_at, updated_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP,"
            + " CURRENT_TIMESTAMP)",
        assignment,
        person,
        role,
        institution);
    return assignment;
  }

  private void selectPath(UUID assignment, UUID path) {
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              jdbcTemplate.update(
                  "UPDATE person_role_assignments SET access_scope = 'TRAINING_PATHS' WHERE"
                      + " person_role_assignment_id = ?",
                  assignment);
              jdbcTemplate.update(
                  "INSERT INTO person_role_assignment_training_paths (person_role_assignment_id,"
                      + " training_path_id) VALUES (?, ?)",
                  assignment,
                  path);
            });
  }

  private void authenticate(UUID person, UUID institution) {
    final var principal =
        JwtAuthenticatedUser.builder()
            .userId(UUID.randomUUID())
            .personId(person)
            .institutionId(institution)
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
  }

  private @Nullable Authentication authentication() {
    return SecurityContextHolder.getContext().getAuthentication();
  }
}
