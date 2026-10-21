package ar.edu.utn.frvm.typeit.bemol_api.support;

import ar.edu.utn.frvm.typeit.bemol_api.common.time.BusinessDateProvider;
import ar.edu.utn.frvm.typeit.bemol_api.config.JpaAuditingConfig;
import ar.edu.utn.frvm.typeit.bemol_api.config.TimeConfig;
import ar.edu.utn.frvm.typeit.bemol_api.config.ValidationConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration
@Import({
  TimeConfig.class,
  JpaAuditingConfig.class,
  BusinessDateProvider.class,
  ValidationConfig.class
})
public class JpaAuditingTestConfig {}
