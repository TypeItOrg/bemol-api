package ar.edu.utn.frvm.typeit.bemol_api.institutional.validation;

public final class InstitutionFieldConstraints {

  public static final int SLUG_MAX = 100;
  public static final String SLUG_PATTERN = "^[a-z0-9]+(?:-[a-z0-9]+)*$";
}
