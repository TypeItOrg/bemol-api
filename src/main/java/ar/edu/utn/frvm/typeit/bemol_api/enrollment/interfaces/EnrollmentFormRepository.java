package ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentForm;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentFormRepository extends JpaRepository<EnrollmentForm, UUID> {
  Page<EnrollmentForm> findByInstitution_Id(UUID institutionId, Pageable pageable);

  @Query(
      """
      SELECT form FROM EnrollmentForm form
      WHERE form.institution.id = :institutionId
        AND UNACCENT_LOWER(form.name) LIKE :pattern ESCAPE '\\'
      """)
  Page<EnrollmentForm> searchByInstitutionId(
      @Param("institutionId") UUID institutionId,
      @Param("pattern") String pattern,
      Pageable pageable);

  Optional<EnrollmentForm> findByIdAndInstitution_Id(UUID id, UUID institutionId);
}
