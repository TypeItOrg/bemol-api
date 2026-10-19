package ar.edu.utn.frvm.typeit.boero_api.auth.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ar.edu.utn.frvm.typeit.boero_api.auth.services.StagingAutoLoginUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class StagingAutoLoginControllerTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(StagingAutoLoginController.class)
          .withBean(StagingAutoLoginUseCase.class, () -> mock(StagingAutoLoginUseCase.class));

  @Test
  void registersOnlyWithExplicitStagingOptIn() {
    runner
        .withPropertyValues("spring.profiles.active=staging", "app.auto-login.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(StagingAutoLoginController.class));
  }

  @Test
  void remainsDisabledByDefault() {
    runner
        .withPropertyValues("spring.profiles.active=staging")
        .run(context -> assertThat(context).doesNotHaveBean(StagingAutoLoginController.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"prod", "staging,prod"})
  void refusesProductionEvenWithTheFlagEnabled(final String profiles) {
    runner
        .withPropertyValues("spring.profiles.active=" + profiles, "app.auto-login.enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean(StagingAutoLoginController.class));
  }
}
