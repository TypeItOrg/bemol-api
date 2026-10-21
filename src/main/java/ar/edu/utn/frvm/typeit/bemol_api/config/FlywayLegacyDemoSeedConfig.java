package ar.edu.utn.frvm.typeit.bemol_api.config;

import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.stream.Stream;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
public class FlywayLegacyDemoSeedConfig implements FlywayConfigurationCustomizer {

  @Override
  public void customize(final FluentConfiguration configuration) {
    final var locations =
        Arrays.stream(configuration.getLocations())
            .map(location -> location.getDescriptor())
            .toList();
    if (!locations.contains("classpath:db/dev")) {
      return;
    }

    final var jdbc = new JdbcTemplate(requireNonNull(configuration.getDataSource()));
    if (!Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT to_regclass('public.flyway_schema_history') IS NOT NULL", Boolean.class))) {
      return;
    }
    if (!Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM public.flyway_schema_history WHERE version = ? AND success)",
            Boolean.class,
            "20260930190000"))) {
      return;
    }

    // Resolve the unchanged script for existing history only; never seed a new database at startup.
    configuration.locations(
        Stream.concat(locations.stream(), Stream.of("classpath:db/legacy/enrollment-demo"))
            .toArray(String[]::new));
  }
}
