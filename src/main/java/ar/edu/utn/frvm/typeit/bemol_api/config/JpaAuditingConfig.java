package ar.edu.utn.frvm.typeit.bemol_api.config;

import java.time.Clock;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

  @Bean
  @ConditionalOnMissingBean(Clock.class)
  Clock auditingClock() {
    return Clock.systemUTC();
  }

  @Bean
  DateTimeProvider auditingDateTimeProvider(final Clock clock) {
    return () -> Optional.of(clock.instant());
  }
}
