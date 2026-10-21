package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.FrontendPublicProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InstitutionalContextMismatchException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidInstitutionalHostException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionPublicAccessUnavailableException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InvalidPublicSubdomainException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.validation.PublicSubdomainPolicy;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** The private API trusts only this header, rebuilt (never forwarded) by the frontend BFF. */
@Service
@RequiredArgsConstructor
public class InstitutionalHostContext {
  public static final String HEADER = "X-Institutional-Host";
  private static final String ATTRIBUTE = InstitutionalHostContext.class.getName();

  public record Context(UUID institutionId, String origin) {}

  private final FrontendPublicProperties properties;
  private final InstitutionRepository institutions;

  public Optional<Context> resolve(final @Nullable String host) {
    final String domain = properties.institutionalBaseDomain();
    if (host == null || domain.isEmpty()) {
      return Optional.empty();
    }
    if (!host.matches("[A-Za-z0-9.-]+(?::[0-9]{1,5})?")) {
      throw new InvalidInstitutionalHostException();
    }
    final URI parsed;
    try {
      parsed = URI.create("https://" + host.toLowerCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new InvalidInstitutionalHostException();
    }
    final String hostname = parsed.getHost();
    final URI base = properties.baseUri();
    final int effectivePort =
        base.getPort() == -1 ? (base.getScheme().equals("https") ? 443 : 80) : base.getPort();
    if (hostname == null || parsed.getPort() != -1 && parsed.getPort() != effectivePort) {
      throw new InvalidInstitutionalHostException();
    }
    if (hostname.equals(base.getHost())) {
      return Optional.empty();
    }
    final String suffix = "." + domain;
    if (!hostname.endsWith(suffix)) {
      throw new InvalidInstitutionalHostException();
    }
    final String name = hostname.substring(0, hostname.length() - suffix.length());
    try {
      PublicSubdomainPolicy.validate(name);
    } catch (InvalidPublicSubdomainException exception) {
      throw new InvalidInstitutionalHostException();
    }
    try {
      final var institution =
          institutions
              .findByPublicSubdomainAndActiveTrue(name)
              .orElseThrow(InstitutionNotFoundException::new);
      return Optional.of(
          new Context(
              institution.getId(),
              base.getScheme()
                  + "://"
                  + hostname
                  + (base.getPort() == -1 ? "" : ":" + base.getPort())));
    } catch (DataAccessException | CannotCreateTransactionException exception) {
      throw new InstitutionPublicAccessUnavailableException();
    }
  }

  public Optional<Context> bind(final HttpServletRequest request) {
    if (request.getRequestURI().startsWith("/api/v1/admin/")) {
      return Optional.empty();
    }
    if (request.getAttribute(ATTRIBUTE) instanceof Optional<?> existing) {
      @SuppressWarnings("unchecked")
      final Optional<Context> bound = (Optional<Context>) existing;
      return bound;
    }
    if (java.util.Collections.list(request.getHeaders(HEADER)).size() > 1) {
      throw new InvalidInstitutionalHostException();
    }
    final var context = resolve(request.getHeader(HEADER));
    request.setAttribute(ATTRIBUTE, context);
    return context;
  }

  public Optional<Context> current() {
    if (!(RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes)) {
      return Optional.empty();
    }
    return bind(attributes.getRequest());
  }

  public Optional<String> trustedRequestOrigin() {
    if (!(RequestContextHolder.getRequestAttributes()
            instanceof ServletRequestAttributes attributes)
        || attributes.getRequest().getHeader(HEADER) == null
        || properties.institutionalBaseDomain().isEmpty()) {
      return Optional.empty();
    }
    final var tenant = bind(attributes.getRequest());
    final URI base = properties.baseUri();
    return Optional.of(
        tenant
            .map(context -> context.origin())
            .orElseGet(
                () ->
                    base.getScheme()
                        + "://"
                        + base.getHost()
                        + (base.getPort() == -1 ? "" : ":" + base.getPort())));
  }

  public void requireInstitution(final UUID institutionId) {
    requireInstitution(current(), institutionId);
  }

  public void requireInstitution(final Optional<Context> context, final UUID institutionId) {
    if (context.isPresent() && !context.get().institutionId().equals(institutionId)) {
      throw new InstitutionalContextMismatchException();
    }
  }
}
