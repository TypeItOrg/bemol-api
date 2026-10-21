package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.WebAuthnProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.LoginStateInconsistentException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PasskeyLimitExceededException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyRegistrationOptionsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsCodec;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialCreationOptionsRequest;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RequestPasskeyRegistrationOptionsUseCase {

  private final UserRepository userRepository;
  private final PasskeyCredentialRepository passkeyCredentialRepository;
  private final WebAuthnUserHandleService userHandleService;
  private final WebAuthnRelyingPartyOperations relyingPartyOperations;
  private final WebAuthnCeremonyService ceremonyService;
  private final RecentAuthService recentAuthService;
  private final WebAuthnProperties properties;
  private final WebAuthnOptionsCodec optionsCodec;

  @Transactional
  public PasskeyRegistrationOptionsResponse execute(
      final JwtAuthenticatedUser principal, final String label) {
    recentAuthService.requireRecent(principal.sessionId(), principal.userId());
    final String trimmed = label == null ? "" : label.trim();
    final boolean validLabel = !trimmed.isEmpty() && trimmed.length() <= 100;
    if (!validLabel) {
      throw new IllegalArgumentException("El nombre debe tener entre 1 y 100 caracteres.");
    }
    if (passkeyCredentialRepository.countActiveByUserId(principal.userId())
        >= properties.maxPasskeys()) {
      throw new PasskeyLimitExceededException();
    }
    userHandleService.ensureHandle(principal.userId());
    final User user =
        userRepository
            .findWithPersonAndInstitutionById(principal.userId())
            .orElseThrow(LoginStateInconsistentException::new);
    final Authentication authentication =
        UsernamePasswordAuthenticationToken.authenticated(user.getId().toString(), null, List.of());
    final PublicKeyCredentialCreationOptions options =
        relyingPartyOperations.createPublicKeyCredentialCreationOptions(
            new ImmutablePublicKeyCredentialCreationOptionsRequest(authentication));
    final String optionsJson = optionsCodec.encodeCreationOptions(options);
    final String ceremonyId =
        ceremonyService.storeRegistration(
            user.getId(), principal.sessionId(), trimmed, optionsJson);
    return new PasskeyRegistrationOptionsResponse(
        ceremonyId, optionsCodec.parseSnapshot(optionsJson));
  }
}
