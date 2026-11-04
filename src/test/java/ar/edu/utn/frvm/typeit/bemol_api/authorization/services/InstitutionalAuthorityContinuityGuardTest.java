package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.LastInstitutionalAuthorityException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstitutionalAuthorityContinuityGuardTest {
  @Mock private PersonRoleAssignmentRepository assignments;
  @InjectMocks private InstitutionalAuthorityContinuityGuard guard;

  private final Institution institution =
      Institution.builder().id(UUID.fromString("00000000-0000-0000-0000-000000000001")).build();
  private final Person person =
      Person.builder()
          .id(UUID.fromString("00000000-0000-0000-0000-000000000002"))
          .institution(institution)
          .build();

  @Test
  void rejectsRemovingAuthorityWhenNoOtherCurrentAuthorityRemains() {
    givenRole(SystemRoleCode.INSTITUTIONAL_AUTHORITY);

    assertThatThrownBy(() -> guard.requireOtherAuthorityBeforeDeletion(person))
        .isInstanceOf(LastInstitutionalAuthorityException.class);
  }

  @Test
  void allowsRemovingAuthorityWhenAnotherCurrentAuthorityRemains() {
    givenRole(SystemRoleCode.INSTITUTIONAL_AUTHORITY);
    when(assignments.existsCurrentAuthorityOtherThan(institution.getId(), person.getId()))
        .thenReturn(true);

    assertThatCode(() -> guard.requireOtherAuthorityBeforeDeletion(person))
        .doesNotThrowAnyException();
  }

  @Test
  void doesNotRequireAnAuthorityReplacementToDeleteANonAuthority() {
    givenRole(SystemRoleCode.STUDENT);

    assertThatCode(() -> guard.requireOtherAuthorityBeforeDeletion(person))
        .doesNotThrowAnyException();

    verify(assignments, never()).existsCurrentAuthorityOtherThan(any(), any());
  }

  private void givenRole(final SystemRoleCode code) {
    final var role =
        Role.builder()
            .id(UUID.fromString("00000000-0000-0000-0000-000000000003"))
            .scope(RoleScope.INSTITUTION)
            .code(code.name())
            .system(true)
            .institution(institution)
            .build();
    final var assignment = PersonRoleAssignment.assign(person, role, institution);
    when(assignments.findByPerson_IdAndInstitution_Id(person.getId(), institution.getId()))
        .thenReturn(List.of(assignment));
  }
}
