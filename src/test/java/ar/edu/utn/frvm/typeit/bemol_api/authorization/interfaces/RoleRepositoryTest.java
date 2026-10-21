package ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces;

import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.createInstitution;
import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.persist;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.support.JpaAuditingTestConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataJpaTest
@Import(JpaAuditingTestConfig.class)
class RoleRepositoryTest {

  @Autowired private EntityManager entityManager;
  @Autowired private RoleRepository roleRepository;

  @Test
  @DisplayName("Should filter and sort institutional roles across institutions")
  void findPlatformRoles_filtersAndSortsByInstitution() {
    Institution boero = createInstitution(entityManager, "boero");
    boero.rename("Boero");
    Institution alberdi = createInstitution(entityManager, "alberdi");
    alberdi.rename("Alberdi");
    persist(entityManager, role("Docentes", boero, true));
    persist(entityManager, role("Preceptores", alberdi, false));
    persist(entityManager, role("Dirección", boero, false));
    entityManager.flush();
    entityManager.clear();

    var result =
        roleRepository.findPlatformRoles(
            RoleScope.INSTITUTION,
            null,
            null,
            "direccion",
            PageRequest.of(0, 10, Sort.by(Sort.Order.asc("institution.name"))));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().getFirst().getName()).isEqualTo("Dirección");
    assertThat(result.getContent().getFirst().getInstitution().getName()).isEqualTo("Boero");
  }

  @Test
  @DisplayName("Should filter platform roles by institution and system flag")
  void findPlatformRoles_filtersByInstitutionAndSystem() {
    Institution boero = createInstitution(entityManager, "boero");
    Institution alberdi = createInstitution(entityManager, "alberdi");
    persist(entityManager, role("Docentes", boero, true));
    persist(entityManager, role("Preceptores", boero, false));
    persist(entityManager, role("Docentes", alberdi, true));
    entityManager.flush();
    entityManager.clear();

    var result =
        roleRepository.findPlatformRoles(
            RoleScope.INSTITUTION,
            boero.getId(),
            false,
            null,
            PageRequest.of(0, 10, Sort.by("name")));

    assertThat(result.getContent())
        .extracting(mappedRole -> mappedRole.getName())
        .containsExactly("Preceptores");
  }

  private static Role role(String name, Institution institution, boolean system) {
    return Role.builder()
        .code(system ? "SYSTEM_" + name : "CUSTOM_" + name)
        .name(name)
        .scope(RoleScope.INSTITUTION)
        .system(system)
        .institution(institution)
        .build();
  }
}
