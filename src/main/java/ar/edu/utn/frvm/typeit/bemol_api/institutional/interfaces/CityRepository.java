package ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.City;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CityRepository extends JpaRepository<City, UUID> {
  Optional<City> findByProvinceIdAndGeorefId(UUID provinceId, String georefId);

  Optional<City> findByGeorefId(String georefId);

  @Query(
      """
      SELECT c FROM City c JOIN FETCH c.province p
      WHERE       UNACCENT_LOWER(c.name) LIKE UNACCENT_LOWER(CONCAT('%', :search, '%'))
             OR UNACCENT_LOWER(p.name) LIKE UNACCENT_LOWER(CONCAT('%', :search, '%'))
      """)
  Page<City> searchByNameOrProvince(@Param("search") String search, Pageable pageable);

  @Query(
      """
      SELECT c FROM City c JOIN FETCH c.province p
      WHERE p.id = :provinceId
        AND UNACCENT_LOWER(c.name) LIKE UNACCENT_LOWER(CONCAT('%', :search, '%'))
      """)
  Page<City> searchByProvinceAndName(
      @Param("provinceId") UUID provinceId, @Param("search") String search, Pageable pageable);

  @EntityGraph(attributePaths = {"province"})
  Page<City> findByProvinceId(UUID provinceId, Pageable pageable);

  @EntityGraph(attributePaths = {"province"})
  Page<City> findAll(Pageable pageable);
}
