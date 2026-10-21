package ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceType;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AcademicSpaceRepository extends JpaRepository<AcademicSpace, UUID> {
  @EntityGraph(attributePaths = "institution")
  @Query(
      """
      SELECT space FROM AcademicSpace space
      WHERE (:institutionId IS NULL OR space.institution.id = :institutionId)
        AND ((:deleted = true AND space.deletedAt IS NOT NULL) OR (:deleted = false AND space.deletedAt IS NULL))
        AND (:active IS NULL OR space.active = :active)
        AND (:type IS NULL OR space.type = :type)
        AND (:format IS NULL OR space.format = :format)
        AND (:search IS NULL OR UNACCENT_LOWER(space.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR UNACCENT_LOWER(space.institution.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%')))
      """)
  Page<AcademicSpace> findByFilters(
      @Param("institutionId") @Nullable UUID institutionId,
      @Param("search") @Nullable String search,
      @Param("active") @Nullable Boolean active,
      @Param("type") @Nullable AcademicSpaceType type,
      @Param("format") @Nullable AcademicSpaceFormat format,
      @Param("deleted") boolean deleted,
      Pageable pageable);

  @Query(
      "SELECT space FROM AcademicSpace space WHERE space.id = :id AND space.institution.id = :institutionId AND space.deletedAt IS NULL")
  Optional<AcademicSpace> findByIdAndInstitution_Id(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT space FROM AcademicSpace space WHERE space.id = :id AND space.institution.id = :institutionId AND space.deletedAt IS NULL")
  Optional<AcademicSpace> findByIdAndInstitution_IdForUpdate(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  Optional<AcademicSpace> findByIdAndInstitution_IdAndActiveTrueAndDeletedAtIsNull(
      UUID id, UUID institutionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT space FROM AcademicSpace space WHERE space.id = :id AND space.institution.id = :institutionId")
  Optional<AcademicSpace> findByIdAndInstitution_IdForLifecycle(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  @Query(
      value =
          "SELECT EXISTS (SELECT 1 FROM academic_spaces WHERE institution_id = :institutionId AND deleted_at IS NULL AND type = :type AND format = :format AND lower(translate(name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN')) = lower(translate(:name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN'))) ",
      nativeQuery = true)
  boolean existsByNormalizedNameAndTypeAndFormat(
      @Param("institutionId") UUID institutionId,
      @Param("name") String name,
      @Param("type") String type,
      @Param("format") String format);

  @Query(
      value =
          "SELECT EXISTS (SELECT 1 FROM academic_spaces WHERE institution_id = :institutionId AND deleted_at IS NULL AND academic_space_id <> :id AND type = :type AND format = :format AND lower(translate(name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN')) = lower(translate(:name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN'))) ",
      nativeQuery = true)
  boolean existsByNormalizedNameAndTypeAndFormatAndIdNot(
      @Param("institutionId") UUID institutionId,
      @Param("name") String name,
      @Param("type") String type,
      @Param("format") String format,
      @Param("id") UUID id);

  @Query(
      "SELECT COUNT(space) > 0 FROM StudyPlanSpace space WHERE space.academicSpace.id = :academicSpaceId AND space.studyPlan.deletedAt IS NULL AND space.studyPlan.status IN (ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus.DRAFT, ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus.ACTIVE)")
  boolean existsInEditableOrActivePlan(@Param("academicSpaceId") UUID academicSpaceId);

  @Query(
      "SELECT COUNT(course) > 0 FROM Course course WHERE course.institution.id = :institutionId AND course.academicSpaceId = :academicSpaceId")
  boolean existsInCourse(
      @Param("institutionId") UUID institutionId, @Param("academicSpaceId") UUID academicSpaceId);

  @Query(
      "SELECT COUNT(course) > 0 FROM Course course WHERE course.institution.id = :institutionId AND course.academicSpaceId = :academicSpaceId AND course.deletedAt IS NULL")
  boolean existsInNonDeletedCourse(
      @Param("institutionId") UUID institutionId, @Param("academicSpaceId") UUID academicSpaceId);
}
