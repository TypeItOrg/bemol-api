package ar.edu.utn.frvm.typeit.bemol_api;

import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.anonymous;
import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.authentication;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.StartEnrollmentApplicationRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class EnrollmentDynamicAnswersSecurityPostgresIntegrationTest
    extends EnrollmentDynamicFormTestSupport {
  @Autowired private MockMvc mvc;

  @ParameterizedTest
  @ValueSource(strings = {"\\u0000", "\\uD800", "\\uDC00"})
  void unsupportedJsonTextIsRejectedBeforeChangingAnswersOrRevision(final String escaped)
      throws Exception {
    final var fixture = fixture();
    final var saved = save(fixture, 0, completeAnswers(), false);
    final var principal = principal(fixture.institution(), fixture.person(), fixture.person());
    final var request =
        """
        {"expectedRevision":1,"data":{"customAnswers":{"%s":"Texto %s inválido"}}}
        """
            .formatted(TEXT, escaped);

    mvc.perform(
            patch(path(fixture.institution(), fixture.person(), fixture.application()) + "/draft")
                .with(authentication(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isBadRequest());

    final var after = read(fixture);
    assertThat(after.draftRevision()).isEqualTo(saved.draftRevision());
    assertCompleteAnswers(answers(after));
    assertThat(
            count(
                "SELECT count(*) FROM audit_events WHERE entity_id=? AND action='ENROLLMENT_APPLICATION_DRAFT_UPDATED'",
                fixture.application()))
        .isEqualTo(1);
  }

  @Test
  void theHttpContractRequiresRevisionsForVersionedSavesAndSubmission() throws Exception {
    final var fixture = fixture();
    final var principal = principal(fixture.institution(), fixture.person(), fixture.person());
    final var path = path(fixture.institution(), fixture.person(), fixture.application());
    mvc.perform(get(path).with(authentication(principal)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.formVersionId").value(fixture.version().toString()))
        .andExpect(jsonPath("$.draftRevision").value(0))
        .andExpect(jsonPath("$.formDefinition.sections").isArray());
    mvc.perform(
            patch(path + "/draft")
                .with(authentication(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":{\"customAnswers\":{}}}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            patch(path + "/draft")
                .with(authentication(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":-1,\"data\":{}}"))
        .andExpect(status().isBadRequest());
    mvc.perform(post(path + "/submit").with(authentication(principal)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post(path + "/submit")
                .with(authentication(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":-1}"))
        .andExpect(status().isBadRequest());

    final String data =
        """
        {"expectedRevision":0,"data":{"customAnswers":{"%s":"Respuesta privada HTTP","%s":false,"%s":0},
        "courses":[{"courseId":"%s","preferredTeacherId":null}]}}
        """
            .formatted(TEXT, BOOLEAN, NUMBER, fixture.course());
    mvc.perform(
            patch(path + "/draft")
                .with(authentication(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content(data))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.draftRevision").value(1))
        .andExpect(jsonPath("$.data.customAnswers['" + BOOLEAN + "']").value(false))
        .andExpect(jsonPath("$.data.customAnswers['" + NUMBER + "']").value(0));
    mvc.perform(
            patch(path + "/draft")
                .with(authentication(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content(data))
        .andExpect(status().isConflict());
    mvc.perform(
            post(path + "/submit")
                .with(authentication(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":0}"))
        .andExpect(status().isConflict());
    mvc.perform(
            post(path + "/submit")
                .with(authentication(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.draftRevision").value(2));
  }

  @Test
  void saveAndSubmitRequireTheDraftRevision() throws Exception {
    final var context = academicContext();
    final var draft =
        applications.startOrGetApplication(
            context.institution(),
            context.person(),
            new StartEnrollmentApplicationRequest(context.path(), null, context.year()));
    final var principal = principal(context.institution(), context.person(), context.person());
    final var path =
        path(context.institution(), context.person(), requireNonNull(draft.applicationId()));
    final var data =
        """
        {"data":{"academicBackground":{"currentlyStudying":false,"educationLevel":"NO_SCHOOLING"},
        "preference":{"preferredShift":"MORNING"},"courses":[{"courseId":"%s","preferredTeacherId":null}]}}
        """
            .formatted(context.course());
    mvc.perform(
            patch(path + "/draft")
                .with(authentication(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content(data))
        .andExpect(status().isBadRequest());
    mvc.perform(post(path + "/submit").with(authentication(principal)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void otherSubjectsAndInstitutionsCannotReadOrOverwriteDynamicAnswers() throws Exception {
    final var fixture = fixture();
    save(fixture, 0, completeAnswers(), true);
    final var other = fixture();
    final var principal = principal(other.institution(), other.person(), other.person());
    final var originalPath = path(fixture.institution(), fixture.person(), fixture.application());
    final var foreignPath = path(other.institution(), other.person(), fixture.application());
    mvc.perform(get(originalPath).with(anonymous())).andExpect(status().isUnauthorized());
    final var forbidden =
        mvc.perform(get(originalPath).with(authentication(principal)))
            .andExpect(status().isForbidden())
            .andReturn()
            .getResponse()
            .getContentAsString();
    final var absent =
        mvc.perform(get(foreignPath).with(authentication(principal)))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    mvc.perform(
            patch(foreignPath + "/draft")
                .with(authentication(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":1,\"data\":{\"customAnswers\":{}}}"))
        .andExpect(status().isNotFound());
    assertThat(forbidden).doesNotContain("Respuesta privada", "Detalle privado", "customAnswers");
    assertThat(absent).doesNotContain("Respuesta privada", "Detalle privado", "customAnswers");
    assertCompleteAnswers(answers(read(fixture)));
  }

  @Test
  void representedAnswersRequireTheSelectedSubjectAndAnActiveLink() throws Exception {
    final var fixture = fixture();
    save(fixture, 0, completeAnswers(), true);
    final var link =
        tx(
            () -> {
              final var institution =
                  requireNonNull(entities.find(Institution.class, fixture.institution()));
              final var dependent = requireNonNull(entities.find(Person.class, fixture.person()));
              final var guardian = persist(InstitutionalTestData.person(institution, "99331001"));
              final var value =
                  PersonGuardian.request(
                      institution, guardian, dependent, GuardianRelationship.LEGAL_GUARDIAN, true);
              value.declareContact(dependent.getPhoneNumber(), dependent.getNationalityCountry());
              value.approve(null);
              persist(value);
              entities.flush();
              return new Representation(guardian.getId(), value.getId());
            });
    final var principal = principal(fixture.institution(), link.guardian(), fixture.person());
    final var selectedPath = path(fixture.institution(), fixture.person(), fixture.application());
    mvc.perform(get(selectedPath).with(authentication(principal)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.customAnswers['" + TEXT + "']").value("Respuesta privada breve"));
    mvc.perform(
            get(path(fixture.institution(), link.guardian(), fixture.application()))
                .with(authentication(principal)))
        .andExpect(status().isForbidden());
    tx(
        () -> {
          final var stored = requireNonNull(entities.find(PersonGuardian.class, link.link()));
          stored.end();
          return stored;
        });

    mvc.perform(get(selectedPath).with(authentication(principal)))
        .andExpect(status().isForbidden());
    mvc.perform(
            patch(selectedPath + "/draft")
                .with(authentication(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":1,\"data\":{\"customAnswers\":{}}}"))
        .andExpect(status().isForbidden());
    assertCompleteAnswers(answers(read(fixture)));
  }

  private Authentication principal(final UUID institution, final UUID actor, final UUID selected) {
    final UUID user = UUID.randomUUID(), session = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users(user_id,person_id,institution_id,password,enabled,created_at,updated_at) VALUES (?,?,?,'test-only',true,now(),now())",
        user,
        actor,
        institution);
    jdbcTemplate.update(
        "INSERT INTO user_sessions(user_session_id,user_id,started_at,active,remember_me,active_person_id) VALUES (?,?,now(),true,false,?)",
        session,
        user,
        selected);
    final var document =
        requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT document_number FROM people WHERE person_id=?", String.class, selected));
    final var identity =
        JwtAuthenticatedUser.builder()
            .personId(selected)
            .documentNumber(document)
            .institutionId(institution)
            .authenticatedPersonId(actor)
            .authenticatedUserId(user)
            .sessionId(session)
            .tokenId("dynamic-answer-test")
            .build();
    return new TestingAuthenticationToken(identity, "", "ROLE_USER");
  }

  private String path(final UUID institution, final UUID person, final UUID application) {
    return "/api/v1/institutions/"
        + institution
        + "/self-service/"
        + person
        + "/enrollment-applications/"
        + application;
  }

  private record Representation(UUID guardian, UUID link) {}
}
