package ar.edu.utn.frvm.typeit.bemol_api.auth.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
class WebAuthnDeploymentGuardTest {

  @Mock private Environment environment;

  private static final ApplicationArguments NO_ARGS = new DefaultApplicationArguments();

  @Test
  @DisplayName("Should reject localhost RP ID on prod")
  void run_rejectsLocalhostRpIdOnProd() {
    when(environment.getActiveProfiles()).thenReturn(new String[] {"prod"});
    final WebAuthnDeploymentGuard guard =
        new WebAuthnDeploymentGuard(localhostProperties(), environment);

    assertThatThrownBy(() -> guard.run(NO_ARGS)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("Should reject localhost origins on staging")
  void run_rejectsLocalhostOriginsOnStaging() {
    when(environment.getActiveProfiles()).thenReturn(new String[] {"staging"});
    final WebAuthnDeploymentGuard guard =
        new WebAuthnDeploymentGuard(localhostProperties(), environment);

    assertThatThrownBy(() -> guard.run(NO_ARGS)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("Should reject localhost config without an explicit profile")
  void run_rejectsLocalhostWithoutProfile() {
    when(environment.getActiveProfiles()).thenReturn(new String[0]);
    final WebAuthnDeploymentGuard guard =
        new WebAuthnDeploymentGuard(localhostProperties(), environment);

    assertThatThrownBy(() -> guard.run(NO_ARGS)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("Should allow localhost config on dev and test")
  void run_allowsLocalhostOnDevAndTest() {
    final WebAuthnProperties properties = localhostProperties();

    when(environment.getActiveProfiles()).thenReturn(new String[] {"dev"});
    assertThatCode(() -> new WebAuthnDeploymentGuard(properties, environment).run(NO_ARGS))
        .doesNotThrowAnyException();

    when(environment.getActiveProfiles()).thenReturn(new String[] {"test"});
    assertThatCode(() -> new WebAuthnDeploymentGuard(properties, environment).run(NO_ARGS))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Should allow explicit production config on prod")
  void run_allowsExplicitConfigOnProd() {
    when(environment.getActiveProfiles()).thenReturn(new String[] {"prod"});
    final WebAuthnProperties properties =
        new WebAuthnProperties(
            "universidad.edu.ar",
            "Boero",
            List.of("https://universidad.edu.ar"),
            Duration.ofMinutes(5),
            Duration.ofMinutes(5),
            10,
            Duration.ofMinutes(5));
    final WebAuthnDeploymentGuard guard = new WebAuthnDeploymentGuard(properties, environment);

    assertThatCode(() -> guard.run(NO_ARGS)).doesNotThrowAnyException();
  }

  private static WebAuthnProperties localhostProperties() {
    return new WebAuthnProperties(
        "localhost",
        "Boero",
        List.of("http://localhost:3000"),
        Duration.ofMinutes(5),
        Duration.ofMinutes(5),
        10,
        Duration.ofMinutes(5));
  }
}
