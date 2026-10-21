package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.AccessScope;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@io.swagger.v3.oas.annotations.media.Schema(requiredProperties = {"accessScope", "trainingPathIds"})
public record PermissionAccess(AccessScope accessScope, Set<UUID> trainingPathIds)
    implements Serializable {
  public PermissionAccess {
    trainingPathIds = Set.copyOf(trainingPathIds);
  }

  public static PermissionAccess institution() {
    return new PermissionAccess(AccessScope.INSTITUTION, Set.of());
  }

  public static PermissionAccess none() {
    return new PermissionAccess(AccessScope.TRAINING_PATHS, Set.of());
  }

  public boolean institutional() {
    return accessScope == AccessScope.INSTITUTION;
  }

  public boolean includes(UUID id) {
    return institutional() || trainingPathIds.contains(id);
  }

  public boolean contains(PermissionAccess other) {
    return institutional()
        || (!other.institutional() && trainingPathIds.containsAll(other.trainingPathIds));
  }

  public PermissionAccess union(PermissionAccess other) {
    if (institutional() || other.institutional()) {
      return institution();
    }
    Set<UUID> paths = new HashSet<>(trainingPathIds);
    paths.addAll(other.trainingPathIds);
    return new PermissionAccess(AccessScope.TRAINING_PATHS, paths);
  }
}
