package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.AccessScope;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PermissionAccessTest {
  @Test
  void selectedPathsNeverImplyInstitutionalAccess() {
    var path = UUID.randomUUID();
    var selected = new PermissionAccess(AccessScope.TRAINING_PATHS, Set.of(path));
    assertThat(selected.contains(PermissionAccess.institution())).isFalse();
    assertThat(PermissionAccess.institution().contains(selected)).isTrue();
    assertThat(selected.includes(UUID.randomUUID())).isFalse();
  }

  @Test
  void unionsOnlyThePathsGrantedToTheSamePermission() {
    var professor = UUID.randomUUID();
    var technical = UUID.randomUUID();
    var first = new PermissionAccess(AccessScope.TRAINING_PATHS, Set.of(professor));
    var second = new PermissionAccess(AccessScope.TRAINING_PATHS, Set.of(technical));
    assertThat(first.union(second).trainingPathIds())
        .containsExactlyInAnyOrder(professor, technical);
    assertThat(first.trainingPathIds()).containsExactly(professor);
    assertThat(first.union(PermissionAccess.institution()))
        .isEqualTo(PermissionAccess.institution());
    assertThat(PermissionAccess.none().includes(professor)).isFalse();
  }
}
