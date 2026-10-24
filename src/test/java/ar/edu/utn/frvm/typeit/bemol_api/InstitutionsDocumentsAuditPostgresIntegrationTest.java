package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.DocumentCatalogException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.DocumentAssignmentRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.DocumentDefinitionRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.DocumentRequirementRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.DocumentCatalogUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.TrainingPathDocumentRequirementService;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.common.logging.RequestLoggingFilter;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailSender;
import ar.edu.utn.frvm.typeit.bemol_api.common.storage.StorageService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.DocumentRequirementLevel;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.requests.CreateInstitutionRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.requests.UpdateInstitutionRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.requests.UpdateInstitutionalInstitutionRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.CreateInstitutionUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.InstitutionLogoUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.UpdateInstitutionPublicAccessUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.UpdateInstitutionStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.UpdateInstitutionUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.UpdateInstitutionalInstitutionUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.InputStreamSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@IntegrationTest
@AutoConfigureMockMvc
class InstitutionsDocumentsAuditPostgresIntegrationTest extends DatabaseMigrationTestSupport {
  private static final String BASE = "/api/v1/admin/audit-events";
  private static final String PRIVATE_EMAIL = "private-audit-contact@example.test";
  private static final String PRIVATE_PHONE = "+5493531234567";
  private static final String PRIVATE_INSTRUCTIONS = "private-audit-instructions";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired CreateInstitutionUseCase createInstitution;
  @Autowired UpdateInstitutionUseCase updateInstitution;
  @Autowired UpdateInstitutionalInstitutionUseCase updateInstitutionalInstitution;
  @Autowired UpdateInstitutionStatusUseCase institutionStatus;
  @Autowired UpdateInstitutionPublicAccessUseCase publicAccess;
  @Autowired InstitutionLogoUseCase logos;
  @Autowired DocumentCatalogUseCase catalog;
  @Autowired TrainingPathDocumentRequirementService pathRequirements;
  @Autowired TransactionTemplate transaction;
  @MockitoBean MailSender mailSender;
  @MockitoSpyBean StorageService storageService;

  @BeforeEach
  void isolateLogoStorageWrites() {
    doNothing()
        .when(storageService)
        .write(anyString(), anyString(), anyLong(), any(InputStreamSource.class));
    doReturn(true).when(storageService).deletePhysicalFile(anyString());
  }

  @AfterEach
  void clearRequestId() {
    MDC.remove(RequestLoggingFilter.MDC_REQUEST_ID_KEY);
  }

  @Test
  void recordsInstitutionsAndDocumentationWithAtomicPrivateSnapshotsAndModuleFiltering()
      throws Exception {
    authenticatePlatformAdministrator();
    final Authentication admin =
        requireNonNull(SecurityContextHolder.getContext().getAuthentication());
    final UUID adminId =
        requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT platform_account_id FROM platform_accounts WHERE email = 'admin@plataforma.com'",
                UUID.class));
    final String requestId = UUID.randomUUID().toString();
    MDC.put(RequestLoggingFilter.MDC_REQUEST_ID_KEY, requestId);
    final String suffix = UUID.randomUUID().toString().substring(0, 8);
    final UUID cityId =
        requireNonNull(
            jdbcTemplate.queryForObject("SELECT city_id FROM cities LIMIT 1", UUID.class));
    final UUID institutionId =
        createInstitution.execute(institutionRequest("Instituto Boreal", cityId)).id();
    final UUID destinationId =
        createInstitution.execute(institutionRequest("Instituto Destino", cityId)).id();
    final var updatedInstitution =
        new UpdateInstitutionRequest(
            "Instituto Boreal Renovado",
            cityId,
            null,
            null,
            null,
            null,
            PRIVATE_PHONE,
            PRIVATE_EMAIL,
            true);
    updateInstitution.execute(institutionId, updatedInstitution);
    updateInstitution.execute(institutionId, updatedInstitution);
    institutionStatus.execute(institutionId, false);
    institutionStatus.execute(institutionId, false);
    institutionStatus.execute(institutionId, true);
    publicAccess.execute(institutionId, "aurora-" + suffix);
    publicAccess.execute(institutionId, "aurora-" + suffix);
    final var image = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", image);
    logos.replace(
        institutionId,
        new MockMultipartFile("file", "private-logo-name.png", "image/png", image.toByteArray()));
    logos.delete(institutionId);
    logos.delete(institutionId);
    assertThat(count(institutionId, "INSTITUTION_CREATED")).isEqualTo(1);
    assertThat(count(institutionId, "INSTITUTION_UPDATED")).isEqualTo(1);
    assertThat(count(institutionId, "INSTITUTION_STATUS_CHANGED")).isEqualTo(2);
    assertThat(count(institutionId, "INSTITUTION_PUBLIC_ACCESS_CHANGED")).isEqualTo(1);
    assertThat(count(institutionId, "INSTITUTION_LOGO_CHANGED")).isEqualTo(2);
    assertThat(event(institutionId, "INSTITUTION_CREATED").get("actor_platform_account_id"))
        .isEqualTo(adminId);
    assertThat(event(institutionId, "INSTITUTION_CREATED").get("origin"))
        .isEqualTo("PLATFORM_ADMINISTRATION");

    final UUID personId = UUID.randomUUID();
    fixtures.insertPerson(personId, institutionId, fixtures.randomDocumentNumber(), false);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new TestingAuthenticationToken(
                JwtAuthenticatedUser.builder()
                    .userId(UUID.randomUUID())
                    .personId(personId)
                    .institutionId(institutionId)
                    .build(),
                "",
                "ROLE_USER"));
    final var institutionalUpdate =
        new UpdateInstitutionalInstitutionRequest(
            "Instituto Boreal Comunitario",
            cityId,
            null,
            null,
            null,
            null,
            PRIVATE_PHONE,
            PRIVATE_EMAIL);
    updateInstitutionalInstitution.execute(institutionId, institutionalUpdate);
    updateInstitutionalInstitution.execute(institutionId, institutionalUpdate);
    assertThat(count(institutionId, "INSTITUTION_UPDATED")).isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT actor_person_id FROM audit_events WHERE entity_id = ? AND actor_kind = 'INSTITUTION'",
                UUID.class,
                institutionId))
        .isEqualTo(personId);
    SecurityContextHolder.getContext().setAuthentication(admin);

    final UUID firstPath = UUID.randomUUID(), secondPath = UUID.randomUUID();
    fixtures.insertTrainingPath(firstPath, institutionId, "Trayecto Aurora");
    fixtures.insertTrainingPath(secondPath, institutionId, "Trayecto Complementario");
    final var created =
        catalog.save(
            institutionId,
            null,
            documentRequest(
                "Documento Aurora",
                null,
                List.of(assignment(firstPath, null, DocumentRequirementLevel.AT_SUBMISSION, 0)),
                null));
    final UUID documentId = created.document().id();
    assertThat(count(documentId, "DOCUMENT_DEFINITION_CREATED")).isEqualTo(1);
    assertThat(count(documentId, "DOCUMENT_REQUIREMENTS_CHANGED")).isZero();
    final var updated =
        catalog.save(
            institutionId,
            documentId,
            documentRequest(
                "Documento Aurora Revisado", created.document().revision(), null, null));
    catalog.save(
        institutionId,
        documentId,
        documentRequest(" Documento Aurora Revisado ", updated.document().revision(), null, null));
    assertThat(count(documentId, "DOCUMENT_DEFINITION_UPDATED")).isEqualTo(1);
    final var assigned =
        catalog.saveAssignments(
            institutionId,
            documentId,
            List.of(
                assignment(
                    firstPath,
                    updated.assignments().getFirst().revision(),
                    DocumentRequirementLevel.BEFORE_CONFIRMATION,
                    2)));
    catalog.saveAssignments(
        institutionId,
        documentId,
        List.of(
            assignment(
                firstPath,
                assigned.assignments().getFirst().revision(),
                DocumentRequirementLevel.BEFORE_CONFIRMATION,
                2)));
    assertThat(count(documentId, "DOCUMENT_REQUIREMENTS_CHANGED")).isEqualTo(1);
    catalog.save(
        institutionId,
        documentId,
        documentRequest(
            "Documento Aurora Revisado",
            assigned.document().revision(),
            List.of(
                assignment(
                    firstPath,
                    assigned.assignments().getFirst().revision(),
                    DocumentRequirementLevel.OPTIONAL,
                    3)),
            null));
    assertThat(count(documentId, "DOCUMENT_REQUIREMENTS_CHANGED")).isEqualTo(2);
    assertThat(count(documentId, "DOCUMENT_DEFINITION_UPDATED")).isEqualTo(1);
    final var secondRequirement =
        pathRequirements.save(
            institutionId,
            secondPath,
            null,
            new DocumentRequirementRequest(
                documentId,
                null,
                DocumentRequirementLevel.OPTIONAL,
                4,
                true,
                PRIVATE_INSTRUCTIONS));
    pathRequirements.save(
        institutionId,
        secondPath,
        secondRequirement.id(),
        new DocumentRequirementRequest(
            documentId,
            secondRequirement.revision(),
            DocumentRequirementLevel.OPTIONAL,
            4,
            true,
            " " + PRIVATE_INSTRUCTIONS + " "));
    assertThat(count(documentId, "DOCUMENT_REQUIREMENTS_CHANGED")).isEqualTo(3);

    final var transferable =
        catalog.save(institutionId, null, documentRequest("Constancia Traslado", null, null, null));
    catalog.save(
        institutionId,
        transferable.document().id(),
        documentRequest(
            "Constancia Traslado", transferable.document().revision(), null, destinationId));
    final var transferred = event(transferable.document().id(), "DOCUMENT_DEFINITION_TRANSFERRED");
    assertThat(transferred.get("institution_id")).isEqualTo(destinationId);
    assertThat(requireNonNull(transferred.get("details")).toString())
        .contains("Instituto Boreal Comunitario", "Instituto Destino");

    final long committedEvents = totalForEntity(documentId);
    final long currentRevision = catalog.get(institutionId, documentId).revision();
    assertThatThrownBy(
            () ->
                catalog.save(
                    institutionId,
                    documentId,
                    documentRequest("Rejected stale update", currentRevision + 99, null, null)))
        .isInstanceOf(DocumentCatalogException.class);
    assertThatThrownBy(
            () ->
                catalog.save(
                    institutionId,
                    documentId,
                    documentRequest("Rejected transfer", currentRevision, null, destinationId)))
        .isInstanceOf(DocumentCatalogException.class);
    transaction.executeWithoutResult(
        status -> {
          catalog.save(
              institutionId,
              documentId,
              documentRequest("Rolled back document", currentRevision, null, null));
          status.setRollbackOnly();
        });
    assertThat(totalForEntity(documentId)).isEqualTo(committedEvents);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT name FROM document_definitions WHERE id = ?", String.class, documentId))
        .isEqualTo("Documento Aurora Revisado");
    assertThat(count(institutionId, "INSTITUTION_UPDATED")).isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT details::text FROM audit_events WHERE request_id = ?",
                String.class,
                requestId))
        .allSatisfy(
            details ->
                assertThat(details)
                    .doesNotContain(
                        PRIVATE_EMAIL,
                        PRIVATE_PHONE,
                        PRIVATE_INSTRUCTIONS,
                        "private-logo-name",
                        "logoKey"));

    final var first =
        asAdmin(
            get(BASE)
                .param("requestId", requestId)
                .param("module", "DOCUMENTATION")
                .param("search", "Documento Aurora")
                .param("size", "1"),
            admin);
    final var second =
        asAdmin(
            get(BASE)
                .param("requestId", requestId)
                .param("module", "DOCUMENTATION")
                .param("search", "Documento Aurora")
                .param("size", "1")
                .param("page", "1"),
            admin);
    assertThat(first.get("totalItems").asInt()).isEqualTo(5);
    assertThat(second.get("totalItems").asInt()).isEqualTo(5);
    assertThat(first.at("/items/0/id").asString())
        .isNotEqualTo(second.at("/items/0/id").asString());
    assertThat(first.at("/items/0/entityType").asString()).isEqualTo("DOCUMENT_DEFINITION");
    assertThat(first.at("/items/0/actorId").asString()).isEqualTo(adminId.toString());
    assertThat(
            asAdmin(
                    get(BASE)
                        .param("requestId", requestId)
                        .param("module", "INSTITUTIONS")
                        .param("institutionId", institutionId.toString())
                        .param("size", "1"),
                    admin)
                .get("totalItems")
                .asInt())
        .isEqualTo(8);
    assertThat(
            asAdmin(get(BASE).param("requestId", requestId).param("module", "COURSES"), admin)
                .get("totalItems")
                .asInt())
        .isZero();
    final UUID createdEventId =
        (UUID) event(documentId, "DOCUMENT_DEFINITION_CREATED").get("audit_event_id");
    final var detail = asAdmin(get(BASE + "/" + createdEventId), admin);
    assertThat(detail.at("/details/entityName").asString()).isEqualTo("Documento Aurora");
    assertThat(detail.at("/details/after/requirementsCount").asInt()).isEqualTo(1);
    assertThat(detail.at("/details/after/requirements/0/trainingPathName").asString())
        .isEqualTo("Trayecto Aurora");
  }

  private CreateInstitutionRequest institutionRequest(final String name, final UUID cityId) {
    return new CreateInstitutionRequest(
        name, cityId, null, null, null, null, PRIVATE_PHONE, PRIVATE_EMAIL, true);
  }

  private DocumentDefinitionRequest documentRequest(
      final String name,
      final @Nullable Long revision,
      final @Nullable List<DocumentAssignmentRequest> assignments,
      final @Nullable UUID targetInstitutionId) {
    return new DocumentDefinitionRequest(
        name,
        " " + PRIVATE_INSTRUCTIONS + " ",
        List.of("image/png", "application/pdf", "image/png"),
        true,
        revision,
        assignments,
        targetInstitutionId);
  }

  private DocumentAssignmentRequest assignment(
      final UUID path,
      final @Nullable Long revision,
      final DocumentRequirementLevel level,
      final int order) {
    return new DocumentAssignmentRequest(path, revision, level, order, true, PRIVATE_INSTRUCTIONS);
  }

  private Map<String, @Nullable Object> event(final UUID entityId, final String action) {
    return jdbcTemplate.queryForMap(
        "SELECT * FROM audit_events WHERE entity_id = ? AND action = ?", entityId, action);
  }

  private long count(final UUID entityId, final String action) {
    return requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_events WHERE entity_id = ? AND action = ?",
            Long.class,
            entityId,
            action));
  }

  private long totalForEntity(final UUID entityId) {
    return requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_events WHERE entity_id = ?", Long.class, entityId));
  }

  private JsonNode asAdmin(final MockHttpServletRequestBuilder request, final Authentication admin)
      throws Exception {
    return json.readTree(
        mvc.perform(request.with(authentication(admin)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }
}
