package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormNativeBlock;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.util.Collections;
import java.util.Map;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Embeddable
@Getter
public class EnrollmentApplicationFormState {
  // Null identifies BASE_V1, never the current institutional assignment.
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "form_version_id", updatable = false)
  private @Nullable EnrollmentFormVersion formVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "custom_answers", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> customAnswers = Map.of();

  @Column(name = "draft_revision", nullable = false)
  private long draftRevision;

  void bindFormVersion(final EnrollmentFormVersion version) {
    formVersion = version;
  }

  public boolean hasBlock(final EnrollmentFormNativeBlock block) {
    return formVersion == null
        ? block != EnrollmentFormNativeBlock.PREFERRED_SHIFT
        : formVersion.getDefinition().sections().stream()
            .anyMatch(section -> section.nativeBlock() == block);
  }

  public EnrollmentFormDefinition definition() {
    return formVersion == null
        ? EnrollmentFormBaseDefinition.create()
        : formVersion.getDefinition();
  }

  public void requireRevision(final @Nullable Long expectedRevision) {
    if (expectedRevision == null) {
      if (formVersion != null) {
        throw new EnrollmentValidationException(
            EnrollmentFormMessages.DRAFT_REVISION_REQUIRED,
            Map.of("expectedRevision", EnrollmentFormMessages.DRAFT_REVISION_REQUIRED));
      }
      return;
    }
    if (expectedRevision != draftRevision) {
      throw new EnrollmentFormException(
          ErrorCategory.CONFLICT, EnrollmentFormMessages.DRAFT_REVISION_CONFLICT);
    }
  }

  public Map<String, Object> getCustomAnswers() {
    return Collections.unmodifiableMap(customAnswers);
  }

  void replaceAnswers(final Map<String, Object> answers) {
    if (formVersion == null && !answers.isEmpty()) {
      throw new EnrollmentValidationException(EnrollmentFormMessages.ANSWERS_INVALID);
    }
    customAnswers = Map.copyOf(answers);
  }

  public void advanceRevision() {
    draftRevision++;
  }
}
