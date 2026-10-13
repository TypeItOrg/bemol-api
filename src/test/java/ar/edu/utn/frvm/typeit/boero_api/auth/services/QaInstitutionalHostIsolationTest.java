package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ar.edu.utn.frvm.typeit.boero_api.auth.config.*;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.*;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.*;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.*;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.*;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.*;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData;
import java.time.*;
import java.util.*;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.*;

class QaInstitutionalHostIsolationTest {
  InstitutionRepository institutions;
  InstitutionalHostContext host;
  Institution tenant;
  Institution other;
  User user;

  @BeforeEach
  void setup() {
    institutions = mock(InstitutionRepository.class);
    tenant = Institution.builder().id(UUID.randomUUID()).name("A").slug("old-a").build();
    tenant.changePublicSubdomain("cboero");
    other = Institution.builder().id(UUID.randomUUID()).name("B").slug("old-b").build();
    user =
        User.builder()
            .id(UUID.randomUUID())
            .institution(other)
            .person(InstitutionalTestData.person(other, "87654321"))
            .password("encoded-password")
            .build();
    host =
        new InstitutionalHostContext(
            new FrontendPublicProperties(
                "https://testing.typeit.com.ar:9443", "testing.typeit.com.ar"),
            institutions);
    lenient()
        .when(institutions.findByPublicSubdomainAndActiveTrue("cboero"))
        .thenReturn(Optional.of(tenant));
    bind("cboero.testing.typeit.com.ar");
  }

  void bind(final @Nullable String hostname) {
    final var request = new MockHttpServletRequest("POST", "/api/v1/auth/login/identify");
    if (hostname != null) {
      request.addHeader(InstitutionalHostContext.HEADER, hostname);
    }
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  @AfterEach
  void cleanup() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void A03_payloadContext_preAuthIdentityRejectedBeforeLookup() {
    final var users = mock(UserRepository.class);
    final var identify =
        new IdentifyLoginUseCase(
            host, users, mock(PasskeyCredentialRepository.class), mock(LoginAttemptService.class));
    assertThatThrownBy(() -> identify.execute(new IdentifyLoginRequest(other.getId(), "87654321")))
        .isInstanceOf(InstitutionalContextMismatchException.class);
    final var recovery =
        new RequestInstitutionalPasswordRecoveryUseCase(
            host,
            Clock.systemUTC(),
            users,
            mock(InstitutionalPasswordResetTokenRepository.class),
            mock(ApplicationEventPublisher.class),
            new PasswordRecoveryProperties("http://localhost:3000", Duration.ofMinutes(30)));
    assertThatThrownBy(
            () -> recovery.execute(new PasswordRecoveryRequest("87654321", other.getId())))
        .isInstanceOf(InstitutionalContextMismatchException.class);
    verifyNoInteractions(users);
  }

  @Test
  void A03_payloadContext_registrationResendAndChangeEmailBeforeEffects() {
    final var users = mock(UserRepository.class);
    final var registration =
        new RegisterUserUseCase(
            host,
            mock(InstitutionalEmailVerificationUseCase.class),
            users,
            institutions,
            mock(ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository.class),
            mock(PasswordEncoder.class),
            mock(jakarta.validation.Validator.class),
            mock(
                ar.edu.utn.frvm.typeit.boero_api.authorization.services
                    .AssignPersonSystemRoleUseCase.class),
            mock(ar.edu.utn.frvm.typeit.boero_api.common.time.BusinessDateProvider.class));
    assertThatThrownBy(
            () ->
                registration.execute(
                    new RegisterRequest(
                        "Ana",
                        "Garcia",
                        LocalDate.of(1990, 1, 1),
                        "87654321",
                        "ana@example.com",
                        "long-password",
                        other.getId(),
                        false)))
        .isInstanceOf(InstitutionalContextMismatchException.class);
    final var verification =
        new InstitutionalEmailVerificationUseCase(
            host,
            users,
            mock(InstitutionalEmailVerificationTokenRepository.class),
            mock(InstitutionalPasswordResetTokenRepository.class),
            mock(PasswordEncoder.class),
            mock(ApplicationEventPublisher.class),
            new EmailVerificationProperties(
                "http://localhost:3000", Duration.ofHours(24), Duration.ofMinutes(1)),
            Clock.systemUTC());
    assertThatThrownBy(
            () ->
                verification.resend(new ResendEmailVerificationRequest("87654321", other.getId())))
        .isInstanceOf(InstitutionalContextMismatchException.class);
    assertThatThrownBy(
            () ->
                verification.changeEmail(
                    new ChangePendingEmailRequest(
                        other.getId(), "87654321", "password", "other@example.com")))
        .isInstanceOf(InstitutionalContextMismatchException.class);
    verifyNoInteractions(users);
  }

  @Test
  void A03_sessionContext_jwtPrincipalRejectedBeforeControllerButPlatformUnaffected()
      throws Exception {
    final var resolver = mock(org.springframework.web.servlet.HandlerExceptionResolver.class);
    final var filter =
        new ar.edu.utn.frvm.typeit.boero_api.auth.filters.InstitutionalHostFilter(host, resolver);
    final var request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
    request.addHeader(InstitutionalHostContext.HEADER, "cboero.testing.typeit.com.ar");
    final var response = new org.springframework.mock.web.MockHttpServletResponse();
    final var chain = mock(jakarta.servlet.FilterChain.class);
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(
            new org.springframework.security.authentication.TestingAuthenticationToken(
                ar.edu.utn.frvm.typeit.boero_api.support.AuthTestData.institutionalPrincipal(
                    user.getId(), other.getId()),
                ""));
    try {
      filter.doFilter(request, response, chain);
      verifyNoInteractions(chain);
      verify(resolver)
          .resolveException(
              eq(request),
              eq(response),
              isNull(),
              isA(InstitutionalContextMismatchException.class));
      final var platformRequest = new MockHttpServletRequest("POST", "/api/v1/admin/auth/login");
      platformRequest.addHeader(InstitutionalHostContext.HEADER, "arbitrary-malicious-host");
      filter.doFilter(platformRequest, response, chain);
      verify(chain).doFilter(platformRequest, response);
    } finally {
      org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
  }

  @Test
  void A03_untrustedHeader_absentGenericAndCanonicalPortAreCompatible() {
    assertThat(host.current().orElseThrow().origin())
        .isEqualTo("https://cboero.testing.typeit.com.ar:9443");
    host.requireInstitution(tenant.getId());
    bind("cboero.testing.typeit.com.ar:9443");
    host.requireInstitution(tenant.getId());
    bind(null);
    host.requireInstitution(other.getId());
    assertThat(host.current()).isEmpty();
    bind("testing.typeit.com.ar");
    host.requireInstitution(other.getId());
    assertThat(host.current()).isEmpty();
  }

  @ParameterizedTest(name = "[A03.untrusted-header] reject host {index}: {0}")
  @ValueSource(
      strings = {
        "other.testing.typeit.com.ar.evil.test",
        "https://cboero.testing.typeit.com.ar",
        "cboero.testing.typeit.com.ar/path",
        "x@y.testing.typeit.com.ar",
        "cboero.testing.typeit.com.ar:8443",
        "nested.cboero.testing.typeit.com.ar",
        "www.testing.typeit.com.ar",
        "cboero.testing.typeit.com.ar,evil.test"
      })
  void A03_untrustedHeader_rejectsArbitraryOrMalformedHosts(final String hostname) {
    bind(hostname);
    assertThatThrownBy(host::current).isInstanceOf(InvalidInstitutionalHostException.class);
  }

  @Test
  void A03_untrustedHeader_unknownAndInactiveAreNotFound() {
    bind("unknown.testing.typeit.com.ar");
    assertThatThrownBy(host::current).isInstanceOf(InstitutionNotFoundException.class);
  }

  @Test
  void A03_attemptContext_foreignAttemptNotConsumed() {
    final var redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    final ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    final String value =
        user.getId() + "|" + other.getId() + "|true|" + Instant.now().toEpochMilli();
    when(values.get(anyString())).thenReturn(value);
    final var attempts = new LoginAttemptService(host, redis, properties());
    assertThatThrownBy(() -> attempts.resolve("attempt"))
        .isInstanceOf(InstitutionalContextMismatchException.class);
    assertThatThrownBy(() -> attempts.claim("attempt"))
        .isInstanceOf(InstitutionalContextMismatchException.class);
    verify(values, never()).getAndDelete(anyString());
    verify(redis, never()).delete(anyString());
  }

  @Test
  void A06_invalidTokens_foreignVerificationAndResetNoEffects() {
    final var users = mock(UserRepository.class);
    final var verificationTokens = mock(InstitutionalEmailVerificationTokenRepository.class);
    final var resetTokens = mock(InstitutionalPasswordResetTokenRepository.class);
    final String hash = RequestInstitutionalPasswordRecoveryUseCase.hash("foreign-token");
    when(users.findForEmailVerificationById(user.getId())).thenReturn(Optional.of(user));
    when(verificationTokens.findUserIdByTokenHash(hash)).thenReturn(Optional.of(user.getId()));
    when(resetTokens.findUserIdByTokenHash(hash)).thenReturn(Optional.of(user.getId()));
    final var encoder = mock(PasswordEncoder.class);
    final var events = mock(ApplicationEventPublisher.class);
    final var sessions = mock(SessionRevocationService.class);
    final var verification =
        new InstitutionalEmailVerificationUseCase(
            host,
            users,
            verificationTokens,
            resetTokens,
            encoder,
            events,
            new EmailVerificationProperties(
                "http://localhost:3000", Duration.ofHours(24), Duration.ofMinutes(1)),
            Clock.systemUTC());
    assertThatThrownBy(
            () -> verification.confirm(new ConfirmEmailVerificationRequest("foreign-token")))
        .isInstanceOf(InstitutionalContextMismatchException.class);
    final var reset =
        new ResetInstitutionalPasswordUseCase(
            host, Clock.systemUTC(), users, resetTokens, encoder, sessions);
    assertThatThrownBy(
            () ->
                reset.execute(
                    new ResetPasswordRequest("foreign-token", "new-password", "new-password")))
        .isInstanceOf(InstitutionalContextMismatchException.class);
    verify(verificationTokens, never()).findByTokenHash(anyString());
    verify(resetTokens, never()).findByTokenHashForUpdate(anyString());
    verifyNoInteractions(encoder, events, sessions);
  }

  @Test
  void A03_sessionContext_refreshRejectsForeignOwnerBeforeReplayOrRevocation() {
    final var refreshTokens = mock(RefreshTokenRepository.class);
    final var sessions = mock(UserSessionRepository.class);
    final var users = mock(UserRepository.class);
    final var session =
        UserSession.builder()
            .id(UUID.randomUUID())
            .userId(user.getId())
            .startedAt(Instant.now())
            .build();
    final var refresh =
        RefreshToken.builder()
            .sessionId(session.getId())
            .tokenHash(JwtService.hashToken("foreign-refresh"))
            .familyId("family")
            .expiresAt(Instant.now().plusSeconds(600))
            .revoked(true)
            .build();
    when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.of(refresh));
    when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
    when(users.findWithPersonAndInstitutionById(user.getId())).thenReturn(Optional.of(user));
    final var replay = mock(RefreshReplayCache.class);
    final var revoke = mock(SessionRevocationService.class);
    final var service =
        new RefreshTokenUseCase(
            host,
            Clock.systemUTC(),
            refreshTokens,
            sessions,
            users,
            mock(JwtService.class),
            mock(JwtProperties.class),
            mock(ar.edu.utn.frvm.typeit.boero_api.authorization.services.AuthorityResolver.class),
            replay,
            mock(RefreshTokenGenerator.class),
            revoke);
    assertThatThrownBy(() -> service.execute(new RefreshTokenRequest("foreign-refresh")))
        .isInstanceOf(InstitutionalContextMismatchException.class);
    verifyNoInteractions(replay, revoke);
    verify(refreshTokens, never()).revokeByFamilyId(anyString());
    verify(refreshTokens, never()).save(any());
  }

  @Test
  void A04_untrustedOrigin_dynamicAllowlistHasOnlyResolvedActiveOrigin() {
    final var rp =
        org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity.builder()
            .id("testing.typeit.com.ar")
            .name("Boero")
            .build();
    final var operations =
        new TrustedWebAuthnOperations(
            mock(PasskeyUserEntityRepository.class),
            mock(PasskeyUserCredentialRepository.class),
            rp,
            properties(),
            host);
    assertThat(operations.trustedOrigins())
        .containsExactlyInAnyOrder("https://cboero.testing.typeit.com.ar:9443")
        .doesNotContain("https://other.testing.typeit.com.ar:9443");
    bind(null);
    assertThat(operations.trustedOrigins()).containsExactly("https://testing.typeit.com.ar:9443");
    when(institutions.findByPublicSubdomainAndActiveTrue("cboero")).thenReturn(Optional.empty());
    bind("cboero.testing.typeit.com.ar");
    assertThatThrownBy(operations::trustedOrigins).isInstanceOf(InstitutionNotFoundException.class);
  }

  @Test
  void A04_untrustedOrigin_configuredSiblingCannotOverrideCurrentHost() {
    final var configured =
        new WebAuthnProperties(
            "testing.typeit.com.ar",
            "Boero",
            List.of(
                "https://testing.typeit.com.ar:9443", "https://sibling.testing.typeit.com.ar:9443"),
            Duration.ofMinutes(5),
            Duration.ofMinutes(5),
            10,
            Duration.ofMinutes(5));
    final var rp =
        org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity.builder()
            .id("testing.typeit.com.ar")
            .name("Boero")
            .build();
    final var operations =
        new TrustedWebAuthnOperations(
            mock(PasskeyUserEntityRepository.class),
            mock(PasskeyUserCredentialRepository.class),
            rp,
            configured,
            host);
    assertThat(operations.trustedOrigins())
        .containsExactly("https://cboero.testing.typeit.com.ar:9443");
    bind("testing.typeit.com.ar");
    assertThat(operations.trustedOrigins()).containsExactly("https://testing.typeit.com.ar:9443");
    bind(null);
    assertThat(operations.trustedOrigins())
        .containsExactlyInAnyOrder(
            "https://testing.typeit.com.ar:9443", "https://sibling.testing.typeit.com.ar:9443");
  }

  @Test
  void A04_passkeyGeneralBranded_realOptionsPreserveRpAndUserVerification() {
    final var users = mock(PasskeyUserEntityRepository.class);
    final var credentials = mock(PasskeyUserCredentialRepository.class);
    final var handle = org.springframework.security.web.webauthn.api.Bytes.random();
    final var identity =
        org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity
            .builder()
            .id(handle)
            .name("user")
            .displayName("User")
            .build();
    when(users.findByUsername("user")).thenReturn(identity);
    when(credentials.findByUserId(handle)).thenReturn(List.of());
    final var rp =
        org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity.builder()
            .id("testing.typeit.com.ar")
            .name("Boero")
            .build();
    final var operations =
        new TrustedWebAuthnOperations(users, credentials, rp, properties(), host);
    for (final String hostname :
        new String[] {"cboero.testing.typeit.com.ar", "testing.typeit.com.ar"}) {
      bind(hostname);
      final var auth =
          org.springframework.security.authentication.UsernamePasswordAuthenticationToken
              .authenticated("user", null, List.of());
      final var creation =
          operations.createPublicKeyCredentialCreationOptions(
              new org.springframework.security.web.webauthn.management
                  .ImmutablePublicKeyCredentialCreationOptionsRequest(auth));
      assertThat(creation.getRp().getId()).isEqualTo("testing.typeit.com.ar");
      assertThat(creation.getAuthenticatorSelection().getUserVerification())
          .isEqualTo(
              org.springframework.security.web.webauthn.api.UserVerificationRequirement.REQUIRED);
      final var request =
          operations.createCredentialRequestOptions(
              new org.springframework.security.web.webauthn.management
                  .ImmutablePublicKeyCredentialRequestOptionsRequest(auth));
      assertThat(request.getRpId()).isEqualTo("testing.typeit.com.ar");
      assertThat(request.getUserVerification())
          .isEqualTo(
              org.springframework.security.web.webauthn.api.UserVerificationRequirement.REQUIRED);
    }
  }

  static WebAuthnProperties properties() {
    return new WebAuthnProperties(
        "testing.typeit.com.ar",
        "Boero",
        List.of("https://testing.typeit.com.ar:9443"),
        Duration.ofMinutes(5),
        Duration.ofMinutes(5),
        10,
        Duration.ofMinutes(5));
  }
}
