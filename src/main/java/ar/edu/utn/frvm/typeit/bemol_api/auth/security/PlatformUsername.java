package ar.edu.utn.frvm.typeit.bemol_api.auth.security;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.PLATFORM_USERNAME_INVALID;

public final class PlatformUsername {

  private static final String PREFIX = "platform:";

  private PlatformUsername() {}

  public static String format(String email) {
    return PREFIX + email.trim().toLowerCase();
  }

  public static boolean isPlatformPrincipal(String principal) {
    return principal != null && principal.regionMatches(true, 0, PREFIX, 0, PREFIX.length());
  }

  public static String parseEmail(String principal) {
    if (!isPlatformPrincipal(principal)) {
      throw new IllegalArgumentException(PLATFORM_USERNAME_INVALID);
    }

    return principal.substring(PREFIX.length());
  }
}
