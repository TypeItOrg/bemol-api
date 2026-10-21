package ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Address;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, UUID> {}
