package ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces;

import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardianAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonGuardianAttachmentRepository
    extends JpaRepository<PersonGuardianAttachment, UUID> {

  List<PersonGuardianAttachment> findByPersonGuardianOrderByCreatedAtAsc(PersonGuardian link);

  Optional<PersonGuardianAttachment> findByIdAndPersonGuardian(UUID id, PersonGuardian link);

  long countByPersonGuardian(PersonGuardian link);
}
