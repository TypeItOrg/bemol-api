package ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Permission;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

  Optional<Permission> findByCode(String code);

  List<Permission> findByCodeIn(List<String> codes);
}
