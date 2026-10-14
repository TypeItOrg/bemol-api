package ar.edu.utn.frvm.typeit.boero_api.support;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.testcontainers.containers.PostgreSQLContainer;

/** Shares server startup within a test JVM while keeping each suite's database isolated. */
public final class PostgresTestDatabase {

  // Ryuk removes these servers when the test JVM exits; Spring contexts may outlive a class.
  private static final Map<String, PostgreSQLContainer<?>> SERVERS = new HashMap<>();

  private final String image;
  private final String databaseName;
  private @Nullable String jdbcUrl;

  public PostgresTestDatabase(final String image, final String databaseName) {
    if (!databaseName.matches("[a-z][a-z0-9_]{0,62}")) {
      throw new IllegalArgumentException("Use a simple PostgreSQL test database identifier");
    }
    this.image = image;
    this.databaseName = databaseName;
  }

  public synchronized String getJdbcUrl() {
    if (jdbcUrl == null) {
      final PostgreSQLContainer<?> server = server(image);
      try (final var connection =
              DriverManager.getConnection(server.getJdbcUrl(), getUsername(), getPassword());
          final var statement = connection.createStatement()) {
        statement.execute("CREATE DATABASE " + databaseName);
      } catch (SQLException exception) {
        throw new IllegalStateException(
            "Could not create isolated PostgreSQL test database", exception);
      }
      jdbcUrl = server.getJdbcUrl().replace("/" + server.getDatabaseName(), "/" + databaseName);
    }

    return jdbcUrl;
  }

  public String getUsername() {
    return "test";
  }

  public String getPassword() {
    return "test";
  }

  private static synchronized PostgreSQLContainer<?> server(final String image) {
    return SERVERS.computeIfAbsent(
        image,
        name -> {
          final PostgreSQLContainer<?> container = new PostgreSQLContainer<>(name);
          container.start();
          return container;
        });
  }
}
