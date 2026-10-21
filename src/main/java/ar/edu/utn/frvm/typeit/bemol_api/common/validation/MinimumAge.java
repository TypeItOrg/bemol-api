package ar.edu.utn.frvm.typeit.bemol_api.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = MinimumAgeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MinimumAge {

  String message() default ValidationMessages.MINIMUM_AGE;

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  int value();
}
