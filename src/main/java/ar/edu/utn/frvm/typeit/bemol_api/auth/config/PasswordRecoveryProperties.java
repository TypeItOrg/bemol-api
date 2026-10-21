package ar.edu.utn.frvm.typeit.bemol_api.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.password-recovery")
public record PasswordRecoveryProperties(String frontendUrl, Duration tokenExpiration) {}
