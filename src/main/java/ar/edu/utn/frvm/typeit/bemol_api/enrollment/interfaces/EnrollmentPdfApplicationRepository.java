package ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface EnrollmentPdfApplicationRepository
    extends Repository<EnrollmentApplication, UUID> {
  @Query(
      """
      SELECT application FROM EnrollmentApplication application
      JOIN FETCH application.institution institution
      JOIN FETCH institution.city institutionCity
      JOIN FETCH institutionCity.province
      JOIN FETCH application.applicantPerson person
      LEFT JOIN FETCH person.nationalityCountry
      LEFT JOIN FETCH person.birthCity
      LEFT JOIN FETCH person.address address
      LEFT JOIN FETCH address.city city
      LEFT JOIN FETCH city.province
      WHERE application.institution.id = :institutionId AND application.id = :applicationId
        AND application.deletedAt IS NULL
      """)
  Optional<EnrollmentApplication> findForPdf(
      @Param("institutionId") UUID institutionId, @Param("applicationId") UUID applicationId);
}
