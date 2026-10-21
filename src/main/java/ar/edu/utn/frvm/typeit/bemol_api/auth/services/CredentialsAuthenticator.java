package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CredentialsAuthenticator {

  private final AuthenticationManager authenticationManager;

  public Authentication authenticate(final String principal, final String password) {
    try {
      return authenticationManager.authenticate(
          UsernamePasswordAuthenticationToken.unauthenticated(principal, password));
    } catch (BadCredentialsException | DisabledException exception) {
      throw new InvalidCredentialsException();
    }
  }
}
