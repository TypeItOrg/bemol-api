package ar.edu.utn.frvm.typeit.bemol_api.auth.security;

import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@RequiredArgsConstructor
public class PlatformUserDetailsService implements UserDetailsService {

  private final PlatformAccountRepository platformAccountRepository;

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    return platformAccountRepository
        .findByEmailIgnoreCase(email)
        .orElseThrow(() -> new UsernameNotFoundException(email));
  }
}
