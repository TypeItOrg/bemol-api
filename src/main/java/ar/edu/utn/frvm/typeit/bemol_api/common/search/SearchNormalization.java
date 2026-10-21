package ar.edu.utn.frvm.typeit.bemol_api.common.search;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class SearchNormalization {

  private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

  private SearchNormalization() {}

  public static final String UNACCENT_LOWER_FUNCTION = "unaccent_lower";
  public static final String UNACCENT_LOWER_PATTERN =
      "lower(translate(?1, 'áéíóúÁÉÍÓÚüÜñÑ', 'aeiouAEIOUuUnN'))";
  public static final String POSTGRES_UNACCENT_LOWER_PATTERN = "bemol_normalize_text(?1)";

  public static Expression<String> unaccentLower(
      final CriteriaBuilder builder, final Expression<String> expression) {
    return builder.function(UNACCENT_LOWER_FUNCTION, String.class, expression);
  }

  public static @Nullable String normalizeSearch(final @Nullable String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  public static String normalizeForComparison(final String value) {
    final String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
    return DIACRITICS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
  }

  public static String likeContainsPattern(final String value) {
    return "%" + escapeLike(normalizeForComparison(value)) + "%";
  }

  public static String escapeLike(final String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
