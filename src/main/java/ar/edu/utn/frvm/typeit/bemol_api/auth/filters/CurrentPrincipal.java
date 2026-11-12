package ar.edu.utn.frvm.typeit.bemol_api.auth.filters;

import static ar.edu.utn.frvm.typeit.bemol_api.security.handlers.SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentPrincipal {
  private CurrentPrincipal() {}

  public static Optional<JwtPrincipal> jwt() {
    return jwt(authentication());
  }

  public static Optional<JwtPrincipal> jwt(final @Nullable Authentication authentication) {
    return of(authentication, JwtPrincipal.class);
  }

  public static Optional<JwtAuthenticatedUser> institutionalUser() {
    return institutionalUser(authentication());
  }

  public static Optional<JwtAuthenticatedUser> institutionalUser(
      final @Nullable Authentication authentication) {
    return of(authentication, JwtAuthenticatedUser.class);
  }

  public static Optional<JwtAuthenticatedPlatformAccount> platformAccount() {
    return platformAccount(authentication());
  }

  public static Optional<JwtAuthenticatedPlatformAccount> platformAccount(
      final @Nullable Authentication authentication) {
    return of(authentication, JwtAuthenticatedPlatformAccount.class);
  }

  public static JwtAuthenticatedUser requireInstitutionalUser(
      final @Nullable Authentication authentication) {
    return institutionalUser(authentication).orElseThrow(CurrentPrincipal::forbidden);
  }

  public static JwtAuthenticatedPlatformAccount requirePlatformAccount(
      final @Nullable Authentication authentication) {
    return platformAccount(authentication).orElseThrow(CurrentPrincipal::forbidden);
  }

  public static <T> Optional<T> of(
      final @Nullable Authentication authentication, final Class<T> type) {
    return Optional.ofNullable(authentication)
        .map(entry -> entry.getPrincipal())
        .filter(type::isInstance)
        .map(type::cast);
  }

  private static @Nullable Authentication authentication() {
    return SecurityContextHolder.getContext().getAuthentication();
  }

  private static AccessDeniedException forbidden() {
    return new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
  }
}
