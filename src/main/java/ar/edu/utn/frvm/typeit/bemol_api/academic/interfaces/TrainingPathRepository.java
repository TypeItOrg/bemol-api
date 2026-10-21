package ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainingPathRepository extends JpaRepository<TrainingPath, UUID> {
  @Query(
      "SELECT path FROM TrainingPath path WHERE path.institution.id = :institutionId AND path.deletedAt IS NULL AND (:allPaths = true OR path.id IN :paths) AND (:search IS NULL OR UNACCENT_LOWER(path.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%')))")
  Page<TrainingPath> findAssignablePaths(
      UUID institutionId,
      boolean allPaths,
      Set<UUID> paths,
      @Nullable String search,
      Pageable pageable);

  @EntityGraph(attributePaths = "institution")
  @Query(
      """
      SELECT path FROM TrainingPath path
      WHERE (:#{@scopedAuthorization.unrestricted('TRAINING_PATH_READ')} = true OR path.id IN :#{@scopedAuthorization.paths('TRAINING_PATH_READ')})
        AND (:institutionId IS NULL OR path.institution.id = :institutionId)
        AND ((:deleted = true AND path.deletedAt IS NOT NULL) OR (:deleted = false AND path.deletedAt IS NULL))
        AND (:active IS NULL OR path.active = :active)
        AND (:search IS NULL OR UNACCENT_LOWER(path.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR UNACCENT_LOWER(path.institution.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%')))
      """)
  Page<TrainingPath> findByFilters(
      @Param("institutionId") @Nullable UUID institutionId,
      @Param("search") @Nullable String search,
      @Param("active") @Nullable Boolean active,
      @Param("deleted") boolean deleted,
      Pageable pageable);

  @Query(
      "SELECT path FROM TrainingPath path WHERE path.id = :id AND path.institution.id = :institutionId AND path.deletedAt IS NULL")
  Optional<TrainingPath> findByIdAndInstitution_Id(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  Optional<TrainingPath> findByIdAndInstitution_IdAndActiveTrueAndDeletedAtIsNull(
      UUID id, UUID institutionId);

  @EntityGraph(attributePaths = "institution")
  @Query(
      """
      SELECT path FROM TrainingPath path
      WHERE path.institution.id = :institutionId
        AND path.active = true
        AND path.deletedAt IS NULL
        AND EXISTS (
          SELECT course.id FROM Course course
          WHERE course.institution.id = :institutionId
            AND course.studyPlanSpace.studyPlan.trainingPath.id = path.id
            AND course.status = ar.edu.utn.frvm.typeit.bemol_api.academic.enums.CourseStatus.ACTIVE
            AND course.deletedAt IS NULL
        )
      """)
  Page<TrainingPath> findAvailableForEnrollment(
      @Param("institutionId") UUID institutionId, Pageable pageable);

  java.util.List<TrainingPath> findByInstitution_IdAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(
      UUID institutionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT path FROM TrainingPath path WHERE path.id = :id AND path.institution.id = :institutionId")
  Optional<TrainingPath> findByIdAndInstitution_IdForLifecycle(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  @Query(
      value =
          "SELECT EXISTS (SELECT 1 FROM training_paths WHERE institution_id = :institutionId AND deleted_at IS NULL AND lower(translate(name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN')) = lower(translate(:name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN'))) ",
      nativeQuery = true)
  boolean existsByNormalizedName(
      @Param("institutionId") UUID institutionId, @Param("name") String name);

  @Query(
      value =
          "SELECT EXISTS (SELECT 1 FROM training_paths WHERE institution_id = :institutionId AND deleted_at IS NULL AND training_path_id <> :id AND lower(translate(name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN')) = lower(translate(:name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN'))) ",
      nativeQuery = true)
  boolean existsByNormalizedNameAndIdNot(
      @Param("institutionId") UUID institutionId, @Param("name") String name, @Param("id") UUID id);

  @Query(
      "SELECT COUNT(plan) > 0 FROM StudyPlan plan WHERE plan.trainingPath.id = :trainingPathId AND plan.deletedAt IS NULL AND plan.status = ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus.ACTIVE")
  boolean existsActiveStudyPlan(@Param("trainingPathId") UUID trainingPathId);

  @Query(
      "SELECT COUNT(plan) > 0 FROM StudyPlan plan WHERE plan.trainingPath.id = :trainingPathId AND plan.deletedAt IS NULL")
  boolean existsCurrentStudyPlan(@Param("trainingPathId") UUID trainingPathId);

  @Query(
      """
      SELECT path FROM TrainingPath path
      WHERE path.institution.id = :institutionId AND path.active = true AND path.deletedAt IS NULL
        AND EXISTS (SELECT ep.id FROM EnrollmentPeriod ep
          WHERE ep.institution.id = :institutionId AND ep.status = ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentPeriodStatus.OPEN
            AND ep.deletedAt IS NULL AND ep.scopeConfigured = true AND ep.startDate <= :now AND ep.endDate >= :now
            AND (:periodId IS NULL OR ep.id = :periodId) AND EXISTS (SELECT course.id FROM Course course
              WHERE course.institution.id = ep.institution.id
                AND course.academicYear.id = ep.academicYear.id
                AND course.studyPlanSpace.studyPlan.trainingPath.active = true
                AND course.studyPlanSpace.studyPlan.trainingPath.deletedAt IS NULL
                AND course.status = ar.edu.utn.frvm.typeit.bemol_api.academic.enums.CourseStatus.ACTIVE
                AND course.deletedAt IS NULL
                AND course.studyPlanSpace.studyPlan.trainingPath.id = path.id
                AND EXISTS (SELECT selection.id FROM EnrollmentPeriodOfferingLevel selection
          WHERE selection.offering.period.id = ep.id
            AND selection.offering.period.scopeConfigured = true
            AND selection.offering.studyPlan.deletedAt IS NULL
            AND selection.offering.studyPlan.trainingPath.deletedAt IS NULL
            AND selection.offering.studyPlan.trainingPath.active = true
            AND selection.offering.period.academicYear.id = course.academicYear.id
            AND selection.offering.studyPlan.id = course.studyPlanSpace.studyPlan.id
            AND (selection.academicLevel.id = course.studyPlanSpace.academicLevel.id
              OR selection.academicLevel IS NULL AND course.studyPlanSpace.academicLevel IS NULL))))
      """)
  Page<TrainingPath> findOfferedForEnrollment(
      UUID institutionId, java.time.Instant now, @Nullable UUID periodId, Pageable pageable);
}
