package ar.edu.utn.frvm.typeit.boero_api.academic.interfaces;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicLevel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AcademicLevelRepository extends JpaRepository<AcademicLevel, UUID> {
  List<AcademicLevel> findByStudyPlan_IdOrderByDisplayOrderAsc(UUID studyPlanId);

  @Query(
      """
      SELECT level FROM AcademicLevel level
      WHERE level.studyPlan.institution.id = :institutionId
        AND level.studyPlan.id = :studyPlanId
        AND level.studyPlan.deletedAt IS NULL
        AND (:#{@scopedAuthorization.unrestricted('COURSE_READ')} = true OR level.studyPlan.trainingPath.id IN :#{@scopedAuthorization.paths('COURSE_READ')})
        AND (:id IS NULL OR level.id = :id)
        AND (:search IS NULL OR UNACCENT_LOWER(level.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%')))
      ORDER BY level.displayOrder, level.id
      """)
  Page<AcademicLevel> findCourseFilterOptions(
      @Param("institutionId") UUID institutionId,
      @Param("studyPlanId") UUID studyPlanId,
      @Param("search") @Nullable String search,
      @Param("id") @Nullable UUID id,
      Pageable pageable);

  void deleteByStudyPlan_Id(UUID studyPlanId);

  @Query(
      "SELECT level FROM AcademicLevel level WHERE level.id = :id AND level.studyPlan.institution.id = :institutionId AND level.studyPlan.deletedAt IS NULL")
  Optional<AcademicLevel> findByIdAndStudyPlan_Institution_Id(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  @Query(
      "SELECT level FROM AcademicLevel level WHERE level.id = :id AND level.studyPlan.id = :studyPlanId AND level.studyPlan.deletedAt IS NULL")
  Optional<AcademicLevel> findByIdAndStudyPlan_Id(
      @Param("id") UUID id, @Param("studyPlanId") UUID studyPlanId);

  boolean existsByStudyPlan_IdAndDisplayOrder(UUID studyPlanId, int displayOrder);

  boolean existsByStudyPlan_IdAndDisplayOrderAndIdNot(UUID studyPlanId, int displayOrder, UUID id);

  @Query(
      value =
          "SELECT EXISTS (SELECT 1 FROM academic_levels WHERE study_plan_id = :studyPlanId AND lower(translate(name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN')) = lower(translate(:name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN'))) ",
      nativeQuery = true)
  boolean existsByNormalizedName(
      @Param("studyPlanId") UUID studyPlanId, @Param("name") String name);

  @Query(
      value =
          "SELECT EXISTS (SELECT 1 FROM academic_levels WHERE study_plan_id = :studyPlanId AND academic_level_id <> :id AND lower(translate(name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN')) = lower(translate(:name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN'))) ",
      nativeQuery = true)
  boolean existsByNormalizedNameAndIdNot(
      @Param("studyPlanId") UUID studyPlanId, @Param("name") String name, @Param("id") UUID id);
}
