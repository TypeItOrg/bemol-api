package ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyPlanRepository
    extends JpaRepository<StudyPlan, UUID>, JpaSpecificationExecutor<StudyPlan> {
  @EntityGraph(attributePaths = {"institution", "trainingPath"})
  Page<StudyPlan> findAll(Specification<StudyPlan> specification, Pageable pageable);

  @EntityGraph(attributePaths = {"institution", "trainingPath"})
  @Query(
      """
      SELECT plan FROM StudyPlan plan
      WHERE (:#{@scopedAuthorization.unrestricted('ACADEMIC_OFFER_READ')} = true OR plan.trainingPath.id IN :#{@scopedAuthorization.paths('ACADEMIC_OFFER_READ')})
        AND plan.institution.id = :institutionId
        AND plan.deletedAt IS NULL
        AND plan.status = ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus.ACTIVE
        AND plan.trainingPath.active = true
        AND plan.trainingPath.deletedAt IS NULL
        AND plan.effectiveFrom <= :validOn
        AND (plan.effectiveTo IS NULL OR plan.effectiveTo >= :validOn)
      """)
  Page<StudyPlan> findAvailableOffers(
      @Param("institutionId") UUID institutionId,
      @Param("validOn") LocalDate validOn,
      Pageable pageable);

  @EntityGraph(attributePaths = {"institution", "trainingPath"})
  @Query(
      """
      SELECT plan FROM StudyPlan plan
      WHERE plan.id = :studyPlanId
        AND plan.institution.id = :institutionId
        AND plan.deletedAt IS NULL
        AND plan.status = ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus.ACTIVE
        AND plan.trainingPath.active = true
        AND plan.trainingPath.deletedAt IS NULL
        AND plan.effectiveFrom <= :validOn
        AND (plan.effectiveTo IS NULL OR plan.effectiveTo >= :validOn)
      """)
  Optional<StudyPlan> findAvailableOfferById(
      @Param("institutionId") UUID institutionId,
      @Param("studyPlanId") UUID studyPlanId,
      @Param("validOn") LocalDate validOn);

  @EntityGraph(attributePaths = {"institution", "trainingPath"})
  @Query(
      "SELECT plan FROM StudyPlan plan WHERE plan.trainingPath.id = :trainingPathId AND plan.institution.id = :institutionId AND plan.deletedAt IS NULL AND (:#{@scopedAuthorization.unrestricted('STUDY_PLAN_READ')} = true OR plan.trainingPath.id IN :#{@scopedAuthorization.paths('STUDY_PLAN_READ')})")
  Page<StudyPlan> findByTrainingPath_IdAndInstitution_IdAndDeletedAtIsNull(
      UUID trainingPathId, UUID institutionId, Pageable pageable);

  @EntityGraph(attributePaths = {"institution", "trainingPath"})
  @Query(
      """
      SELECT plan FROM StudyPlan plan
      WHERE plan.trainingPath.id = :trainingPathId
        AND plan.institution.id = :institutionId
        AND plan.status = :status
        AND plan.deletedAt IS NULL
        AND (plan.effectiveFrom IS NULL OR plan.effectiveFrom <= :validOn)
        AND (plan.effectiveTo IS NULL OR plan.effectiveTo >= :validOn)
      ORDER BY plan.effectiveFrom DESC, plan.createdAt DESC
      """)
  java.util.List<StudyPlan> findActiveByTrainingPathIdAndInstitutionIdValidOn(
      @Param("trainingPathId") UUID trainingPathId,
      @Param("institutionId") UUID institutionId,
      @Param("status") StudyPlanStatus status,
      @Param("validOn") LocalDate validOn);

  @EntityGraph(attributePaths = {"institution", "trainingPath"})
  @Query(
      """
      SELECT plan FROM StudyPlan plan
      WHERE plan.institution.id = :institutionId
        AND plan.deletedAt IS NULL
        AND EXISTS (
          SELECT space.id FROM StudyPlanSpace space
          WHERE space.studyPlan.id = plan.id
            AND space.institution.id = :institutionId
            AND space.academicSpace.id = :academicSpaceId AND (:#{@scopedAuthorization.unrestricted('STUDY_PLAN_READ')} = true OR plan.trainingPath.id IN :#{@scopedAuthorization.paths('STUDY_PLAN_READ')})
        )
      """)
  Page<StudyPlan> findByAcademicSpaceIdAndInstitutionId(
      @Param("institutionId") UUID institutionId,
      @Param("academicSpaceId") UUID academicSpaceId,
      Pageable pageable);

  @EntityGraph(attributePaths = {"institution", "trainingPath"})
  @Query(
      "SELECT plan FROM StudyPlan plan WHERE plan.id = :id AND plan.institution.id = :institutionId AND plan.deletedAt IS NULL")
  Optional<StudyPlan> findByIdAndInstitution_Id(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT plan FROM StudyPlan plan WHERE plan.id = :id AND plan.institution.id = :institutionId AND plan.deletedAt IS NULL")
  Optional<StudyPlan> findByIdAndInstitution_IdForUpdate(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT plan FROM StudyPlan plan WHERE plan.id = :id AND plan.institution.id = :institutionId")
  Optional<StudyPlan> findByIdAndInstitution_IdForLifecycle(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  boolean existsByPreviousVersion_IdAndDeletedAtIsNull(UUID previousVersionId);

  @Query(
      value =
          "SELECT EXISTS (SELECT 1 FROM study_plans WHERE training_path_id = :trainingPathId AND version_number = 1 AND deleted_at IS NULL AND lower(translate(name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN')) = lower(translate(:name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN'))) ",
      nativeQuery = true)
  boolean existsByNormalizedName(
      @Param("trainingPathId") UUID trainingPathId, @Param("name") String name);

  @Query(
      value =
          "SELECT EXISTS (SELECT 1 FROM study_plans WHERE training_path_id = :trainingPathId AND version_number = :versionNumber AND deleted_at IS NULL AND lower(translate(name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN')) = lower(translate(:name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN'))) ",
      nativeQuery = true)
  boolean existsByNormalizedNameAndVersion(
      @Param("trainingPathId") UUID trainingPathId,
      @Param("name") String name,
      @Param("versionNumber") int versionNumber);

  @Query(
      value =
          "SELECT EXISTS (SELECT 1 FROM study_plans WHERE training_path_id = :trainingPathId AND version_number = 1 AND deleted_at IS NULL AND study_plan_id <> :id AND lower(translate(name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN')) = lower(translate(:name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN'))) ",
      nativeQuery = true)
  boolean existsByNormalizedNameAndIdNot(
      @Param("trainingPathId") UUID trainingPathId,
      @Param("name") String name,
      @Param("id") UUID id);

  @Query(
      value =
          "SELECT EXISTS (SELECT 1 FROM study_plans WHERE training_path_id = :trainingPathId AND version_number = :versionNumber AND deleted_at IS NULL AND study_plan_id <> :id AND lower(translate(name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN')) = lower(translate(:name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN'))) ",
      nativeQuery = true)
  boolean existsByNormalizedNameAndVersionAndIdNot(
      @Param("trainingPathId") UUID trainingPathId,
      @Param("name") String name,
      @Param("versionNumber") int versionNumber,
      @Param("id") UUID id);

  @EntityGraph(attributePaths = {"institution", "trainingPath"})
  @Query(
      """
      SELECT plan FROM StudyPlan plan
      WHERE (:#{@scopedAuthorization.unrestricted('STUDY_PLAN_READ')} = true OR plan.trainingPath.id IN :#{@scopedAuthorization.paths('STUDY_PLAN_READ')})
        AND (:institutionId IS NULL OR plan.institution.id = :institutionId)
        AND plan.deletedAt IS NULL AND plan.status <> ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus.DRAFT
        AND plan.trainingPath.active = true AND plan.trainingPath.deletedAt IS NULL
        AND (:trainingPathId IS NULL OR plan.trainingPath.id = :trainingPathId)
        AND (:search IS NULL OR UNACCENT_LOWER(plan.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR UNACCENT_LOWER(plan.trainingPath.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%')))
      """)
  Page<StudyPlan> findPublished(
      @Nullable UUID institutionId,
      @Nullable UUID trainingPathId,
      @Nullable String search,
      Pageable pageable);
}
