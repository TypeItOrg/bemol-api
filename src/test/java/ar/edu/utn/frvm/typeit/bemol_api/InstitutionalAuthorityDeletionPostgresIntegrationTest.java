package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.LastInstitutionalAuthorityException;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailSender;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.DeletePersonUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class InstitutionalAuthorityDeletionPostgresIntegrationTest extends DatabaseMigrationTestSupport {
  @Autowired DeletePersonUseCase deletePerson;
  @Autowired TransactionTemplate transaction;
  @MockitoBean MailSender mailSender;

  @Test
  void concurrentInstitutionalDeletionsPreserveOneCurrentAuthority() throws Exception {
    authenticatePlatformAdministrator();
    final UUID institution = UUID.randomUUID(), otherInstitution = UUID.randomUUID();
    final UUID first = UUID.randomUUID(), second = UUID.randomUUID(), deleted = UUID.randomUUID();
    fixtures.insertTestInstitution(institution);
    fixtures.insertTestInstitution(otherInstitution);
    final UUID role = insertAuthorityRole(institution);
    insertAuthority(first, institution, role, "99118001", false);
    insertAuthority(second, institution, role, "99118002", false);
    insertAuthority(deleted, institution, role, "99118003", true);
    insertAuthority(
        UUID.randomUUID(),
        otherInstitution,
        insertAuthorityRole(otherInstitution),
        "99118004",
        false);
    final var started = new CountDownLatch(1);

    try (final var executor = Executors.newSingleThreadExecutor()) {
      final var waiting =
          requireNonNull(
              transaction.execute(
                  status -> {
                    jdbcTemplate.queryForObject(
                        "SELECT institution_id FROM institutions WHERE institution_id = ? FOR UPDATE",
                        UUID.class,
                        institution);
                    final var future =
                        executor.submit(
                            () -> {
                              authenticatePlatformAdministrator();
                              started.countDown();
                              try {
                                deletePerson.execute(institution, second);
                              } finally {
                                SecurityContextHolder.clearContext();
                              }
                            });
                    await(started);
                    assertThatThrownBy(() -> future.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                    deletePerson.execute(institution, first);
                    return future;
                  }));

      assertThatThrownBy(() -> waiting.get(10, TimeUnit.SECONDS))
          .hasCauseInstanceOf(LastInstitutionalAuthorityException.class);
    }

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT deleted FROM people WHERE person_id = ?", Boolean.class, first))
        .isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT deleted FROM people WHERE person_id = ?", Boolean.class, second))
        .isFalse();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM person_role_assignments WHERE institution_id = ?",
                Long.class,
                institution))
        .isEqualTo(3L);
  }

  private UUID insertAuthorityRole(final UUID institution) {
    final UUID role = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO roles (
          role_id, code, name, scope, is_system, institution_id, created_at, updated_at
        ) VALUES (?, 'INSTITUTIONAL_AUTHORITY', 'Authority', 'INSTITUTION', true, ?,
          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        role,
        institution);
    return role;
  }

  private void insertAuthority(
      final UUID person,
      final UUID institution,
      final UUID role,
      final String document,
      final boolean deleted) {
    fixtures.insertPerson(person, institution, document, deleted);
    jdbcTemplate.update(
        """
        INSERT INTO person_role_assignments (
          person_role_assignment_id, person_id, role_id, institution_id, created_at, updated_at
        ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        UUID.randomUUID(),
        person,
        role,
        institution);
  }

  private static void await(final CountDownLatch latch) {
    try {
      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }
}
