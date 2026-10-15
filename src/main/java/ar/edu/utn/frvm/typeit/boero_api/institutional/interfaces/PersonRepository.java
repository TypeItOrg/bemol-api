package ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces;

import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
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

public interface PersonRepository extends JpaRepository<Person, UUID> {

  Optional<Person> findByIdAndInstitution_Id(UUID personId, UUID institutionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      SELECT person FROM Person person
      WHERE person.id = :personId AND person.institution.id = :institutionId
      """)
  Optional<Person> findByIdAndInstitutionIdForUpdate(
      @Param("personId") UUID personId, @Param("institutionId") UUID institutionId);

  @Query(
      """
      SELECT person FROM Person person
      WHERE person.documentNumber = :documentNumber
        AND person.institution.id = :institutionId
        AND person.deleted = false
      """)
  Optional<Person> findByDocumentNumberAndInstitution_Id(
      @Param("documentNumber") String documentNumber, @Param("institutionId") UUID institutionId);

  @Query(
      """
      SELECT COUNT(person) > 0 FROM Person person
      WHERE person.documentNumber = :documentNumber
        AND person.institution.id = :institutionId
        AND person.deleted = false
      """)
  boolean existsByDocumentNumberAndInstitution_Id(
      @Param("documentNumber") String documentNumber, @Param("institutionId") UUID institutionId);

  long countByDeletedFalse();

  Page<Person> findByInstitution_IdAndDeletedFalse(UUID institutionId, Pageable pageable);

  @Query(
      """
      SELECT p FROM Person p
      WHERE p.institution.id = :institutionId
        AND p.deleted = false
        AND (
          :roleId IS NULL
          OR EXISTS (
            SELECT assignment.id FROM PersonRoleAssignment assignment
            WHERE assignment.person = p
              AND assignment.institution.id = :institutionId
              AND assignment.role.id = :roleId
          )
        )
        AND (
          :search IS NULL
          OR (
            UNACCENT_LOWER(CONCAT(p.firstName, ' ', p.lastName))
                LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
            OR p.documentNumber LIKE CONCAT('%', CAST(:search AS string), '%')
          )
        )
      """)
  Page<Person> search(
      @Param("institutionId") UUID institutionId,
      @Param("search") @Nullable String search,
      @Param("roleId") @Nullable UUID roleId,
      Pageable pageable);

  @EntityGraph(attributePaths = "institution")
  @Query(
      """
      SELECT person FROM Person person
      WHERE person.deleted = false
        AND (:institutionId IS NULL OR person.institution.id = :institutionId)
        AND (
          :roleCode IS NULL
          OR EXISTS (
            SELECT assignment.id FROM PersonRoleAssignment assignment
            WHERE assignment.person = person
              AND assignment.role.code = :roleCode
          )
        )
        AND (
          :search IS NULL
          OR UNACCENT_LOWER(CONCAT(person.firstName, ' ', person.lastName))
              LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR person.documentNumber LIKE CONCAT('%', CAST(:search AS string), '%')
          OR UNACCENT_LOWER(person.email)
              LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
        )
      """)
  Page<Person> findPlatformPeople(
      @Param("search") @Nullable String search,
      @Param("institutionId") @Nullable UUID institutionId,
      @Param("roleCode") @Nullable String roleCode,
      Pageable pageable);

  @EntityGraph(
      attributePaths = {
        "address",
        "address.city",
        "address.city.province",
        "birthCity",
        "birthCity.province",
        "nationalityCountry",
        "institution"
      })
  Optional<Person> findWithDetailsByIdAndInstitution_Id(UUID personId, UUID institutionId);
}
