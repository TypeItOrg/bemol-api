package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.RoleRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.SystemRoleResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ListSystemRolesUseCase {

  private final RoleRepository roleRepository;

  public List<SystemRoleResponse> execute() {
    return roleRepository
        .findByScopeAndSystemTrueAndInstitutionIsNullOrderByNameAsc(RoleScope.INSTITUTION)
        .stream()
        .map(SystemRoleResponse::from)
        .toList();
  }
}
