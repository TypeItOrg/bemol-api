package ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Shift;
import jakarta.persistence.LockModeType;
import java.util.List;
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

public interface ShiftRepository extends JpaRepository<Shift, UUID> {
  @EntityGraph(attributePaths = "institution")
  @Query(
      """
      SELECT shift FROM Shift shift
      WHERE (:institutionId IS NULL OR shift.institution.id = :institutionId)
        AND ((:deleted = true AND shift.deletedAt IS NOT NULL) OR (:deleted = false AND shift.deletedAt IS NULL))
        AND (:active IS NULL OR shift.active = :active)
        AND (:search IS NULL OR UNACCENT_LOWER(shift.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR UNACCENT_LOWER(shift.institution.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%')))
      """)
  Page<Shift> findByFilters(
      @Param("institutionId") @Nullable UUID institutionId,
      @Param("search") @Nullable String search,
      @Param("active") @Nullable Boolean active,
      @Param("deleted") boolean deleted,
      Pageable pageable);

  List<Shift> findByInstitution_IdAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(UUID institutionId);

  @Query(
      "SELECT shift FROM Shift shift WHERE shift.id = :id AND shift.institution.id = :institutionId AND shift.deletedAt IS NULL")
  Optional<Shift> findByIdAndInstitution_Id(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT shift FROM Shift shift WHERE shift.id = :id AND shift.institution.id = :institutionId")
  Optional<Shift> findByIdAndInstitution_IdForLifecycle(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  @Query(
      value =
          "SELECT EXISTS (SELECT 1 FROM shifts WHERE institution_id = :institutionId AND deleted_at IS NULL AND lower(translate(name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN')) = lower(translate(:name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN'))) ",
      nativeQuery = true)
  boolean existsByNormalizedName(
      @Param("institutionId") UUID institutionId, @Param("name") String name);

  @Query(
      value =
          "SELECT EXISTS (SELECT 1 FROM shifts WHERE institution_id = :institutionId AND deleted_at IS NULL AND shift_id <> :id AND lower(translate(name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN')) = lower(translate(:name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN'))) ",
      nativeQuery = true)
  boolean existsByNormalizedNameAndIdNot(
      @Param("institutionId") UUID institutionId, @Param("name") String name, @Param("id") UUID id);
}
