package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.ShiftNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicYearStatusRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.ActiveStatusRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateAcademicSpaceRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateAcademicYearRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateInstrumentRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateShiftRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateTrainingPathRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateAcademicSpaceRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateAcademicYearRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateInstrumentRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateShiftRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateTrainingPathRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.AcademicLifecycleService;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreateAcademicSpaceUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreateAcademicYearUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreateInstrumentUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreateShiftUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreateTrainingPathUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateAcademicSpaceStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateAcademicSpaceUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateAcademicYearStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateAcademicYearUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateInstrumentStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateInstrumentUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateShiftStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateShiftUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateTrainingPathStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateTrainingPathUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailSender;
import ar.edu.utn.frvm.typeit.bemol_api.common.storage.StorageService;
import ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@IntegrationTest
@AutoConfigureMockMvc
class AcademicCatalogAuditPostgresIntegrationTest extends DatabaseMigrationTestSupport {
  @Autowired CreateShiftUseCase createShift;
  @Autowired UpdateShiftUseCase updateShift;
  @Autowired UpdateShiftStatusUseCase updateShiftStatus;
  @Autowired CreateInstrumentUseCase createInstrument;
  @Autowired UpdateInstrumentUseCase updateInstrument;
  @Autowired UpdateInstrumentStatusUseCase updateInstrumentStatus;
  @Autowired CreateTrainingPathUseCase createTrainingPath;
  @Autowired UpdateTrainingPathUseCase updateTrainingPath;
  @Autowired UpdateTrainingPathStatusUseCase updateTrainingPathStatus;
  @Autowired CreateAcademicSpaceUseCase createAcademicSpace;
  @Autowired UpdateAcademicSpaceUseCase updateAcademicSpace;
  @Autowired UpdateAcademicSpaceStatusUseCase updateAcademicSpaceStatus;
  @Autowired CreateAcademicYearUseCase createAcademicYear;
  @Autowired UpdateAcademicYearUseCase updateAcademicYear;
  @Autowired UpdateAcademicYearStatusUseCase updateAcademicYearStatus;
  @Autowired AcademicLifecycleService lifecycle;
  @Autowired TransactionTemplate transaction;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockitoBean MailSender mailSender;
  @MockitoSpyBean StorageService storageService;

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/admin/institutions", "/api/v1/institutions"})
  void shiftHttpLifecyclePersistsChangesAndEnforcesValidationTenantAndUniqueness(
      final String routePrefix) throws Exception {
    authenticatePlatformAdministrator();
    final var admin = requireNonNull(SecurityContextHolder.getContext().getAuthentication());
    final UUID institution = UUID.randomUUID(), otherInstitution = UUID.randomUUID();
    fixtures.insertTestInstitution(institution);
    fixtures.insertTestInstitution(otherInstitution);
    final String base = routePrefix + "/" + institution + "/shifts";

    mvc.perform(get(base).with(anonymous())).andExpect(status().isUnauthorized());
    mvc.perform(
            get(base)
                .with(
                    authentication(
                        new TestingAuthenticationToken(
                            AuthTestData.platformPrincipal(UUID.randomUUID()), "", "ROLE_USER"))))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(base)
                .with(authentication(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.name").exists());
    mvc.perform(
            post(routePrefix + "/{id}/shifts", UUID.randomUUID())
                .with(authentication(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Turno mañana\"}"))
        .andExpect(status().isNotFound());

    final var created =
        json.readTree(
            mvc.perform(
                    post(base)
                        .with(authentication(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Turno   mañana  \",\"description\":\"De 8 a 12\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Turno mañana"))
                .andExpect(jsonPath("$.institutionId").value(institution.toString()))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString());
    final UUID shift = UUID.fromString(created.path("id").asString());
    final String resource = base + "/" + shift;
    mvc.perform(get(resource).with(authentication(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("De 8 a 12"));
    mvc.perform(
            post(base)
                .with(authentication(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"turno manana\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.fieldErrors.name").exists());
    mvc.perform(
            put(resource)
                .with(authentication(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Turno mañana\",\"description\":\"Horario ampliado\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Horario ampliado"));
    mvc.perform(
            put(resource)
                .with(authentication(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" Turno   tarde \",\"description\":\"De 13 a 17\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Turno tarde"));

    createShift.execute(institution, new CreateShiftRequest("Turno noche", null));
    createShift.execute(otherInstitution, new CreateShiftRequest("Turno tarde", null));
    mvc.perform(
            put(resource)
                .with(authentication(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Turno noche\"}"))
        .andExpect(status().isConflict());
    mvc.perform(
            get(routePrefix + "/{id}/shifts/{shift}", otherInstitution, shift)
                .with(authentication(admin)))
        .andExpect(status().isNotFound());
    final String missing = base + "/" + UUID.randomUUID();
    mvc.perform(get(missing).with(authentication(admin))).andExpect(status().isNotFound());
    mvc.perform(
            put(missing)
                .with(authentication(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Turno faltante\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            patch(missing + "/status")
                .with(authentication(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
        .andExpect(status().isNotFound());

    mvc.perform(
            get(base)
                .with(authentication(admin))
                .param("search", "  tarde ")
                .param("active", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(shift.toString()));
    mvc.perform(delete(resource).with(authentication(admin))).andExpect(status().isConflict());
    mvc.perform(get(resource).with(authentication(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Turno tarde"))
        .andExpect(jsonPath("$.active").value(true));
    mvc.perform(
            patch(resource + "/status")
                .with(authentication(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
        .andExpect(status().isNoContent());
    mvc.perform(
            get(base).with(authentication(admin)).param("search", "tarde").param("active", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(0));
    mvc.perform(
            get(base).with(authentication(admin)).param("search", "tarde").param("active", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(shift.toString()));

    mvc.perform(delete(resource).with(authentication(admin))).andExpect(status().isNoContent());
    mvc.perform(get(resource).with(authentication(admin))).andExpect(status().isNotFound());
    mvc.perform(get(base).with(authentication(admin)).param("search", "tarde"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(0));
    mvc.perform(
            get(base).with(authentication(admin)).param("search", "tarde").param("deleted", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(shift.toString()))
        .andExpect(jsonPath("$.items[0].active").value(false));
    mvc.perform(post(resource + "/restore").with(authentication(admin)))
        .andExpect(status().isNoContent());
    mvc.perform(get(resource).with(authentication(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Turno tarde"))
        .andExpect(jsonPath("$.active").value(false));
    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT name, active, deleted_at FROM shifts WHERE shift_id = ?", shift))
        .containsEntry("name", "Turno tarde")
        .containsEntry("active", false)
        .containsEntry("deleted_at", null);
  }

  @Test
  void auditsCatalogLifecycleEffectiveChangesPrivacyRollbackAndTenantFilters() throws Exception {
    authenticatePlatformAdministrator();
    final var admin = requireNonNull(SecurityContextHolder.getContext().getAuthentication());
    final UUID institution = UUID.randomUUID(), otherInstitution = UUID.randomUUID();
    fixtures.insertTestInstitution(institution);
    fixtures.insertTestInstitution(otherInstitution);
    final String privateDescription = "Descripción privada no auditable";
    final UUID shift =
        createShift
            .execute(institution, new CreateShiftRequest("Turno auditado", privateDescription))
            .id();
    final var shiftChange = new UpdateShiftRequest("Turno actualizado", privateDescription);
    updateShift.execute(institution, shift, shiftChange);
    updateShift.execute(institution, shift, shiftChange);
    updateShiftStatus.execute(institution, shift, new ActiveStatusRequest(false));
    updateShiftStatus.execute(institution, shift, new ActiveStatusRequest(false));
    lifecycle.deleteShift(institution, shift, null);
    lifecycle.deleteShift(institution, shift, null);
    lifecycle.restoreShift(institution, shift, null);
    lifecycle.restoreShift(institution, shift, null);
    assertActions(shift, "SHIFT");
    final UUID instrument =
        createInstrument
            .execute(
                institution,
                new CreateInstrumentRequest("Instrumento auditado", privateDescription))
            .id();
    final var instrumentChange =
        new UpdateInstrumentRequest("Instrumento actualizado", privateDescription);
    updateInstrument.execute(institution, instrument, instrumentChange);
    updateInstrument.execute(institution, instrument, instrumentChange);
    updateInstrumentStatus.execute(institution, instrument, new ActiveStatusRequest(false));
    updateInstrumentStatus.execute(institution, instrument, new ActiveStatusRequest(false));
    lifecycle.deleteInstrument(institution, instrument, null);
    lifecycle.deleteInstrument(institution, instrument, null);
    lifecycle.restoreInstrument(institution, instrument, null);
    lifecycle.restoreInstrument(institution, instrument, null);
    assertActions(instrument, "INSTRUMENT");
    final UUID trainingPath =
        createTrainingPath
            .execute(
                institution, new CreateTrainingPathRequest("Trayecto auditado", privateDescription))
            .id();
    final var trainingPathChange =
        new UpdateTrainingPathRequest("Trayecto actualizado", privateDescription);
    updateTrainingPath.execute(institution, trainingPath, trainingPathChange);
    updateTrainingPath.execute(institution, trainingPath, trainingPathChange);
    updateTrainingPathStatus.execute(institution, trainingPath, new ActiveStatusRequest(false));
    updateTrainingPathStatus.execute(institution, trainingPath, new ActiveStatusRequest(false));
    lifecycle.deleteTrainingPath(institution, trainingPath, null);
    lifecycle.deleteTrainingPath(institution, trainingPath, null);
    lifecycle.restoreTrainingPath(institution, trainingPath, null);
    lifecycle.restoreTrainingPath(institution, trainingPath, null);
    assertActions(trainingPath, "TRAINING_PATH");
    final UUID academicSpace =
        createAcademicSpace
            .execute(
                institution,
                new CreateAcademicSpaceRequest(
                    "Espacio auditado",
                    privateDescription,
                    AcademicSpaceType.SUBJECT,
                    AcademicSpaceFormat.GRUPAL))
            .id();
    final var academicSpaceChange =
        new UpdateAcademicSpaceRequest(
            "Espacio actualizado",
            privateDescription,
            AcademicSpaceType.SUBJECT,
            AcademicSpaceFormat.GRUPAL);
    updateAcademicSpace.execute(institution, academicSpace, academicSpaceChange);
    updateAcademicSpace.execute(institution, academicSpace, academicSpaceChange);
    updateAcademicSpaceStatus.execute(institution, academicSpace, new ActiveStatusRequest(false));
    updateAcademicSpaceStatus.execute(institution, academicSpace, new ActiveStatusRequest(false));
    lifecycle.deleteAcademicSpace(institution, academicSpace, null);
    lifecycle.deleteAcademicSpace(institution, academicSpace, null);
    lifecycle.restoreAcademicSpace(institution, academicSpace, null);
    lifecycle.restoreAcademicSpace(institution, academicSpace, null);
    assertActions(academicSpace, "ACADEMIC_SPACE");
    final int currentYear = LocalDate.now().getYear();
    final UUID year =
        createAcademicYear
            .execute(
                institution,
                new CreateAcademicYearRequest(
                    currentYear,
                    LocalDate.of(currentYear, 1, 1),
                    LocalDate.of(currentYear, 12, 30)))
            .id();
    final var yearChange =
        new UpdateAcademicYearRequest(
            currentYear,
            LocalDate.of(currentYear, 1, 1),
            LocalDate.of(currentYear, 12, 31),
            AcademicYearStatus.PLANNED);
    updateAcademicYear.execute(institution, year, yearChange);
    updateAcademicYear.execute(institution, year, yearChange);
    lifecycle.deleteAcademicYear(institution, year, null);
    lifecycle.restoreAcademicYear(institution, year, null);
    updateAcademicYearStatus.execute(
        institution, year, new AcademicYearStatusRequest(AcademicYearStatus.ACTIVE));
    updateAcademicYearStatus.execute(
        institution, year, new AcademicYearStatusRequest(AcademicYearStatus.ACTIVE));
    assertActions(year, "ACADEMIC_YEAR");
    assertThatThrownBy(
            () ->
                updateShift.execute(
                    otherInstitution, shift, new UpdateShiftRequest("Inválido", null)))
        .isInstanceOf(ShiftNotFoundException.class);
    assertThat(count(shift)).isEqualTo(5);

    final UUID actor = UUID.randomUUID();
    fixtures.insertPerson(actor, institution, fixtures.randomDocumentNumber(), false);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new TestingAuthenticationToken(
                new JwtAuthenticatedUser(
                    UUID.randomUUID(), actor, "12345678", institution, UUID.randomUUID(), "test"),
                "",
                "ROLE_INSTITUTION_AUTHORITY"));
    updateShift.execute(
        institution,
        shift,
        new UpdateShiftRequest("Turno actualizado", "Sólo cambia el texto privado"));
    final var change =
        jdbcTemplate.queryForMap(
            "SELECT * FROM audit_events WHERE entity_id = ? AND actor_person_id = ?", shift, actor);
    assertThat(change.get("actor_kind")).isEqualTo("INSTITUTION");
    assertThat(change.get("origin")).isEqualTo("INSTITUTIONAL_ADMINISTRATION");
    assertThat(requireNonNull(change.get("details")).toString())
        .contains("description")
        .doesNotContain("Sólo cambia");

    final UUID rolledBack =
        requireNonNull(
            transaction.execute(
                state -> {
                  final UUID id =
                      createShift
                          .execute(institution, new CreateShiftRequest("Rollback", null))
                          .id();
                  state.setRollbackOnly();
                  return id;
                }));
    assertThat(count(rolledBack)).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM shifts WHERE shift_id = ?", Long.class, rolledBack))
        .isZero();

    final var response =
        json.readTree(
            mvc.perform(
                    get("/api/v1/admin/audit-events")
                        .with(authentication(admin))
                        .param("institutionId", institution.toString())
                        .param("module", "SHIFTS")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(response.path("totalItems").asLong()).isEqualTo(6);
    assertThat(response.path("items").size()).isEqualTo(2);
    for (final var entry : response.path("items")) {
      assertThat(entry.path("institutionId").asString()).isEqualTo(institution.toString());
      assertThat(entry.path("action").asString()).startsWith("SHIFT_");
    }
    final String snapshots =
        jdbcTemplate.queryForObject(
            "SELECT string_agg(details::text, '') FROM audit_events WHERE institution_id = ?",
            String.class,
            institution);
    assertThat(snapshots).doesNotContain(privateDescription);
  }

  private long count(final UUID id) {
    return requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_events WHERE entity_id = ?", Long.class, id));
  }

  private void assertActions(final UUID id, final String prefix) {
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT action FROM audit_events WHERE entity_id = ?", String.class, id))
        .containsExactlyInAnyOrderElementsOf(
            List.of(
                prefix + "_CREATED",
                prefix + "_UPDATED",
                prefix + "_STATUS_CHANGED",
                prefix + "_DELETED",
                prefix + "_RESTORED"));
  }
}
