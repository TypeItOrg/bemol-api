package ar.edu.utn.frvm.typeit.boero_api.authorization.entities;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.boero_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthorizationDomainTest {

  @Test
  @DisplayName("Should reject a person role assignment across institutions")
  void personRoleAssignmentRejectsAnotherInstitution() {
    final Institution institution = Institution.builder().id(UUID.randomUUID()).build();
    final Institution otherInstitution = Institution.builder().id(UUID.randomUUID()).build();
    final Person person = Person.builder().institution(institution).build();
    final Role role =
        Role.builder().scope(RoleScope.INSTITUTION).institution(otherInstitution).build();

    assertThatThrownBy(() -> PersonRoleAssignment.assign(person, role, institution))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("Should accept only platform roles for platform accounts")
  void platformAssignmentRequiresPlatformRole() {
    final PlatformAccount account = PlatformAccount.builder().build();
    final Role institutionalRole =
        Role.builder()
            .scope(RoleScope.INSTITUTION)
            .institution(Institution.builder().build())
            .build();

    assertThatThrownBy(() -> PlatformAccountRole.assign(account, institutionalRole))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
