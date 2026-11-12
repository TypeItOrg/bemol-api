package ar.edu.utn.frvm.typeit.bemol_api;

import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.anonymous;
import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.authentication;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class EnrollmentFormSecurityPostgresIntegrationTest extends DatabaseMigrationTestSupport {
  private static final AtomicInteger DOCUMENTS = new AtomicInteger(99118000);

  @Autowired private MockMvc mvc;
  @Autowired private EntityManager entities;
  @Autowired private TransactionTemplate transaction;

  @Test
  void anonymousRequestsCannotReadOrChangeEitherAdministrationSurface() throws Exception {
    SecurityContextHolder.clearContext();
    final UUID institution = fixtures.firstInstitutionId();
    for (final var prefix : new String[] {"/api/v1/institutions/", "/api/v1/admin/institutions/"}) {
      mvc.perform(get(prefix + institution + "/enrollment-forms").with(anonymous()))
          .andExpect(status().isUnauthorized());
      mvc.perform(
              post(prefix + institution + "/enrollment-form-assignments")
                  .with(anonymous())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isUnauthorized());
    }
  }

  @Test
  void institutionalAuthorityCanManageOwnFormsButNotAnotherInstitutionOrPlatform()
      throws Exception {
    final var identity = institutionalAuthority();
    final var principal = ownAuthentication(identity);
    final UUID other = UUID.randomUUID();
    fixtures.insertTestInstitution(other);
    final var base = "/api/v1/institutions/" + identity.institution() + "/enrollment-forms";

    mvc.perform(get(base).with(authentication(principal))).andExpect(status().isOk());
    mvc.perform(
            post(base)
                .with(authentication(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Formulario autorizado\"}"))
        .andExpect(status().isCreated());
    mvc.perform(
            get("/api/v1/institutions/" + other + "/enrollment-forms")
                .with(authentication(principal)))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/institutions/" + other + "/enrollment-forms")
                .with(authentication(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"No autorizado\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/admin/institutions/" + identity.institution() + "/enrollment-forms")
                .with(authentication(principal)))
        .andExpect(status().isForbidden());
    assertThat(countForms(other)).isZero();
  }

  @Test
  void selectingADependentDoesNotInheritTheActorsInstitutionManagementPermission()
      throws Exception {
    final var identity = institutionalAuthority();
    final var base = "/api/v1/institutions/" + identity.institution() + "/enrollment-forms";
    mvc.perform(get(base).with(authentication(ownAuthentication(identity))))
        .andExpect(status().isOk());
    final UUID dependentId = UUID.randomUUID();
    final var document = Integer.toString(DOCUMENTS.incrementAndGet());
    fixtures.insertPerson(dependentId, identity.institution(), document, false);
    assignRole(identity.institution(), dependentId, "APPLICANT");
    transaction.executeWithoutResult(
        status -> {
          final var dependent = requireNonNull(entities.find(Person.class, dependentId));
          final var guardian = requireNonNull(entities.find(Person.class, identity.person()));
          final var institution =
              requireNonNull(entities.find(Institution.class, identity.institution()));
          final var link =
              PersonGuardian.request(
                  institution, guardian, dependent, GuardianRelationship.LEGAL_GUARDIAN, true);
          link.declareContact(dependent.getPhoneNumber(), dependent.getNationalityCountry());
          link.approve(null);
          entities.persist(link);
        });
    jdbcTemplate.update(
        "UPDATE user_sessions SET active_person_id=? WHERE user_session_id=?",
        dependentId,
        identity.session());
    final var selected =
        JwtAuthenticatedUser.builder()
            .personId(dependentId)
            .documentNumber(document)
            .institutionId(identity.institution())
            .authenticatedPersonId(identity.person())
            .authenticatedUserId(identity.user())
            .sessionId(identity.session())
            .tokenId("form-security-test")
            .build();
    final Authentication delegated = new TestingAuthenticationToken(selected, "", "ROLE_USER");
    final long before = countForms(identity.institution());

    mvc.perform(get(base).with(authentication(delegated))).andExpect(status().isForbidden());
    mvc.perform(
            post(base)
                .with(authentication(delegated))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"No heredar permisos del actor\"}"))
        .andExpect(status().isForbidden());
    assertThat(countForms(identity.institution())).isEqualTo(before);
  }

  private Identity institutionalAuthority() {
    final UUID institution = fixtures.firstInstitutionId();
    final UUID person = UUID.randomUUID(), user = UUID.randomUUID(), session = UUID.randomUUID();
    final var document = Integer.toString(DOCUMENTS.incrementAndGet());
    fixtures.insertPerson(person, institution, document, false);
    assignRole(institution, person, "INSTITUTIONAL_AUTHORITY");
    jdbcTemplate.update(
        "INSERT INTO users(user_id,person_id,institution_id,password,enabled,created_at,updated_at) VALUES (?,?,?,'test-only',true,now(),now())",
        user,
        person,
        institution);
    jdbcTemplate.update(
        "INSERT INTO user_sessions(user_session_id,user_id,started_at,active,remember_me,active_person_id) VALUES (?,?,now(),true,false,?)",
        session,
        user,
        person);
    return new Identity(institution, person, user, session, document);
  }

  private Authentication ownAuthentication(final Identity identity) {
    return new TestingAuthenticationToken(
        new JwtAuthenticatedUser(
            identity.user(),
            identity.person(),
            identity.document(),
            identity.institution(),
            identity.session(),
            "form-security-test"),
        "",
        "ROLE_USER");
  }

  private void assignRole(final UUID institution, final UUID person, final String role) {
    assertThat(
            jdbcTemplate.update(
                "INSERT INTO person_role_assignments(person_role_assignment_id,person_id,role_id,institution_id,created_at,updated_at) SELECT ?,?,role_id,?,now(),now() FROM roles WHERE institution_id=? AND code=?",
                UUID.randomUUID(),
                person,
                institution,
                institution,
                role))
        .isEqualTo(1);
  }

  private long countForms(final UUID institution) {
    return requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM enrollment_forms WHERE institution_id=?",
            Long.class,
            institution));
  }

  private record Identity(
      UUID institution, UUID person, UUID user, UUID session, String document) {}
}
