package ar.edu.utn.frvm.typeit.bemol_api.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchQueryTest {

  @Test
  void normalizesAccentsAndBuildsAnOrderIndependentPrefixQuery() {
    final SearchQuery query = SearchQuery.from("  Matías, Delgado ");

    assertThat(query.normalized()).isEqualTo("matias delgado");
    assertThat(query.tsQuery()).isEqualTo("matias:* & delgado:*");
  }

  @Test
  void removesDuplicateTokens() {
    assertThat(SearchQuery.from("Ana Ana").tsQuery()).isEqualTo("ana:*");
  }

  @Test
  void keepsCharactersOutsideTheDatabaseSpanishNormalizationMapping() {
    final SearchQuery query = SearchQuery.from("À la carte");

    assertThat(query.normalized()).isEqualTo("à la carte");
    assertThat(query.tsQuery()).isEqualTo("à:* & la:* & carte:*");
  }
}
