package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.CurrentPrincipal;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtPrincipal;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.audit.EnrollmentAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentAttachment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.DocumentVersionStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentDocumentAction;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentApplicationRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentAttachmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.OperatingContextResolver;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentDocumentAudit {
  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final AuditEventRecorder auditRecorder;
  private final EnrollmentApplicationRepository applications;
  private final EnrollmentAttachmentRepository attachments;
  private final OperatingContextResolver operatingContext;

  public record Actor(@Nullable UUID id, String accountType, @Nullable String requestId) {
    public static Actor from(final @Nullable Authentication authentication) {
      final String requestId = MDC.get("requestId");
      return CurrentPrincipal.jwt(authentication)
          .map(principal -> of(principal, requestId))
          .orElseGet(() -> new Actor(null, "ANONYMOUS", requestId));
    }

    private static Actor of(final JwtPrincipal principal, final @Nullable String requestId) {
      return switch (principal) {
        case JwtAuthenticatedUser user -> new Actor(user.actorPersonId(), "INSTITUTION", requestId);
        case JwtAuthenticatedPlatformAccount account ->
            new Actor(account.platformAccountId(), "PLATFORM", requestId);
      };
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      final Actor actor,
      final @Nullable UUID institutionId,
      final @Nullable UUID applicationId,
      final @Nullable UUID attachmentId,
      final EnrollmentDocumentAction action,
      final String result) {
    jdbc.update(
        """
        INSERT INTO enrollment_document_audit
          (id, occurred_at, actor_id, account_type, institution_id, application_id, attachment_id, action, result, request_id)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        UUID.randomUUID(),
        Timestamp.from(clock.instant()),
        actor.id(),
        actor.accountType(),
        institutionId,
        applicationId,
        attachmentId,
        action.name(),
        result,
        actor.requestId());
    recordGlobal(actor, applicationId, attachmentId, action, result);
  }

  private void recordGlobal(
      final Actor actor,
      final @Nullable UUID applicationId,
      final @Nullable UUID attachmentId,
      final EnrollmentDocumentAction action,
      final String result) {
    if (!"SUCCESS".equals(result) || applicationId == null || attachmentId == null) {
      return;
    }
    final var globalAction =
        switch (action) {
          case UPLOAD -> EnrollmentAuditAction.ENROLLMENT_ATTACHMENT_UPLOADED;
          case REPLACE -> EnrollmentAuditAction.ENROLLMENT_ATTACHMENT_REPLACED;
          case DELETE -> EnrollmentAuditAction.ENROLLMENT_ATTACHMENT_DELETED;
          case REVIEW -> EnrollmentAuditAction.ENROLLMENT_ATTACHMENT_REVIEWED;
          default -> null;
        };
    if (globalAction == null) {
      return;
    }
    final var application = applications.findById(applicationId).orElseThrow();
    final var attachment = attachments.findById(attachmentId).orElseThrow();
    // Replacement is one business operation; the old version keeps its private document history.
    if (action == EnrollmentDocumentAction.DELETE
        && attachment.getVersionStatus() == DocumentVersionStatus.SUPERSEDED) {
      return;
    }
    final boolean selfService =
        application.getApplicantPerson().getId().equals(actor.id())
            || operatingContext.currentPersonId().isPresent();
    auditRecorder.recordCurrentActor(
        application.getInstitution(),
        application.getApplicantPerson().getId(),
        globalAction,
        EnrollmentAttachment.AUDIT_TYPE,
        attachmentId,
        selfService ? AuditOrigin.SELF_SERVICE : AuditOrigin.administration(),
        AuditChangeDetails.change(
            "Adjunto de inscripción", AuditSnapshot.empty(), attachment.auditSnapshot()));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordIndependent(
      final @Nullable Authentication authentication,
      final @Nullable UUID institutionId,
      final @Nullable UUID applicationId,
      final @Nullable UUID attachmentId,
      final EnrollmentDocumentAction action,
      final String result) {
    record(Actor.from(authentication), institutionId, applicationId, attachmentId, action, result);
  }
}
