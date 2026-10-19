package ar.edu.utn.frvm.typeit.boero_api.auth.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ar.edu.utn.frvm.typeit.boero_api.auth.services.AutoLoginUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AutoLoginControllerTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(AutoLoginController.class)
          .withBean(AutoLoginUseCase.class, () -> mock(AutoLoginUseCase.class));

  @ParameterizedTest
  @ValueSource(strings = {"dev", "qa", "staging", "test"})
  void registersWithExplicitNonProductionOptIn(final String profiles) {
    runner
        .withPropertyValues("spring.profiles.active=" + profiles, "app.auto-login.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(AutoLoginController.class));
  }

  @Test
  void remainsDisabledByDefault() {
    runner
        .withPropertyValues("spring.profiles.active=staging")
        .run(context -> assertThat(context).doesNotHaveBean(AutoLoginController.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"prod", "production", "staging,prod", "qa,production"})
  void refusesProductionEvenWithTheFlagEnabled(final String profiles) {
    runner
        .withPropertyValues("spring.profiles.active=" + profiles, "app.auto-login.enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean(AutoLoginController.class));
  }
}
