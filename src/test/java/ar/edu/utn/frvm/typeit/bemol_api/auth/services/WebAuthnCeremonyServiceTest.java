package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.WebAuthnProperties;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class WebAuthnCeremonyServiceTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private WebAuthnCeremonyService service;

  @BeforeEach
  void setUp() {
    Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    service =
        new WebAuthnCeremonyService(
            redisTemplate,
            new WebAuthnProperties(
                "localhost",
                "Boero",
                List.of("http://localhost:3000"),
                Duration.ofMinutes(5),
                Duration.ofMinutes(5),
                10,
                Duration.ofMinutes(5)));
  }

  @Test
  @DisplayName("Should consume authentication ceremonies atomically and only once")
  void authenticationCeremony_isOneTime() {
    final UUID userId = UUID.randomUUID();
    final ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);

    final String ceremonyId =
        service.storeAuthentication("attempt-1", userId, "{\"type\":\"auth\"}");

    verify(valueOperations)
        .set(
            ArgumentMatchers.eq("boero:webauthn:authentication:" + ceremonyId),
            valueCaptor.capture(),
            any(Duration.class));
    when(valueOperations.getAndDelete(anyString())).thenReturn(valueCaptor.getValue());

    final Optional<AuthenticationCeremony> consumed = service.consumeAuthentication(ceremonyId);

    assertThat(consumed).isPresent();
    assertThat(consumed.get().loginAttemptId()).isEqualTo("attempt-1");
    assertThat(consumed.get().userId()).isEqualTo(userId);
    assertThat(consumed.get().optionsJson()).isEqualTo("{\"type\":\"auth\"}");

    when(valueOperations.getAndDelete(anyString())).thenReturn(null);

    assertThat(service.consumeAuthentication(ceremonyId)).isEmpty();
  }

  @Test
  @DisplayName("Should bind registration ceremonies to the same session")
  void registrationCeremony_bindsSession() {
    final UUID userId = UUID.randomUUID();
    final UUID sessionId = UUID.randomUUID();
    final ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);

    final String ceremonyId =
        service.storeRegistration(userId, sessionId, "Mi PC", "{\"type\":\"reg\"}");

    verify(valueOperations)
        .set(
            ArgumentMatchers.eq("boero:webauthn:registration:" + ceremonyId),
            valueCaptor.capture(),
            any(Duration.class));
    when(valueOperations.getAndDelete(anyString())).thenReturn(valueCaptor.getValue());

    final Optional<RegistrationCeremony> consumed = service.consumeRegistration(ceremonyId);

    assertThat(consumed).isPresent();
    assertThat(consumed.get().label()).isEqualTo("Mi PC");
    assertThat(consumed.get().sessionId()).isEqualTo(sessionId);
  }

  @Test
  @DisplayName("Should reject blank ceremony ids without touching Redis values")
  void consume_rejectsBlankIds() {
    assertThat(service.consumeAuthentication(" ")).isEmpty();
    assertThat(service.consumeRegistration("")).isEmpty();
  }
}
