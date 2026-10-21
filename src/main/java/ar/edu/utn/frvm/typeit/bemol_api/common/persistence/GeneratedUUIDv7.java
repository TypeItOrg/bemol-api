package ar.edu.utn.frvm.typeit.bemol_api.common.persistence;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.hibernate.annotations.IdGeneratorType;

@IdGeneratorType(GeneratedUUIDv7Generator.class)
@Retention(RUNTIME)
@Target({FIELD, METHOD})
public @interface GeneratedUUIDv7 {}
