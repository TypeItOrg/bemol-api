package ar.edu.utn.frvm.typeit.bemol_api.auth.filters;

import ar.edu.utn.frvm.typeit.bemol_api.auth.services.ActiveIdentityService;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@RequiredArgsConstructor
public class ActiveIdentityFilter extends OncePerRequestFilter {
  private final ActiveIdentityService identities;
  private final HandlerExceptionResolver exceptionResolver;

  @Override
  protected boolean shouldNotFilter(final HttpServletRequest request) {
    // Recovery and logout must remain available even after a representation is revoked.
    return List.of("/api/v1/auth/active-identity", "/api/v1/auth/logout", "/api/v1/auth/session")
        .contains(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
      throws ServletException, IOException {
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof JwtAuthenticatedUser actor) {
      try {
        final var selected = identities.resolve(actor);
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(selected, null, List.of()));
      } catch (ApplicationException exception) {
        exceptionResolver.resolveException(request, response, null, exception);
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
