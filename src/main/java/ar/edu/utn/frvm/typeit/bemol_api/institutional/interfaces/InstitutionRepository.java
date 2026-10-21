package ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import jakarta.persistence.LockModeType;
import java.time.Instant;
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
import org.springframework.transaction.annotation.Transactional;

public interface InstitutionRepository extends JpaRepository<Institution, UUID> {
  Optional<Institution> findBySlug(String slug);

  @Transactional(readOnly = true)
  Optional<Institution> findByPublicSubdomainAndActiveTrue(String publicSubdomain);

  boolean existsByPublicSubdomainAndIdNot(String publicSubdomain, UUID id);

  @EntityGraph(attributePaths = {"city", "city.province"})
  Page<Institution> findByActiveTrue(Pageable pageable);

  @EntityGraph(attributePaths = {"city", "city.province", "city.province.country"})
  @Query("select institution from Institution institution")
  Page<Institution> findAllWithLocation(Pageable pageable);

  @EntityGraph(attributePaths = {"city", "city.province", "city.province.country"})
  @Query(
      """
      SELECT institution FROM Institution institution
      JOIN institution.city city
      JOIN city.province province
      JOIN province.country country
      WHERE (:active IS NULL OR institution.active = :active)
        AND (
          :search IS NULL
          OR UNACCENT_LOWER(institution.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR UNACCENT_LOWER(institution.slug) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR UNACCENT_LOWER(city.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR UNACCENT_LOWER(province.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR UNACCENT_LOWER(country.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
        )
      """)
  Page<Institution> findWithLocationByFilters(
      @Param("search") @Nullable String search,
      @Param("active") @Nullable Boolean active,
      Pageable pageable);

  @EntityGraph(attributePaths = {"city", "city.province"})
  Optional<Institution> findByIdAndActiveTrue(UUID id);

  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query(
      "SELECT institution FROM Institution institution WHERE institution.id = :id AND institution.active = true")
  Optional<Institution> findActiveByIdForShare(@Param("id") UUID id);

  @EntityGraph(attributePaths = {"city", "city.province", "city.province.country"})
  Optional<Institution> findWithLocationById(UUID id);

  boolean existsBySlug(String slug);

  boolean existsBySlugAndIdNot(String slug, UUID id);

  long countByActiveTrue();

  @Query(
      value =
          """
          SELECT
            (SELECT COUNT(*) FROM institutions) AS institutions,
            (SELECT COUNT(*) FROM institutions WHERE active = true) AS "activeInstitutions",
            (SELECT COUNT(*) FROM people WHERE deleted = false) AS people,
            (
              SELECT COUNT(*)
              FROM users platform_user
              JOIN people person ON person.person_id = platform_user.person_id
              JOIN institutions institution
                ON institution.institution_id = platform_user.institution_id
              WHERE platform_user.enabled = true
                AND person.deleted = false
                AND institution.active = true
            ) AS "usersWithAccess"
          """,
      nativeQuery = true)
  PlatformDashboardSummaryCounts getPlatformDashboardSummaryCounts();

  @Query(
      """
      SELECT YEAR(institution.createdAt) AS year,
             MONTH(institution.createdAt) AS month,
             COUNT(institution) AS institutionCount
      FROM Institution institution
      WHERE institution.createdAt >= :from AND institution.createdAt < :until
      GROUP BY YEAR(institution.createdAt), MONTH(institution.createdAt)
      ORDER BY YEAR(institution.createdAt), MONTH(institution.createdAt)
      """)
  List<MonthlyInstitutionCount> countCreatedByMonth(
      @Param("from") Instant from, @Param("until") Instant until);

  @EntityGraph(attributePaths = {"city", "city.province"})
  List<Institution> findTop5ByOrderByCreatedAtDesc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT institution FROM Institution institution WHERE institution.id = :id")
  Optional<Institution> findByIdForUpdate(@Param("id") UUID id);
}
