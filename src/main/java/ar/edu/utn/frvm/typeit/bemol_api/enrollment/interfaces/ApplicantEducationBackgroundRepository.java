package ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.ApplicantEducationBackground;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicantEducationBackgroundRepository
    extends JpaRepository<ApplicantEducationBackground, UUID> {

  Optional<ApplicantEducationBackground> findByEnrollmentApplicationIdAndDeletedAtIsNull(
      UUID enrollmentApplicationId);
}
