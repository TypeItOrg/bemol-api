package ar.edu.utn.frvm.typeit.bemol_api.auth.filters;

import ar.edu.utn.frvm.typeit.bemol_api.auth.services.InstitutionalHostContext;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@RequiredArgsConstructor
public class InstitutionalHostFilter extends OncePerRequestFilter {
  private final InstitutionalHostContext context;
  private final HandlerExceptionResolver exceptionResolver;

  @Override
  protected boolean shouldNotFilter(final HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/")
        || request.getRequestURI().startsWith("/api/v1/admin/");
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
      throws ServletException, IOException {
    try {
      final var tenant = context.bind(request);
      final var authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication != null
          && authentication.getPrincipal() instanceof JwtAuthenticatedUser user) {
        context.requireInstitution(tenant, user.institutionId());
      }
    } catch (ApplicationException exception) {
      exceptionResolver.resolveException(request, response, null, exception);
      return;
    }
    chain.doFilter(request, response);
  }
}
