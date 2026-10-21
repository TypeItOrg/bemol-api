package ar.edu.utn.frvm.typeit.bemol_api.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class InstitutionalUsernameTest {

  @Test
  void format_and_parse_roundTrip() {
    UUID institutionId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    String document = "12345678";

    String username = InstitutionalUsername.format(institutionId, document);
    var parts = InstitutionalUsername.parse(username);

    assertThat(parts.institutionId()).isEqualTo(institutionId);
    assertThat(parts.documentNumber()).isEqualTo(document);
  }

  @Test
  void parse_blank_throws() {
    assertThatThrownBy(() -> InstitutionalUsername.parse(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parse_withoutSeparator_throws() {
    assertThatThrownBy(() -> InstitutionalUsername.parse("noseparator"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parse_invalidUuid_throws() {
    assertThatThrownBy(() -> InstitutionalUsername.parse("not-uuid:12345678"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
