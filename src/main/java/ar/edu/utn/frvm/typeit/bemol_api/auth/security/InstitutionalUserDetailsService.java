package ar.edu.utn.frvm.typeit.bemol_api.auth.security;

import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@RequiredArgsConstructor
public class InstitutionalUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    try {
      var parts = InstitutionalUsername.parse(username);
      return userRepository
          .findWithPersonAndInstitutionByPersonDocumentNumberAndInstitution_Id(
              parts.documentNumber(), parts.institutionId())
          .orElseThrow(() -> new UsernameNotFoundException(username));
    } catch (IllegalArgumentException ex) {
      throw new UsernameNotFoundException(username, ex);
    }
  }
}
