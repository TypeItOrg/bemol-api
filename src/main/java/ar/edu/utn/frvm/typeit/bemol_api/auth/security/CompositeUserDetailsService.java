package ar.edu.utn.frvm.typeit.bemol_api.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@RequiredArgsConstructor
public class CompositeUserDetailsService implements UserDetailsService {

  private final InstitutionalUserDetailsService institutionalUserDetailsService;
  private final PlatformUserDetailsService platformUserDetailsService;

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    if (PlatformUsername.isPlatformPrincipal(username)) {
      return platformUserDetailsService.loadUserByUsername(PlatformUsername.parseEmail(username));
    }
    return institutionalUserDetailsService.loadUserByUsername(username);
  }
}
