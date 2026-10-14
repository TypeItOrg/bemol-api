package ar.edu.utn.frvm.typeit.boero_api.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MinimumAgeValidatorTest {

  private static final ZoneId ARGENTINA_TIME_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-09T02:00:00Z"), ZoneOffset.UTC);

  private static Validator validator;
  private static ValidatorFactory factory;

  @BeforeAll
  static void setUpValidator() {
    factory =
        Validation.byDefaultProvider()
            .configure()
            .clockProvider(() -> CLOCK)
            .buildValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeValidatorFactory() {
    factory.close();
  }

  @Test
  void acceptsExactMinimumAge() {
    final LocalDate birthDate = LocalDate.now(CLOCK.withZone(ARGENTINA_TIME_ZONE)).minusYears(3);

    assertThat(validator.validate(new BirthDateValue(birthDate))).isEmpty();
  }

  @Test
  void rejectsOneDayBeforeMinimumAge() {
    final LocalDate birthDate =
        LocalDate.now(CLOCK.withZone(ARGENTINA_TIME_ZONE)).minusYears(3).plusDays(1);

    assertThat(validator.validate(new BirthDateValue(birthDate)))
        .singleElement()
        .satisfies(
            violation ->
                assertThat(violation.getMessage())
                    .isEqualTo("La persona debe tener al menos 3 años."));
  }

  private record BirthDateValue(@MinimumAge(3) LocalDate birthDate) {}
}
