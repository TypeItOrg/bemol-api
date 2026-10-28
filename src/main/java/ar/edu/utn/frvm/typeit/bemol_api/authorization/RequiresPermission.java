package ar.edu.utn.frvm.typeit.bemol_api.authorization;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
  PermissionCode value();

  boolean actor() default false;
}
