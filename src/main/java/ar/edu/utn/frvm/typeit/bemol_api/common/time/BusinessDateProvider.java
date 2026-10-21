package ar.edu.utn.frvm.typeit.bemol_api.common.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BusinessDateProvider {
  public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
  private final Clock clock;

  public LocalDate today() {
    return LocalDate.now(clock.withZone(BUSINESS_ZONE));
  }
}
