package ar.edu.utn.frvm.typeit.bemol_api.auth.security;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.INSTITUTIONAL_USERNAME_INVALID;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.INSTITUTIONAL_USERNAME_REQUIRED;

import java.util.UUID;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class InstitutionalUsername {

  public record Parts(UUID institutionId, String documentNumber) {}

  private static final String SEPARATOR = ":";

  private InstitutionalUsername() {}

  public static String format(UUID institutionId, String documentNumber) {
    return institutionId + SEPARATOR + documentNumber;
  }

  public static Parts parse(String username) {
    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException(INSTITUTIONAL_USERNAME_REQUIRED);
    }

    int sep = username.indexOf(SEPARATOR);
    if (sep < 0 || sep == username.length() - 1) {
      throw new IllegalArgumentException(INSTITUTIONAL_USERNAME_INVALID);
    }

    String institutionPart = username.substring(0, sep);
    String documentPart = username.substring(sep + 1);
    return new Parts(UUID.fromString(institutionPart), documentPart);
  }
}
