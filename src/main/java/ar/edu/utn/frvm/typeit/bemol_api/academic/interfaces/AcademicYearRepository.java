package ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AcademicYearRepository
    extends JpaRepository<AcademicYear, UUID>, JpaSpecificationExecutor<AcademicYear> {
  @Override
  @EntityGraph(attributePaths = "institution")
  Page<AcademicYear> findAll(Specification<AcademicYear> specification, Pageable pageable);

  @Query(
      "SELECT year FROM AcademicYear year WHERE year.id = :id AND year.institution.id = :institutionId AND year.deletedAt IS NULL")
  Optional<AcademicYear> findByIdAndInstitution_Id(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT year FROM AcademicYear year WHERE year.id = :id AND year.institution.id = :institutionId AND year.deletedAt IS NULL")
  Optional<AcademicYear> findByIdAndInstitution_IdForUpdate(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT year FROM AcademicYear year WHERE year.id = :id AND year.institution.id = :institutionId")
  Optional<AcademicYear> findByIdAndInstitution_IdForLifecycle(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  boolean existsByInstitution_IdAndYearAndDeletedAtIsNull(UUID institutionId, int year);

  boolean existsByInstitution_IdAndYearAndIdNotAndDeletedAtIsNull(
      UUID institutionId, int year, UUID id);

  boolean existsByInstitution_IdAndStatusAndDeletedAtIsNull(
      UUID institutionId, AcademicYearStatus status);

  @Query(
      "SELECT year FROM AcademicYear year WHERE year.institution.id = :institutionId AND year.status = :status AND year.deletedAt IS NULL")
  List<AcademicYear> findAllByInstitutionIdAndStatus(
      @Param("institutionId") UUID institutionId, @Param("status") AcademicYearStatus status);
}
