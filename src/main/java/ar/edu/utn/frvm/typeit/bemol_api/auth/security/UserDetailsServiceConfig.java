package ar.edu.utn.frvm.typeit.bemol_api.auth.security;

import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;

@Configuration
public class UserDetailsServiceConfig {

  @Bean
  UserDetailsService userDetailsService(
      UserRepository userRepository, PlatformAccountRepository platformAccountRepository) {
    return new CompositeUserDetailsService(
        new InstitutionalUserDetailsService(userRepository),
        new PlatformUserDetailsService(platformAccountRepository));
  }
}
