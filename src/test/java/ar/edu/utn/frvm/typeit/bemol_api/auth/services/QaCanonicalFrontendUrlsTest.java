package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.*;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.FrontendPublicProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.events.*;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QaCanonicalFrontendUrlsTest {
  @ParameterizedTest(name = "[A06.canonical-email] canonical base {0}")
  @ValueSource(strings = {"testing.typeit.com.ar", "staging.typeit.com.ar"})
  void A06_canonicalEmail_trustedQaStagingAndGenericBases(final String domain) {
    final var urls =
        new FrontendAccessUrls(new FrontendPublicProperties("https://" + domain + ":9443", domain));
    assertThat(urls.tokenUrl("cboero", "/auth/email-verification/confirm", "token-123"))
        .isEqualTo(
            "https://cboero." + domain + ":9443/auth/email-verification/confirm?token=token-123");
    assertThat(urls.tokenUrl("cboero", "/auth/password-recovery/reset", "token-123"))
        .isEqualTo(
            "https://cboero." + domain + ":9443/auth/password-recovery/reset?token=token-123");
    assertThat(urls.tokenUrl(null, "/auth/password-recovery/reset", "token-123"))
        .isEqualTo("https://" + domain + ":9443/auth/password-recovery/reset?token=token-123");
    assertThat(urls.brandLogoUrl()).isEqualTo("https://" + domain + ":9443/brand/logo-email.png");
  }

  @Test
  void A06_canonicalEmail_asyncEventsSnapshotAccessIdentity() {
    final var institution =
        Institution.builder().id(UUID.randomUUID()).name("Institution").slug("legacy").build();
    institution.changePublicSubdomain("cboero");
    final var user =
        User.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .person(InstitutionalTestData.person(institution, "12345678"))
            .password("password")
            .build();
    final var verification = InstitutionalEmailVerificationRequested.from(user, "token");
    final var recovery = InstitutionalPasswordRecoveryRequested.from(user, "token");
    institution.changePublicSubdomain("new-name");
    assertThat(verification.publicSubdomain()).isEqualTo("cboero");
    assertThat(recovery.publicSubdomain()).isEqualTo("cboero");
  }

  @Test
  void A06_canonicalEmail_emptyPublicUrlFallsBackLegacyAndDomainDisabled() {
    final var urls =
        new FrontendAccessUrls(new FrontendPublicProperties("", "", "https://legacy.example.com"));
    assertThat(urls.base("cboero")).isEqualTo("https://legacy.example.com");
    assertThat(new FrontendAccessUrls(new FrontendPublicProperties("", "")).base(null))
        .isEqualTo("http://localhost:3000");
  }
}
