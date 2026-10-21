package ar.edu.utn.frvm.typeit.bemol_api.auth.config;

import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.email-verification")
public record EmailVerificationProperties(
    String frontendUrl, Duration tokenExpiration, Duration resendInterval) {
  public EmailVerificationProperties {
    if (tokenExpiration == null
        || tokenExpiration.isNegative()
        || tokenExpiration.isZero()
        || resendInterval == null
        || resendInterval.isNegative()
        || resendInterval.isZero()) {
      throw new IllegalArgumentException(AuthMessages.EMAIL_VERIFICATION_DURATION_INVALID);
    }
  }
}
