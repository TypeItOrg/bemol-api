package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidLoginAttemptException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyAuthenticationOptionsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RequestPasskeyAuthenticationOptionsUseCase {

  private final LoginAttemptService loginAttemptService;
  private final UserRepository userRepository;
  private final WebAuthnRelyingPartyOperations relyingPartyOperations;
  private final WebAuthnCeremonyService ceremonyService;
  private final WebAuthnOptionsCodec optionsCodec;

  @Transactional(readOnly = true)
  public PasskeyAuthenticationOptionsResponse execute(final String loginAttemptId) {
    final LoginAttempt attempt = loginAttemptService.resolve(loginAttemptId);
    if (!attempt.hasActivePasskeys()) {
      throw new InvalidLoginAttemptException();
    }
    final User user =
        userRepository
            .findWithPersonAndInstitutionById(attempt.userId())
            .orElseThrow(InvalidLoginAttemptException::new);
    if (!user.getInstitutionId().equals(attempt.institutionId())) {
      throw new InvalidLoginAttemptException();
    }
    final Authentication authentication =
        UsernamePasswordAuthenticationToken.unauthenticated(user.getId().toString(), null);
    final PublicKeyCredentialRequestOptions options =
        relyingPartyOperations.createCredentialRequestOptions(
            new ImmutablePublicKeyCredentialRequestOptionsRequest(authentication));
    final String optionsJson = optionsCodec.encodeRequestOptions(options);
    final String ceremonyId =
        ceremonyService.storeAuthentication(attempt.id(), user.getId(), optionsJson);
    return new PasskeyAuthenticationOptionsResponse(
        ceremonyId, optionsCodec.parseSnapshot(optionsJson));
  }
}
