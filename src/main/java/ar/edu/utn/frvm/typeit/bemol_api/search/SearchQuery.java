package ar.edu.utn.frvm.typeit.bemol_api.search;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record SearchQuery(String normalized, String tsQuery) {

  private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");

  public static SearchQuery from(final String value) {
    final String normalized = normalize(value);
    final String tsQuery =
        Arrays.stream(normalized.split(" "))
            .filter(token -> !token.isBlank())
            .distinct()
            .map(token -> token + ":*")
            .collect(Collectors.joining(" & "));
    if (tsQuery.isEmpty()) {
      throw new IllegalArgumentException(SearchMessages.INVALID_QUERY);
    }
    return new SearchQuery(normalized, tsQuery);
  }

  private static String normalize(final String value) {
    final String withoutSpanishDiacritics =
        value
            .toLowerCase(Locale.ROOT)
            .replace('á', 'a')
            .replace('é', 'e')
            .replace('í', 'i')
            .replace('ó', 'o')
            .replace('ú', 'u')
            .replace('ü', 'u')
            .replace('ñ', 'n');
    return NON_ALPHANUMERIC.matcher(withoutSpanishDiacritics).replaceAll(" ").trim();
  }
}
