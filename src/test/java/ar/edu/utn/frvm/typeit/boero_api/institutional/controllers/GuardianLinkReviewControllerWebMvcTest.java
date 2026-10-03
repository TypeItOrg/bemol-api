package ar.edu.utn.frvm.typeit.boero_api.institutional.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsPlatformSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.IsSessionActiveUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.JwtService;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.TokenBlacklistService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.security.InstitutionAccessAspect;
import ar.edu.utn.frvm.typeit.boero_api.authorization.security.PermissionAuthorizationAspect;
import ar.edu.utn.frvm.typeit.boero_api.authorization.security.RoleAuthorizationAspect;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InitialRoleAssignmentGuard;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frvm.typeit.boero_api.config.WebConfig;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardianAttachment;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.GuardianLinkAlreadyResolvedException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianLinkAttachmentResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianLinkReviewResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianLinkReviewResponse.PersonSummary;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.GuardianLinkAttachmentService;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.ListGuardianLinksUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.ResolveGuardianLinkUseCase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.PathMatcher;

@WebMvcTest(GuardianLinkReviewController.class)
@Import({
  RoleAuthorizationAspect.class,
  InstitutionAccessAspect.class,
  PermissionAuthorizationAspect.class,
  GlobalExceptionHandler.class,
  WebConfig.class
})
@AutoConfigureMockMvc(addFilters = false)
@EnableAspectJAutoProxy
class GuardianLinkReviewControllerWebMvcTest {

  private static final UUID INSTITUTION_ID = UUID.randomUUID();
  private static final UUID REVIEWER_ID = UUID.randomUUID();
  private static final UUID LINK_ID = UUID.randomUUID();
  private static final UUID ATTACHMENT_ID = UUID.randomUUID();
  private static final String BASE_PATH = "/api/v1/institutions/{institutionId}/guardian-links";

  @MockitoBean private InstitutionalCallerGuard institutionalCallerGuard;
  @MockitoBean private InitialRoleAssignmentGuard initialRoleAssignmentGuard;
  @MockitoBean private AuthorizationService authorizationService;
  @MockitoBean private ListGuardianLinksUseCase listGuardianLinksUseCase;
  @MockitoBean private ResolveGuardianLinkUseCase resolveGuardianLinkUseCase;
  @MockitoBean private GuardianLinkAttachmentService guardianLinkAttachmentService;

  @MockitoBean private PathMatcher pathMatcher;
  @MockitoBean private AuthenticationEntryPoint authenticationEntryPoint;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TokenBlacklistService tokenBlacklistService;
  @MockitoBean private IsSessionActiveUseCase isSessionActiveUseCase;
  @MockitoBean private IsPlatformSessionActiveUseCase isPlatformSessionActiveUseCase;

  @Autowired private MockMvc mockMvc;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Should list the pending requests with tutor, person and declared relationship")
  void list_defaultsToPending() throws Exception {
    allowReviewing();
    when(listGuardianLinksUseCase.execute(INSTITUTION_ID, GuardianLinkStatus.PENDING))
        .thenReturn(List.of(response(GuardianLinkStatus.PENDING)));

    mockMvc
        .perform(get(BASE_PATH, INSTITUTION_ID).principal(authentication()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].personGuardianId").value(LINK_ID.toString()))
        .andExpect(jsonPath("$[0].relationship").value("MOTHER"))
        .andExpect(jsonPath("$[0].tutor.documentNumber").value("35123456"))
        .andExpect(jsonPath("$[0].dependent.documentNumber").value("54123456"));
  }

  @Test
  @DisplayName("Should approve a request as the authenticated reviewer")
  void approve_resolvesAsReviewer() throws Exception {
    allowReviewing();
    when(resolveGuardianLinkUseCase.approve(INSTITUTION_ID, REVIEWER_ID, LINK_ID))
        .thenReturn(response(GuardianLinkStatus.ACTIVE));

    mockMvc
        .perform(
            post(BASE_PATH + "/{linkId}/approve", INSTITUTION_ID, LINK_ID)
                .principal(authentication()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  @DisplayName("Should reject a request as the authenticated reviewer")
  void reject_resolvesAsReviewer() throws Exception {
    allowReviewing();
    when(resolveGuardianLinkUseCase.reject(INSTITUTION_ID, REVIEWER_ID, LINK_ID))
        .thenReturn(response(GuardianLinkStatus.REJECTED));

    mockMvc
        .perform(
            post(BASE_PATH + "/{linkId}/reject", INSTITUTION_ID, LINK_ID)
                .principal(authentication()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"));
  }

  @Test
  @DisplayName("Should answer with a conflict when the request was already resolved")
  void approve_conflictWhenAlreadyResolved() throws Exception {
    allowReviewing();
    when(resolveGuardianLinkUseCase.approve(INSTITUTION_ID, REVIEWER_ID, LINK_ID))
        .thenThrow(new GuardianLinkAlreadyResolvedException());

    mockMvc
        .perform(
            post(BASE_PATH + "/{linkId}/approve", INSTITUTION_ID, LINK_ID)
                .principal(authentication()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("GUARDIAN_LINK_ALREADY_RESOLVED"));
  }

  @Test
  @DisplayName("Should let the reviewer consult the supporting documents of a request")
  void attachments_listAndDownload() throws Exception {
    allowReviewing();
    final var auth = authentication();
    when(guardianLinkAttachmentService.list(INSTITUTION_ID, null, LINK_ID))
        .thenReturn(
            List.of(
                new GuardianLinkAttachmentResponse(
                    ATTACHMENT_ID,
                    "partida.pdf",
                    "application/pdf",
                    8,
                    Instant.parse("2026-09-24T12:00:00Z"))));
    when(guardianLinkAttachmentService.content(INSTITUTION_ID, null, LINK_ID, ATTACHMENT_ID))
        .thenReturn(
            new GuardianLinkAttachmentService.Content(
                new ByteArrayResource("%PDF-1.4".getBytes()),
                PersonGuardianAttachment.builder()
                    .originalFileName("partida.pdf")
                    .contentType("application/pdf")
                    .fileSize(8)
                    .build()));

    mockMvc
        .perform(get(BASE_PATH + "/{linkId}/attachments", INSTITUTION_ID, LINK_ID).principal(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].originalFileName").value("partida.pdf"));
    mockMvc
        .perform(
            get(
                    BASE_PATH + "/{linkId}/attachments/{attachmentId}/content",
                    INSTITUTION_ID,
                    LINK_ID,
                    ATTACHMENT_ID)
                .principal(auth))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF));
  }

  @Test
  @DisplayName("Should forbid callers without the review permission")
  void resolve_forbiddenWithoutPermission() throws Exception {
    when(authorizationService.hasPermission(any(), eq(PermissionCode.GUARDIAN_LINK_REVIEW)))
        .thenReturn(false);
    final var auth = authentication();

    mockMvc
        .perform(get(BASE_PATH, INSTITUTION_ID).principal(auth))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(post(BASE_PATH + "/{linkId}/approve", INSTITUTION_ID, LINK_ID).principal(auth))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(post(BASE_PATH + "/{linkId}/reject", INSTITUTION_ID, LINK_ID).principal(auth))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get(BASE_PATH + "/{linkId}/attachments", INSTITUTION_ID, LINK_ID).principal(auth))
        .andExpect(status().isForbidden());
    verifyNoInteractions(
        resolveGuardianLinkUseCase, listGuardianLinksUseCase, guardianLinkAttachmentService);
  }

  private void allowReviewing() {
    when(authorizationService.hasPermission(any(), eq(PermissionCode.GUARDIAN_LINK_REVIEW)))
        .thenReturn(true);
  }

  private GuardianLinkReviewResponse response(final GuardianLinkStatus status) {
    return new GuardianLinkReviewResponse(
        LINK_ID,
        status,
        GuardianRelationship.MOTHER,
        new PersonSummary(UUID.randomUUID(), "35123456", "Ana", "Garcia", null),
        new PersonSummary(
            UUID.randomUUID(), "54123456", "Mateo", "Gonzalez", LocalDate.of(2018, 9, 10)),
        Instant.parse("2026-09-24T12:00:00Z"),
        null);
  }

  private TestingAuthenticationToken authentication() {
    final var user =
        JwtAuthenticatedUser.builder()
            .userId(UUID.randomUUID())
            .personId(REVIEWER_ID)
            .documentNumber("20111222")
            .institutionId(INSTITUTION_ID)
            .sessionId(UUID.randomUUID())
            .tokenId("token-id")
            .build();
    final var auth = new TestingAuthenticationToken(user, null);
    auth.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(auth);

    return auth;
  }
}
