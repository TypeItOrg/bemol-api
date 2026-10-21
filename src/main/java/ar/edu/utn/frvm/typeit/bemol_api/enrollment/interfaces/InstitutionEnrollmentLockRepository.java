package ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.InstitutionEnrollmentLock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface InstitutionEnrollmentLockRepository
    extends JpaRepository<InstitutionEnrollmentLock, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<InstitutionEnrollmentLock> findByInstitutionId(UUID institutionId);
}
