package ar.edu.utn.frvm.typeit.bemol_api.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PlatformAuthenticationPolicyTest {
  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withBean(PlatformAuthenticationPolicy.class);

  @ParameterizedTest
  @CsvSource({
    "dev,false",
    "test,true",
    "staging,true",
    "prod,true",
    "production,true",
    "default,true"
  })
  void configurationDefaultsToPasswordOnlyInDev(final String profile, final boolean required) {
    context
        .withPropertyValues("spring.profiles.active=" + profile)
        .run(
            application -> {
              assertThat(application).hasNotFailed();
              assertThat(application.getBean(PlatformAuthenticationPolicy.class).passkeyRequired())
                  .isEqualTo(required);
            });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void devCanEnableOrDisablePasskeys(final boolean required) {
    context
        .withPropertyValues("spring.profiles.active=dev", "PLATFORM_PASSKEY_REQUIRED=" + required)
        .run(
            application -> {
              assertThat(application).hasNotFailed();
              assertThat(application.getBean(PlatformAuthenticationPolicy.class).passkeyRequired())
                  .isEqualTo(required);
            });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "test",
        "staging",
        "prod",
        "production",
        "default",
        "dev,staging",
        "dev,prod",
        "production,dev"
      })
  void passwordOnlyFailsAtStartupOutsideDev(final String profiles) {
    context
        .withPropertyValues(
            "spring.profiles.active=" + profiles, "app.auth.platform.passkey-required=false")
        .run(
            application ->
                assertThat(application)
                    .hasFailed()
                    .getFailure()
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage(
                        "app.auth.platform.passkey-required solo puede desactivarse en el perfil dev."));
  }
}
