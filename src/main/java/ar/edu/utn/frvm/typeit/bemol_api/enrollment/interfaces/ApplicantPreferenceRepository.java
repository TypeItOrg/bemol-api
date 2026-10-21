package ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.ApplicantPreference;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicantPreferenceRepository extends JpaRepository<ApplicantPreference, UUID> {

  Optional<ApplicantPreference> findByEnrollmentApplicationIdAndDeletedAtIsNull(
      UUID enrollmentApplicationId);
}
