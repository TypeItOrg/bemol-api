package ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormVersion;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormVersionStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormVersionSummaryResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentFormVersionRepository
    extends JpaRepository<EnrollmentFormVersion, UUID> {
  Optional<EnrollmentFormVersion> findByIdAndInstitution_Id(UUID id, UUID institutionId);

  boolean existsByForm_IdAndStatus(UUID formId, EnrollmentFormVersionStatus status);

  @Query(
      "SELECT COALESCE(MAX(version.versionNumber), 0) FROM EnrollmentFormVersion version WHERE version.form.id = :formId")
  int highestVersionNumber(UUID formId);

  @Query(
      """
      SELECT new ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormVersionSummaryResponse(
        version.id, version.form.id, version.institution.id, version.versionNumber,
        version.status, version.revision, version.publishedAt, version.archivedAt)
      FROM EnrollmentFormVersion version
      WHERE version.institution.id = :institutionId AND version.form.id = :formId
      """)
  Page<EnrollmentFormVersionSummaryResponse> findSummaries(
      UUID institutionId, UUID formId, Pageable pageable);

  @Query(
      """
      SELECT new ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormVersionSummaryResponse(
        version.id, version.form.id, version.institution.id, version.versionNumber,
        version.status, version.revision, version.publishedAt, version.archivedAt)
      FROM EnrollmentFormVersion version
      WHERE version.institution.id = :institutionId
        AND version.form.id = :formId
        AND LOWER(CONCAT(
          'v', CAST(version.versionNumber AS string),
          ' version ', CAST(version.versionNumber AS string),
          ' · ',
          CASE version.status
            WHEN ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormVersionStatus.DRAFT THEN 'borrador draft'
            WHEN ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormVersionStatus.PUBLISHED THEN 'publicada published'
            ELSE 'archivada archived'
          END
        )) LIKE :pattern ESCAPE '\\'
      """)
  Page<EnrollmentFormVersionSummaryResponse> searchSummaries(
      @Param("institutionId") UUID institutionId,
      @Param("formId") UUID formId,
      @Param("pattern") String pattern,
      Pageable pageable);
}
