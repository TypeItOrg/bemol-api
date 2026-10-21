package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@IntegrationTest
public abstract class DatabaseMigrationTestSupport {

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"));

  // The Testcontainers lifecycle owns and stops this shared container.
  @SuppressWarnings("resource")
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @Autowired JdbcTemplate jdbcTemplate;

  MigrationFixtures fixtures;

  @BeforeEach
  void createFixtures() {
    fixtures = new MigrationFixtures(jdbcTemplate);
  }

  protected void authenticatePlatformAdministrator() {
    final var id =
        jdbcTemplate.queryForObject(
            "SELECT platform_account_id FROM platform_accounts WHERE email = ?",
            UUID.class,
            "admin@plataforma.com");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new TestingAuthenticationToken(
                AuthTestData.platformPrincipal(requireNonNull(id)), "", "ROLE_PLATFORM_ADMIN"));
  }

  @AfterEach
  void clearTestAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @DynamicPropertySource
  static void infrastructureProperties(final DynamicPropertyRegistry registry) {
    Startables.deepStart(POSTGRES, REDIS).join();
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
}
