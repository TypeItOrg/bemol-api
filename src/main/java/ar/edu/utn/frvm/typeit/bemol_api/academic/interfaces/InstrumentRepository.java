package ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Instrument;
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

public interface InstrumentRepository extends JpaRepository<Instrument, UUID> {
  @EntityGraph(attributePaths = "institution")
  @Query(
      """
      SELECT instrument FROM Instrument instrument
      WHERE (:institutionId IS NULL OR instrument.institution.id = :institutionId)
        AND ((:deleted = true AND instrument.deletedAt IS NOT NULL) OR (:deleted = false AND instrument.deletedAt IS NULL))
        AND (:active IS NULL OR instrument.active = :active)
        AND (:search IS NULL OR UNACCENT_LOWER(instrument.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR UNACCENT_LOWER(instrument.institution.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%')))
      """)
  Page<Instrument> findByFilters(
      @Param("institutionId") @Nullable UUID institutionId,
      @Param("search") @Nullable String search,
      @Param("active") @Nullable Boolean active,
      @Param("deleted") boolean deleted,
      Pageable pageable);

  @Query(
      "SELECT instrument FROM Instrument instrument WHERE instrument.id = :id AND instrument.institution.id = :institutionId AND instrument.deletedAt IS NULL")
  Optional<Instrument> findByIdAndInstitution_Id(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT instrument FROM Instrument instrument WHERE instrument.id = :id AND instrument.institution.id = :institutionId")
  Optional<Instrument> findByIdAndInstitution_IdForLifecycle(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  @Query(
      value =
          "SELECT EXISTS (SELECT 1 FROM instruments WHERE institution_id = :institutionId AND deleted_at IS NULL AND lower(translate(name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN')) = lower(translate(:name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN'))) ",
      nativeQuery = true)
  boolean existsByNormalizedName(
      @Param("institutionId") UUID institutionId, @Param("name") String name);

  @Query(
      value =
          "SELECT EXISTS (SELECT 1 FROM instruments WHERE institution_id = :institutionId AND deleted_at IS NULL AND instrument_id <> :id AND lower(translate(name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN')) = lower(translate(:name, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN'))) ",
      nativeQuery = true)
  boolean existsByNormalizedNameAndIdNot(
      @Param("institutionId") UUID institutionId, @Param("name") String name, @Param("id") UUID id);
}
