package ar.edu.utn.frvm.typeit.bemol_api.config;

import ar.edu.utn.frvm.typeit.bemol_api.common.search.SearchNormalization;
import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.dialect.PostgreSQLDialect;

public class SearchFunctions implements FunctionContributor {

  @Override
  public void contributeFunctions(final FunctionContributions contributions) {
    final String pattern =
        contributions.getDialect() instanceof PostgreSQLDialect
            ? SearchNormalization.POSTGRES_UNACCENT_LOWER_PATTERN
            : SearchNormalization.UNACCENT_LOWER_PATTERN;
    contributions
        .getFunctionRegistry()
        .registerPattern(SearchNormalization.UNACCENT_LOWER_FUNCTION, pattern);
  }
}
