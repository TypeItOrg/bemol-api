package ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.AccessScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.InvalidAccessScopeException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(requiredProperties = {"roleId", "accessScope", "trainingPathIds"})
public record AssignRoleRequest(
    @NotNull UUID roleId,
    @NotNull AccessScope accessScope,
    @NotNull @Nullable List<@NotNull UUID> trainingPathIds) {
  public AssignRoleRequest {
    if (trainingPathIds != null) {
      if (new HashSet<>(trainingPathIds).size() != trainingPathIds.size()
          || trainingPathIds.stream().anyMatch(Objects::isNull)) {
        throw new InvalidAccessScopeException();
      }
      trainingPathIds = List.copyOf(trainingPathIds);
    }
  }

  public AssignRoleRequest(UUID roleId, AccessScope accessScope, Set<UUID> trainingPathIds) {
    this(roleId, accessScope, trainingPathIds == null ? null : List.copyOf(trainingPathIds));
  }

  public Set<UUID> selectedTrainingPathIds() {
    if (trainingPathIds == null) {
      throw new InvalidAccessScopeException();
    }
    return Set.copyOf(trainingPathIds);
  }
}
