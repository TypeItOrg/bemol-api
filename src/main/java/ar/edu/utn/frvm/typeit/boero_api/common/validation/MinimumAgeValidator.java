package ar.edu.utn.frvm.typeit.boero_api.common.validation;

import static ar.edu.utn.frvm.typeit.boero_api.common.time.BusinessDateProvider.BUSINESS_ZONE;

import jakarta.validation.ClockProvider;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Clock;
import java.time.LocalDate;

public class MinimumAgeValidator implements ConstraintValidator<MinimumAge, LocalDate> {

  private int minimumAge;

  @Override
  public void initialize(final MinimumAge constraintAnnotation) {
    minimumAge = constraintAnnotation.value();
  }

  @Override
  public boolean isValid(final LocalDate value, final ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }

    final Clock clock = resolveClock(context);
    final LocalDate latestAllowedBirthDate =
        LocalDate.now(clock.withZone(BUSINESS_ZONE)).minusYears(minimumAge);
    return !value.isAfter(latestAllowedBirthDate);
  }

  private Clock resolveClock(final ConstraintValidatorContext context) {
    if (context != null) {
      final ClockProvider provider = context.getClockProvider();
      if (provider != null) {
        final Clock clock = provider.getClock();
        if (clock != null) {
          return clock;
        }
      }
    }
    return Clock.system(BUSINESS_ZONE);
  }
}
