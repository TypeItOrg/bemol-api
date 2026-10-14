package ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentAttachment;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentDocumentRequirement;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.DocumentRequirementLevel;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.DocumentRequirementOrigin;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.DocumentReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(
    requiredProperties = {
      "sourceRequirementId",
      "appliedDefinitionRevision",
      "appliedAssignmentRevision",
      "origin",
      "requestId",
      "documentId",
      "active",
      "specificInstructions",
      "needsReplacement",
      "changes",
      "id",
      "name",
      "instructions",
      "level",
      "allowedFormats",
      "displayOrder",
      "status",
      "currentAttachment",
      "canUpload",
      "canReplace",
      "canWithdraw",
      "canReview"
    })
public record EnrollmentDocumentRequirementResponse(
    UUID id,
    String name,
    String instructions,
    DocumentRequirementLevel level,
    List<String> allowedFormats,
    int displayOrder,
    String status,
    @Schema(nullable = true) @Nullable EnrollmentAttachmentResponse currentAttachment,
    boolean canUpload,
    boolean canReplace,
    boolean canWithdraw,
    boolean canReview,
    UUID documentId,
    boolean active,
    @Schema(nullable = true) @Nullable String specificInstructions,
    boolean needsReplacement,
    List<EnrollmentRequirementChangeResponse> changes,
    DocumentRequirementOrigin origin,
    @Schema(nullable = true) @Nullable UUID requestId,
    @Schema(nullable = true) @Nullable UUID sourceRequirementId,
    @Schema(nullable = true) @Nullable Long appliedDefinitionRevision,
    @Schema(nullable = true) @Nullable Long appliedAssignmentRevision) {

  public static EnrollmentDocumentRequirementResponse from(
      final EnrollmentDocumentRequirement requirement,
      final @Nullable EnrollmentAttachment file,
      final boolean draftEditable,
      final boolean upload,
      final boolean delete,
      final boolean review) {
    final boolean active = requirement.isActive();
    final boolean mutable = file == null || file.getReviewStatus() != DocumentReviewStatus.ACCEPTED;
    final boolean withdrawable =
        draftEditable
            || requirement.getOrigin() == DocumentRequirementOrigin.ADDITIONAL
            || requirement.getLevel() != DocumentRequirementLevel.AT_SUBMISSION;
    final var request = requirement.getRequest();

    return new EnrollmentDocumentRequirementResponse(
        requirement.getId(),
        requirement.getName(),
        requirement.getInstructions(),
        requirement.getLevel(),
        requirement.getAllowedFormats(),
        requirement.getDisplayOrder(),
        file == null ? "MISSING" : file.getReviewStatus().name(),
        file == null ? null : EnrollmentAttachmentResponse.from(file),
        active && upload && file == null,
        active && upload && delete && file != null && mutable,
        active && delete && file != null && mutable && withdrawable,
        active
            && review
            && file != null
            && file.getReviewStatus() == DocumentReviewStatus.PENDING_REVIEW,
        requirement.getDocument().getId(),
        active,
        requirement.getSpecificInstructions(),
        active && file != null && !requirement.getAllowedFormats().contains(file.getContentType()),
        requirement.getChanges().stream()
            .sorted(Comparator.comparing(change -> change.getOccurredAt()))
            .map(EnrollmentRequirementChangeResponse::from)
            .toList(),
        requirement.getOrigin(),
        request == null ? null : request.getId(),
        requirement.getSourceRequirementId(),
        requirement.getDefinitionRevision(),
        requirement.getAssignmentRevision());
  }
}
