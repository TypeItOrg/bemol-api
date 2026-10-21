package ar.edu.utn.frvm.typeit.bemol_api.config;

import static ar.edu.utn.frvm.typeit.bemol_api.common.time.BusinessDateProvider.BUSINESS_ZONE;

import java.time.Clock;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@Configuration
@ImportAutoConfiguration(ValidationAutoConfiguration.class)
public class ValidationConfig {
  @Bean
  ValidationConfigurationCustomizer validationClock(final Clock clock) {
    return configuration -> configuration.clockProvider(() -> clock.withZone(BUSINESS_ZONE));
  }

  @Bean
  HibernatePropertiesCustomizer persistenceValidator(final LocalValidatorFactoryBean validator) {
    return properties -> properties.put("jakarta.persistence.validation.factory", validator);
  }
}
