package ar.edu.utn.frvm.typeit.bemol_api.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class InstitutionalUserDetailsServiceTest {

  @Mock private UserRepository userRepository;

  private InstitutionalUserDetailsService userDetailsService;

  @BeforeEach
  void setUp() {
    userDetailsService = new InstitutionalUserDetailsService(userRepository);
  }

  @Test
  void loadUserByUsername_returnsUser_whenFound() {
    UUID institutionId = UUID.randomUUID();
    String document = "87654321";
    String username = InstitutionalUsername.format(institutionId, document);
    User user =
        User.builder()
            .id(UUID.randomUUID())
            .institution(Institution.builder().id(institutionId).build())
            .person(Person.builder().documentNumber(document).build())
            .password("hash")
            .build();
    when(userRepository.findWithPersonAndInstitutionByPersonDocumentNumberAndInstitution_Id(
            document, institutionId))
        .thenReturn(Optional.of(user));

    assertThat(userDetailsService.loadUserByUsername(username)).isSameAs(user);
  }

  @Test
  void loadUserByUsername_throws_whenUserMissing() {
    UUID institutionId = UUID.randomUUID();
    String document = "87654321";
    String username = InstitutionalUsername.format(institutionId, document);
    when(userRepository.findWithPersonAndInstitutionByPersonDocumentNumberAndInstitution_Id(
            document, institutionId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> userDetailsService.loadUserByUsername(username))
        .isInstanceOf(UsernameNotFoundException.class);
  }

  @Test
  void loadUserByUsername_throws_whenFormatInvalid() {
    assertThatThrownBy(() -> userDetailsService.loadUserByUsername("bad"))
        .isInstanceOf(UsernameNotFoundException.class);
  }
}
