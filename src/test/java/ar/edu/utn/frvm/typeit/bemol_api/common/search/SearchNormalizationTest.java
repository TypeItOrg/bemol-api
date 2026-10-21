package ar.edu.utn.frvm.typeit.bemol_api.common.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchNormalizationTest {

  @Test
  void normalizesBlankSearchAsNoFilter() {
    assertThat(SearchNormalization.normalizeSearch(null)).isNull();
    assertThat(SearchNormalization.normalizeSearch("   ")).isNull();
    assertThat(SearchNormalization.normalizeSearch("  activo ")).isEqualTo("activo");
  }

  @Test
  void normalizesComparisonTextWithoutCaseOrDiacritics() {
    assertThat(SearchNormalization.normalizeForComparison("Áño Ñandú")).isEqualTo("ano nandu");
  }

  @Test
  void escapesLikeWildcardsInContainsPatterns() {
    assertThat(SearchNormalization.likeContainsPattern("50%_\\")).isEqualTo("%50\\%\\_\\\\%");
  }
}
