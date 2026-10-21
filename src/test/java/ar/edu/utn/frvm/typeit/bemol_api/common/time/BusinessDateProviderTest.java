package ar.edu.utn.frvm.typeit.bemol_api.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BusinessDateProviderTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-09T02:00:00Z"), ZoneOffset.UTC);

  @Test
  @DisplayName("Should use the Argentina calendar date for the current instant")
  void today_usesArgentinaCalendarDate() {
    assertThat(new BusinessDateProvider(CLOCK).today()).isEqualTo(LocalDate.of(2026, 9, 8));
  }
}
