package ar.edu.utn.frvm.typeit.bemol_api.academic.validation;

import org.jspecify.annotations.Nullable;

public final class AcademicNameNormalizer {

  private AcademicNameNormalizer() {}

  public static String display(final String value) {
    return value.trim().replaceAll("\\s+", " ");
  }

  public static @Nullable String search(final @Nullable String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
