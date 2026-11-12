package ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormAssignmentResponse;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EnrollmentFormAssignmentRepository
    extends JpaRepository<EnrollmentFormAssignment, UUID> {
  Optional<EnrollmentFormAssignment> findByIdAndInstitution_Id(UUID id, UUID institutionId);

  boolean existsByFormVersion_Id(UUID formVersionId);

  boolean existsByInstitution_IdAndTrainingPath_IdAndAcademicYear_Id(
      UUID institutionId, UUID trainingPathId, UUID academicYearId);

  @Query(
      """
      SELECT new ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormAssignmentResponse(
        assignment.id, assignment.institution.id, assignment.trainingPath.id, assignment.academicYear.id,
            version.form.id, version.id, version.versionNumber, assignment.revision,
            assignment.trainingPath.name, assignment.academicYear.year, version.form.name)
      FROM EnrollmentFormAssignment assignment JOIN assignment.formVersion version
      WHERE assignment.institution.id = :institutionId
        AND (:academicYearId IS NULL OR assignment.academicYear.id = :academicYearId)
      """)
  Page<EnrollmentFormAssignmentResponse> findSummaries(
      UUID institutionId, @Nullable UUID academicYearId, Pageable pageable);

  @Query(
      """
      SELECT assignment FROM EnrollmentFormAssignment assignment
      JOIN FETCH assignment.formVersion version
      JOIN FETCH version.form
      WHERE assignment.institution.id = :institutionId
        AND assignment.trainingPath.id = :trainingPathId
        AND assignment.academicYear.id = :academicYearId
      """)
  Optional<EnrollmentFormAssignment> findByInstitutionIdAndTrainingPathIdAndAcademicYearId(
      UUID institutionId, UUID trainingPathId, UUID academicYearId);
}
