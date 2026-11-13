package ar.edu.utn.frvm.typeit.bemol_api;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import java.io.IOException;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.flyway.autoconfigure.FlywayProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

@IntegrationTest
class FlywayPolicyIntegrationTest {

  @ParameterizedTest
  @ValueSource(strings = {"dev", "staging", "prod"})
  @DisplayName("Should enable late migrations in every profile without disabling validation")
  void shouldKeepSharedPolicyAcrossProfiles(final String profile) throws IOException {
    final var settings = settings(profile);

    assertThat(settings.isOutOfOrder()).isTrue();
    assertThat(settings.isValidateOnMigrate()).isTrue();
    assertThat(settings.isValidateMigrationNaming()).isTrue();
    assertThat(settings.getIgnoreMigrationPatterns()).doesNotContain("*:ignored", "*:pending");
  }

  private static FlywayProperties settings(final String profile) throws IOException {
    // Test resources shadow application.properties; read the actual deployment configuration.
    final Properties properties =
        PropertiesLoaderUtils.loadProperties(
            new FileSystemResource("src/main/resources/application.properties"));
    properties.putAll(
        PropertiesLoaderUtils.loadProperties(
            new FileSystemResource("src/main/resources/application-" + profile + ".properties")));
    return new Binder(new MapConfigurationPropertySource(properties))
        .bind("spring.flyway", Bindable.of(FlywayProperties.class))
        .get();
  }
}
