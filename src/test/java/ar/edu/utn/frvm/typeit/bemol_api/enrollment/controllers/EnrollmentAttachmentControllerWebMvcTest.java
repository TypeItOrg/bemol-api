package ar.edu.utn.frvm.typeit.bemol_api.enrollment.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frvm.typeit.bemol_api.config.WebConfig;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentAttachment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.DocumentReviewStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.DocumentVersionStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentAttachmentResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentAttachmentService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentAttachmentService.AttachmentContentResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;

@WebMvcTest(EnrollmentAttachmentController.class)
@Import({GlobalExceptionHandler.class, WebConfig.class})
@AutoConfigureMockMvc(addFilters = false)
class EnrollmentAttachmentControllerWebMvcTest {

  private static final UUID APPLICATION_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID ATTACHMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID PERSON_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID INSTITUTION_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");

  private static final UUID REQUIREMENT_ID =
      UUID.fromString("55555555-5555-5555-5555-555555555555");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;
  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;
  @MockitoBean private AuthorizationService authorizationService;

  @MockitoBean private EnrollmentAttachmentService attachmentService;

  @BeforeEach
  void allowStaff() {
    when(authorizationService.hasPermission(
            any(), any(ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode.class)))
        .thenReturn(true);
  }

  private static TestingAuthenticationToken staffAuthentication() {
    JwtAuthenticatedUser principal =
        JwtAuthenticatedUser.builder()
            .userId(UUID.randomUUID())
            .personId(PERSON_ID)
            .documentNumber("12345678")
            .institutionId(INSTITUTION_ID)
            .sessionId(UUID.randomUUID())
            .tokenId("jti")
            .build();
    return new TestingAuthenticationToken(principal, "");
  }

  @Test
  @DisplayName("POST /attachments should upload file and return CREATED response")
  void uploadAttachment_success() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "dni.pdf", "application/pdf", "dummy-content".getBytes());

    EnrollmentAttachmentResponse response =
        new EnrollmentAttachmentResponse(
            ATTACHMENT_ID,
            REQUIREMENT_ID,
            "dni.pdf",
            13L,
            "application/pdf",
            Instant.parse("2026-09-08T12:00:00Z"),
            DocumentVersionStatus.CURRENT,
            DocumentReviewStatus.PENDING_REVIEW,
            PERSON_ID,
            "INSTITUTION",
            null,
            null,
            null,
            null,
            null);

    when(attachmentService.uploadAttachment(eq(APPLICATION_ID), any(), eq(REQUIREMENT_ID), any()))
        .thenReturn(response);

    mockMvc
        .perform(
            multipart("/api/v1/enrollment-applications/{applicationId}/attachments", APPLICATION_ID)
                .file(file)
                .param("requirementId", REQUIREMENT_ID.toString())
                .principal(staffAuthentication()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(ATTACHMENT_ID.toString()))
        .andExpect(jsonPath("$.requirementId").value(REQUIREMENT_ID.toString()))
        .andExpect(jsonPath("$.originalFileName").value("dni.pdf"))
        .andExpect(jsonPath("$.size").value(13));
  }

  @Test
  @DisplayName("GET /attachments/{id}/content should stream resource with content headers")
  void getAttachmentContent_success() throws Exception {
    byte[] fileBytes = "test-pdf-bytes".getBytes();
    ByteArrayResource resource = new ByteArrayResource(fileBytes);

    EnrollmentAttachment attachment =
        EnrollmentAttachment.builder()
            .id(ATTACHMENT_ID)
            .originalFileName("dni.pdf")
            .storagePath(APPLICATION_ID + "/safe-dni.pdf")
            .contentType("application/pdf")
            .fileSize((long) fileBytes.length)
            .build();

    when(attachmentService.getAttachmentContent(eq(APPLICATION_ID), eq(ATTACHMENT_ID), any()))
        .thenReturn(new AttachmentContentResult(resource, attachment));

    mockMvc
        .perform(
            get(
                    "/api/v1/enrollment-applications/{applicationId}/attachments/{attachmentId}/content",
                    APPLICATION_ID,
                    ATTACHMENT_ID)
                .principal(staffAuthentication()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileBytes.length)))
        .andExpect(header().exists(HttpHeaders.CONTENT_DISPOSITION))
        .andExpect(content().bytes(fileBytes));
  }

  @Test
  @DisplayName("DELETE /attachments/{id} should return NO_CONTENT")
  void deleteAttachment_success() throws Exception {
    mockMvc
        .perform(
            delete(
                    "/api/v1/enrollment-applications/{applicationId}/attachments/{attachmentId}",
                    APPLICATION_ID,
                    ATTACHMENT_ID)
                .principal(staffAuthentication()))
        .andExpect(status().isNoContent());

    verify(attachmentService).deleteAttachment(eq(APPLICATION_ID), eq(ATTACHMENT_ID), any());
  }

  @Test
  @DisplayName("GET /attachments should return list of active attachments")
  void listAttachments_success() throws Exception {
    EnrollmentAttachmentResponse response =
        new EnrollmentAttachmentResponse(
            ATTACHMENT_ID,
            REQUIREMENT_ID,
            "dni.pdf",
            13L,
            "application/pdf",
            Instant.parse("2026-09-08T12:00:00Z"),
            DocumentVersionStatus.CURRENT,
            DocumentReviewStatus.PENDING_REVIEW,
            PERSON_ID,
            "INSTITUTION",
            null,
            null,
            null,
            null,
            null);

    when(attachmentService.listAttachments(eq(APPLICATION_ID), any()))
        .thenReturn(List.of(response));

    mockMvc
        .perform(
            get("/api/v1/enrollment-applications/{applicationId}/attachments", APPLICATION_ID)
                .principal(staffAuthentication()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(ATTACHMENT_ID.toString()))
        .andExpect(jsonPath("$[0].requirementId").value(REQUIREMENT_ID.toString()));
  }
}
