package ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReplacePersonRolesRequest(
    @NotEmpty List<@NotNull @Valid AssignRoleRequest> assignments) {
  public ReplacePersonRolesRequest {
    assignments = assignments == null ? List.of() : List.copyOf(assignments);
  }
}
