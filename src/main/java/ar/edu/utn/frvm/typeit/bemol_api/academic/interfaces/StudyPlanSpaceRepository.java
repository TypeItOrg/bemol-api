package ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlanSpace;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyPlanSpaceRepository extends JpaRepository<StudyPlanSpace, UUID> {
  void deleteByStudyPlan_Id(UUID studyPlanId);

  @Query(
      """
      SELECT space FROM StudyPlanSpace space
      LEFT JOIN FETCH space.academicLevel level
      JOIN FETCH space.academicSpace academicSpace
      WHERE space.studyPlan.id = :studyPlanId
      ORDER BY level.displayOrder NULLS LAST, space.displayOrder
      """)
  List<StudyPlanSpace> findByStudyPlanIdWithDetails(@Param("studyPlanId") UUID studyPlanId);

  @Query(
      """
      SELECT space FROM StudyPlanSpace space
      LEFT JOIN FETCH space.academicLevel level
      JOIN FETCH space.academicSpace academicSpace
      WHERE space.studyPlan.id = :studyPlanId
        AND academicSpace.active = true
        AND academicSpace.deletedAt IS NULL
      ORDER BY level.displayOrder NULLS LAST, space.displayOrder
      """)
  List<StudyPlanSpace> findActiveByStudyPlanIdWithDetails(@Param("studyPlanId") UUID studyPlanId);

  @Query(
      """
      SELECT space FROM StudyPlanSpace space
      LEFT JOIN FETCH space.academicLevel
      JOIN FETCH space.academicSpace
      WHERE space.id = :id
        AND space.institution.id = :institutionId
        AND space.studyPlan.deletedAt IS NULL
      """)
  Optional<StudyPlanSpace> findDetailsByIdAndInstitutionId(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      SELECT space FROM StudyPlanSpace space
      LEFT JOIN FETCH space.academicLevel
      JOIN FETCH space.academicSpace
      JOIN FETCH space.studyPlan studyPlan
      JOIN FETCH studyPlan.trainingPath
      WHERE space.id = :id
        AND space.institution.id = :institutionId
        AND studyPlan.deletedAt IS NULL
      """)
  Optional<StudyPlanSpace> findDetailsByIdAndInstitutionIdForUpdate(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  @Query(
      "SELECT space FROM StudyPlanSpace space WHERE space.id = :id AND space.institution.id = :institutionId AND space.studyPlan.deletedAt IS NULL")
  Optional<StudyPlanSpace> findByIdAndInstitution_Id(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  @Query(
      """
      SELECT space FROM StudyPlanSpace space
      LEFT JOIN FETCH space.academicLevel level
      JOIN FETCH space.academicSpace academicSpace
      JOIN FETCH space.studyPlan studyPlan
      WHERE studyPlan.id = :studyPlanId
        AND space.institution.id = :institutionId
        AND studyPlan.deletedAt IS NULL
        AND academicSpace.active = true
        AND academicSpace.deletedAt IS NULL
      ORDER BY level.displayOrder NULLS LAST, space.displayOrder
      """)
  List<StudyPlanSpace> findEligibleByStudyPlanId(
      @Param("institutionId") UUID institutionId, @Param("studyPlanId") UUID studyPlanId);

  @Query(
      """
      SELECT space FROM StudyPlanSpace space
      JOIN FETCH space.academicSpace academicSpace
      JOIN FETCH space.studyPlan studyPlan
      WHERE space.id IN :ids
        AND studyPlan.id = :studyPlanId
        AND space.institution.id = :institutionId
        AND studyPlan.deletedAt IS NULL
        AND academicSpace.active = true
        AND academicSpace.deletedAt IS NULL
      """)
  List<StudyPlanSpace> findEligibleByIdInAndStudyPlanId(
      @Param("institutionId") UUID institutionId,
      @Param("studyPlanId") UUID studyPlanId,
      @Param("ids") List<UUID> ids);

  boolean existsByAcademicLevel_Id(UUID academicLevelId);

  @Query(
      "SELECT COUNT(space) > 0 FROM StudyPlanSpace space WHERE space.studyPlan.id = :studyPlanId")
  boolean existsByStudyPlanId(@Param("studyPlanId") UUID studyPlanId);

  boolean existsByStudyPlan_IdAndAcademicSpace_Id(UUID studyPlanId, UUID academicSpaceId);

  @Query(
      """
      SELECT space FROM StudyPlanSpace space
      LEFT JOIN FETCH space.academicLevel
      JOIN FETCH space.academicSpace
      JOIN FETCH space.studyPlan studyPlan
      JOIN FETCH studyPlan.trainingPath
      WHERE space.studyPlan.id = :studyPlanId
        AND space.academicSpace.id = :academicSpaceId
        AND space.institution.id = :institutionId
        AND studyPlan.deletedAt IS NULL
      """)
  List<StudyPlanSpace> findByStudyPlanIdAndAcademicSpaceId(
      @Param("studyPlanId") UUID studyPlanId,
      @Param("academicSpaceId") UUID academicSpaceId,
      @Param("institutionId") UUID institutionId);

  @Query(
      """
      SELECT
        COUNT(DISTINCT plan.id) AS totalPlans,
        COUNT(DISTINCT CASE WHEN plan.status = ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus.ACTIVE THEN plan.id END) AS activePlans,
        COUNT(DISTINCT CASE WHEN plan.status = ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus.DRAFT THEN plan.id END) AS draftPlans,
        COUNT(DISTINCT CASE WHEN plan.status = ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus.INACTIVE THEN plan.id END) AS inactivePlans,
        COUNT(space.id) AS totalPlacements,
        COALESCE(SUM(CASE WHEN space.academicLevel IS NULL THEN 1 ELSE 0 END), 0) AS unassignedPlacements
      FROM StudyPlanSpace space
      JOIN space.studyPlan plan
      WHERE space.institution.id = :institutionId
        AND space.academicSpace.id = :academicSpaceId
        AND plan.deletedAt IS NULL AND (:#{@scopedAuthorization.unrestricted('STUDY_PLAN_READ')} = true OR plan.trainingPath.id IN :#{@scopedAuthorization.paths('STUDY_PLAN_READ')})
      """)
  AcademicSpaceUsageSummaryProjection summarizeUsage(
      @Param("institutionId") UUID institutionId, @Param("academicSpaceId") UUID academicSpaceId);

  @Query(
      """
      SELECT space FROM StudyPlanSpace space
      JOIN FETCH space.studyPlan plan
      JOIN FETCH plan.trainingPath
      LEFT JOIN FETCH space.academicLevel
      JOIN FETCH space.academicSpace
      WHERE space.institution.id = :institutionId
        AND space.academicSpace.id = :academicSpaceId
        AND plan.id IN :studyPlanIds
        AND plan.deletedAt IS NULL
      ORDER BY plan.name, space.academicLevel.displayOrder NULLS LAST, space.displayOrder
      """)
  List<StudyPlanSpace> findUsageDetails(
      @Param("institutionId") UUID institutionId,
      @Param("academicSpaceId") UUID academicSpaceId,
      @Param("studyPlanIds") List<UUID> studyPlanIds);
}
