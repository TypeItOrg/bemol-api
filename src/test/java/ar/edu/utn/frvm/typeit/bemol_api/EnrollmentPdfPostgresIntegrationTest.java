package ar.edu.utn.frvm.typeit.bemol_api;

import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.authentication;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailSender;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.pdf.render.EnrollmentPdfUnavailableException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentPdfRenderer;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.InstitutionLogoUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
@AutoConfigureMockMvc
class EnrollmentPdfPostgresIntegrationTest extends DatabaseMigrationTestSupport {
  private static final byte[] PDF = "%PDF-1.7\nfixture".getBytes(StandardCharsets.US_ASCII);
  private static final String SETTINGS =
      """
      {"title":"Mi planilla","headerText":"Encabezado privado","introductionText":null,
       "footerText":null,"showApplicantSignature":false,"showResponsibleSignature":true}
      """;
  @Autowired MockMvc mvc;
  @Autowired TransactionTemplate transaction;
  @Autowired EntityManager entities;
  @Autowired InstitutionLogoUseCase logos;
  @MockitoBean EnrollmentPdfRenderer renderer;
  @MockitoBean MailSender mailSender;

  @Test
  void settingsHaveDefaultsAreIsolatedAndPreviewDoesNotPersist() throws Exception {
    authenticatePlatformAdministrator();
    final var admin = requireNonNull(SecurityContextHolder.getContext().getAuthentication());
    final UUID institution = UUID.randomUUID(), other = UUID.randomUUID();
    fixtures.insertTestInstitution(institution);
    fixtures.insertTestInstitution(other);
    final var base = "/api/v1/admin/institutions/" + institution + "/enrollment-pdf-settings";
    mvc.perform(get(base).with(authentication(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Solicitud de inscripción"));
    assertThat(
            count(
                "SELECT count(*) FROM enrollment_pdf_settings WHERE institution_id=?", institution))
        .isZero();
    when(renderer.render(any())).thenReturn(PDF);
    mvc.perform(
            post(base + "/preview")
                .with(authentication(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SETTINGS))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(header().string("Cache-Control", "private, no-store"));
    assertThat(
            count(
                "SELECT count(*) FROM enrollment_pdf_settings WHERE institution_id=?", institution))
        .isZero();
    mvc.perform(
            put(base)
                .with(authentication(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SETTINGS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.showApplicantSignature").value(false));
    final long audits =
        count("SELECT count(*) FROM audit_events WHERE institution_id=?", institution);
    mvc.perform(
            put(base)
                .with(authentication(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SETTINGS))
        .andExpect(status().isOk());
    assertThat(count("SELECT count(*) FROM audit_events WHERE institution_id=?", institution))
        .isEqualTo(audits);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT details::text FROM audit_events WHERE institution_id=?",
                String.class,
                institution))
        .allSatisfy(
            details -> assertThat(details).doesNotContain("Encabezado privado", "Mi planilla"));
    mvc.perform(
            get("/api/v1/institutions/" + other + "/enrollment-pdf-settings")
                .with(authentication(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Solicitud de inscripción"));
    mvc.perform(
            put(base)
                .with(authentication(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Válido\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.showApplicantSignature").exists());
    mvc.perform(
            put(base)
                .with(authentication(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SETTINGS.replace("Mi planilla", "x".repeat(151))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void platformAndInstitutionDownloadsUsePrivateHeadersAndDoNotReadDocumentMetadata()
      throws Exception {
    authenticatePlatformAdministrator();
    final var admin = requireNonNull(SecurityContextHolder.getContext().getAuthentication());
    final UUID institution = fixtures.firstInstitutionId(),
        person = UUID.randomUUID(),
        application = UUID.randomUUID();
    fixtures.insertPerson(person, institution, "88114001", false);
    insertApplication(application, institution, person);
    when(renderer.render(any())).thenReturn(PDF);
    final long documentAudits =
        count("SELECT count(*) FROM enrollment_document_audit WHERE application_id=?", application);
    for (final var path :
        new String[] {
          "/api/v1/admin/enrollment-applications/" + institution + "/" + application + "/pdf",
          "/api/v1/institutions/" + institution + "/enrollment-applications/" + application + "/pdf"
        }) {
      mvc.perform(get(path).with(authentication(admin)))
          .andExpect(status().isOk())
          .andExpect(content().bytes(PDF))
          .andExpect(content().contentType(MediaType.APPLICATION_PDF))
          .andExpect(header().string("Cache-Control", "private, no-store"))
          .andExpect(header().string("X-Content-Type-Options", "nosniff"))
          .andExpect(
              header()
                  .string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")));
    }
    assertThat(
            count(
                "SELECT count(*) FROM enrollment_document_audit WHERE application_id=?",
                application))
        .isEqualTo(documentAudits);
    final var data = ArgumentCaptor.forClass(EnrollmentPdfData.class);
    verify(renderer, times(2)).render(data.capture());
    assertThat(data.getValue().submittedAt()).isNull();
    assertThat(
            data.getValue().sections().stream()
                .flatMap(section -> section.fields().stream())
                .map(field -> field.value()))
        .contains("Sin informar");
    clearInvocations(renderer);
    jdbcTemplate.update(
        "UPDATE enrollment_applications SET status='DRAFT' WHERE enrollment_application_id=?",
        application);
    mvc.perform(
            get("/api/v1/admin/enrollment-applications/" + institution + "/" + application + "/pdf")
                .with(authentication(admin)))
        .andExpect(status().isConflict());
    verifyNoInteractions(renderer);
    mvc.perform(
            get("/api/v1/admin/enrollment-applications/"
                    + UUID.randomUUID()
                    + "/"
                    + application
                    + "/pdf")
                .with(authentication(admin)))
        .andExpect(status().isNotFound());
  }

  @Test
  void selfServiceRequiresSelectedSubjectAndActiveRepresentation() throws Exception {
    final UUID institution = fixtures.firstInstitutionId(),
        person = UUID.randomUUID(),
        tutor = UUID.randomUUID(),
        application = UUID.randomUUID();
    fixtures.insertPerson(person, institution, "88114002", false);
    fixtures.insertPerson(tutor, institution, "88114003", false);
    jdbcTemplate.update(
        "INSERT INTO person_role_assignments(person_role_assignment_id,person_id,role_id,institution_id,created_at,updated_at) SELECT ?,?,role_id,?,now(),now() FROM roles WHERE institution_id=? AND code='APPLICANT'",
        UUID.randomUUID(),
        person,
        institution,
        institution);
    final var link =
        transaction.execute(
            status -> {
              final var dependent = entities.find(Person.class, person);
              final var guardian = entities.find(Person.class, tutor);
              final var value =
                  PersonGuardian.request(
                      entities.find(Institution.class, institution),
                      guardian,
                      dependent,
                      GuardianRelationship.LEGAL_GUARDIAN,
                      true);
              value.declareContact(dependent.getPhoneNumber(), dependent.getNationalityCountry());
              value.approve(null);
              entities.persist(value);
              entities.flush();
              return value.getId();
            });
    insertApplication(application, institution, person);
    when(renderer.render(any())).thenReturn(PDF);
    final UUID tutorUser = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users(user_id,person_id,institution_id,password,enabled,created_at,updated_at) VALUES (?,?,?,'test-only',true,now(),now())",
        tutorUser,
        tutor,
        institution);
    final UUID session = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO user_sessions(user_session_id,user_id,started_at,active,remember_me,active_person_id) VALUES (?,?,now(),true,false,?)",
        session,
        tutorUser,
        person);
    final var delegated =
        JwtAuthenticatedUser.builder()
            .personId(person)
            .documentNumber("88114002")
            .institutionId(institution)
            .authenticatedPersonId(tutor)
            .authenticatedUserId(tutorUser)
            .sessionId(session)
            .tokenId("test")
            .build();
    final Authentication selected = new TestingAuthenticationToken(delegated, "", "ROLE_USER");
    final var base = "/api/v1/institutions/" + institution + "/self-service/";
    mvc.perform(
            get(base + person + "/enrollment-applications/" + application + "/pdf")
                .with(authentication(selected)))
        .andExpect(status().isOk());
    clearInvocations(renderer);
    mvc.perform(
            get(base + tutor + "/enrollment-applications/" + application + "/pdf")
                .with(authentication(selected)))
        .andExpect(status().isForbidden());
    jdbcTemplate.update(
        "UPDATE person_guardians SET status='ENDED' WHERE person_guardian_id=?", link);
    mvc.perform(
            get(base + person + "/enrollment-applications/" + application + "/pdf")
                .with(authentication(selected)))
        .andExpect(status().isForbidden());
    verifyNoInteractions(renderer);
  }

  @Test
  void rendererUnavailableHasRetryAfterAndLogoReadingAllowsInactiveInstitutions() throws Exception {
    authenticatePlatformAdministrator();
    final var admin = requireNonNull(SecurityContextHolder.getContext().getAuthentication());
    final UUID institution = UUID.randomUUID();
    fixtures.insertTestInstitution(institution);
    jdbcTemplate.update("UPDATE institutions SET active=false WHERE institution_id=?", institution);
    assertThat(logos.getForExport(institution)).isEmpty();
    when(renderer.render(any())).thenThrow(new EnrollmentPdfUnavailableException());
    mvc.perform(
            post("/api/v1/admin/institutions/" + institution + "/enrollment-pdf-settings/preview")
                .with(authentication(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SETTINGS))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "5"));
  }

  private long count(final String sql, final UUID id) {
    return requireNonNull(jdbcTemplate.queryForObject(sql, Long.class, id));
  }

  private void insertApplication(
      final UUID application, final UUID institution, final UUID person) {
    final UUID path = UUID.randomUUID();
    fixtures.insertTrainingPath(path, institution);
    final var years =
        jdbcTemplate.queryForList(
            "SELECT academic_year_id FROM academic_years WHERE institution_id=? AND year=2031",
            UUID.class,
            institution);
    final UUID year = years.isEmpty() ? UUID.randomUUID() : years.getFirst();
    if (years.isEmpty()) {
      fixtures.insertAcademicYear(year, institution, 2031);
    }
    jdbcTemplate.update(
        "INSERT INTO enrollment_applications(enrollment_application_id,institution_id,applicant_person_id,training_path_id,academic_year_id,status,created_at,updated_at) VALUES (?,?,?,?,?,'SUBMITTED',now(),now())",
        application,
        institution,
        person,
        path,
        year);
  }
}
