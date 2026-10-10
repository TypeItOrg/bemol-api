package ar.edu.utn.frvm.typeit.boero_api.auth.filters;

import static ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.AuthMessages.TOKEN_BLACKLISTED;
import static ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.AuthMessages.TOKEN_EXPIRED;
import static ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.AuthMessages.TOKEN_INVALID;
import static ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.AuthMessages.TOKEN_SESSION_INACTIVE;
import static ar.edu.utn.frvm.typeit.boero_api.security.config.PublicRoutes.GET_ONLY_ROUTES;
import static ar.edu.utn.frvm.typeit.boero_api.security.config.PublicRoutes.PUBLIC_ROUTES;

import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.AccessTokenParseResult;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.AccountType;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.util.PathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@NullMarked
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final AuthenticationEntryPoint authenticationEntryPoint;
  private final IsSessionActiveUseCase isSessionActiveUseCase;
  private final IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;
  private final JwtService jwtService;
  private final PathMatcher pathMatcher;
  private final TokenBlacklistService tokenBlacklistService;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
      filterChain.doFilter(request, response);
      return;
    }

    String token = header.substring(7).trim();
    if (token.isEmpty()) {
      authenticationEntryPoint.commence(
          request, response, new InsufficientAuthenticationException(TOKEN_INVALID));
      return;
    }

    AccessTokenParseResult accessTokenParseResult = jwtService.parseAccessToken(token);
    switch (accessTokenParseResult) {
      case AccessTokenParseResult.Expired() -> {
        authenticationEntryPoint.commence(
            request, response, new InsufficientAuthenticationException(TOKEN_EXPIRED));
      }

      case AccessTokenParseResult.Invalid() -> {
        authenticationEntryPoint.commence(
            request, response, new InsufficientAuthenticationException(TOKEN_INVALID));
      }

      case AccessTokenParseResult.Ok ok -> {
        Claims claims = ok.claims();
        String tokenId = jwtService.extractTokenId(claims);
        UUID sessionId = jwtService.extractSessionId(claims);
        AccountType accountType = jwtService.extractAccountType(claims);

        if (tokenBlacklistService.isBlacklisted(tokenId)) {
          authenticationEntryPoint.commence(
              request, response, new InsufficientAuthenticationException(TOKEN_BLACKLISTED));
          return;
        }

        JwtPrincipal principal =
            switch (accountType) {
              case INSTITUTION -> authenticateInstitutional(claims, tokenId, sessionId);
              case PLATFORM -> authenticatePlatform(claims, tokenId, sessionId);
            };

        if (principal == null) {
          authenticationEntryPoint.commence(
              request, response, new InsufficientAuthenticationException(TOKEN_SESSION_INACTIVE));
          return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
      }
    }
  }

  private @Nullable JwtPrincipal authenticateInstitutional(
      Claims claims, String tokenId, UUID sessionId) {
    if (!isSessionActiveUseCase.execute(sessionId)) {
      return null;
    }

    return JwtAuthenticatedUser.builder()
        .userId(jwtService.extractUserId(claims))
        .personId(jwtService.extractPersonId(claims))
        .documentNumber(jwtService.extractDocumentNumber(claims))
        .institutionId(jwtService.extractInstitutionId(claims))
        .sessionId(sessionId)
        .tokenId(tokenId)
        .build();
  }

  private @Nullable JwtPrincipal authenticatePlatform(
      Claims claims, String tokenId, UUID sessionId) {
    if (!isPlatformSessionActiveUseCase.execute(sessionId)) {
      return null;
    }

    return JwtAuthenticatedPlatformAccount.builder()
        .platformAccountId(jwtService.extractPlatformAccountId(claims))
        .email(jwtService.extractEmail(claims))
        .sessionId(sessionId)
        .tokenId(tokenId)
        .build();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String currentPath = request.getServletPath();
    if (Arrays.stream(PUBLIC_ROUTES).anyMatch(route -> pathMatcher.match(route, currentPath))) {
      return true;
    }

    if (HttpMethod.GET.matches(request.getMethod())) {
      return Arrays.stream(GET_ONLY_ROUTES)
          .anyMatch(route -> pathMatcher.match(route, currentPath));
    }

    return false;
  }
}
