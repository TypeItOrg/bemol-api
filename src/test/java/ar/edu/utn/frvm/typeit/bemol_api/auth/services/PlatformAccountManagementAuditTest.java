package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.audit.AuthAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformEmailChangeTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformSessionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.UpdateOwnPlatformProfileRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PlatformAccountManagementAuditTest {
  @Mock private PlatformAccountRepository accounts;
  @Mock private PlatformEmailChangeTokenRepository tokens;
  @Mock private PlatformSessionRepository sessions;
  @Mock private PasswordEncoder passwords;
  @Mock private PlatformPasskeyStateStore state;
  @Mock private AccountActionRateLimiter limiter;
  @Mock private SessionRevocationService revocation;
  @Mock private AuditEventRecorder audit;
  @Mock private ApplicationEventPublisher events;
  @Mock private Clock clock;
  @InjectMocks private PlatformAccountManagementUseCase useCase;
  private PlatformAccount account;
  private JwtAuthenticatedPlatformAccount principal;

  @BeforeEach
  void prepare() {
    account =
        PlatformAccount.builder()
            .id(UUID.randomUUID())
            .name("Ana")
            .lastName("Pérez")
            .email("private@example.test")
            .password("secret-hash")
            .build();
    principal =
        JwtAuthenticatedPlatformAccount.builder()
            .platformAccountId(account.getId())
            .sessionId(UUID.randomUUID())
            .build();
    when(accounts.findWithLockById(account.getId())).thenReturn(Optional.of(account));
    when(sessions.existsByIdAndActiveTrue(principal.sessionId())).thenReturn(true);
  }

  @Test
  void unchangedProfileDoesNotCreateAnAuditEvent() {
    useCase.updateProfile(principal, new UpdateOwnPlatformProfileRequest(" Ana ", " Pérez "));

    verify(accounts).saveAndFlush(account);
    verifyNoInteractions(audit, revocation, events);
  }

  @Test
  void profileRecordsActualChangesAndEntityLabelsWithoutEmailOrCredentials() {
    useCase.updateProfile(principal, new UpdateOwnPlatformProfileRequest("María", "Pérez"));

    final ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.captor();
    verify(audit)
        .recordCurrentActor(
            isNull(),
            isNull(),
            eq(AuthAuditAction.PLATFORM_ACCOUNT_UPDATED),
            eq(PlatformAccount.AUDIT_TYPE),
            eq(account.getId()),
            eq(AuditOrigin.SELF_SERVICE),
            details.capture());
    assertThat(details.getValue().get("changedFields")).isEqualTo(List.of("firstName"));
    assertThat(details.getValue().get("fieldLabels")).isEqualTo(account.auditSnapshot().labels());
    assertThat(details.getValue().toString())
        .contains("María")
        .doesNotContain("private@example.test", "secret-hash");
  }

  @Test
  void emailRequestIsRecordedWithoutWaitingForAStateDifferenceOrPersistingItsValue() {
    when(clock.instant()).thenReturn(Instant.parse("2026-11-11T12:00:00Z"));

    useCase.requestEmailChange(principal, "new-private@example.test");

    final ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.captor();
    verify(audit)
        .recordCurrentActor(
            isNull(),
            isNull(),
            eq(AuthAuditAction.ACCOUNT_EMAIL_CHANGE_REQUESTED),
            eq(PlatformAccount.AUDIT_TYPE),
            eq(account.getId()),
            eq(AuditOrigin.SELF_SERVICE),
            details.capture());
    assertThat(details.getValue().get("changedFields")).isEqualTo(List.of("email"));
    assertThat(details.getValue().get("before")).isEqualTo(Map.of());
    assertThat(details.getValue().get("after")).isEqualTo(Map.of());
    assertThat(details.getValue().toString())
        .doesNotContain("new-private@example.test", "private@example.test", "secret-hash");
    assertThat(account.getEmail()).isEqualTo("private@example.test");
    verify(tokens).saveAndFlush(any());
  }
}
