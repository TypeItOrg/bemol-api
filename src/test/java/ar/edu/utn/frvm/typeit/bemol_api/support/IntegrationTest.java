package ar.edu.utn.frvm.typeit.bemol_api.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag(IntegrationTest.TAG)
public @interface IntegrationTest {

  String TAG = "integration";
}
