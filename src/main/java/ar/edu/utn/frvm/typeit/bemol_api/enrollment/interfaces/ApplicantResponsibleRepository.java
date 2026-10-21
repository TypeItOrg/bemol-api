package ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.ApplicantResponsible;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicantResponsibleRepository extends JpaRepository<ApplicantResponsible, UUID> {

  Optional<ApplicantResponsible> findByEnrollmentApplicationIdAndDeletedAtIsNull(
      UUID enrollmentApplicationId);
}
