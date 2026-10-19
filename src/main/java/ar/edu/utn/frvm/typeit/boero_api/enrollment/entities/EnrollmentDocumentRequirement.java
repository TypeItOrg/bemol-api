package ar.edu.utn.frvm.typeit.boero_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.DocumentDefinition;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.TrainingPathDocumentRequirement;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.DocumentRequirementLevel;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.DocumentRequirementOrigin;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "enrollment_document_requirements")
@Getter
@NoArgsConstructor
public class EnrollmentDocumentRequirement {
  @Id @GeneratedUUIDv7 private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "application_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private EnrollmentApplication application;

  @Column(name = "source_requirement_id")
  @Audited
  private @Nullable UUID sourceRequirementId;

  @Column(nullable = false, length = 150)
  @Audited
  private String name;

  @Column(nullable = false, length = 1000)
  private String instructions;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Audited
  private DocumentRequirementLevel level;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "allowed_formats", nullable = false, columnDefinition = "jsonb")
  @Audited
  private List<String> allowedFormats;

  @Column(name = "display_order", nullable = false)
  @Audited
  private int displayOrder;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "document_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private DocumentDefinition document;

  @Column(name = "institution_id", nullable = false)
  @Audited
  private UUID institutionId;

  @Column(name = "definition_revision")
  @Audited
  private @Nullable Long definitionRevision;

  @Column(name = "assignment_revision")
  @Audited
  private @Nullable Long assignmentRevision;

  @Column(name = "specific_instructions", length = 1000)
  private @Nullable String specificInstructions;

  @Column(nullable = false)
  @Audited
  private boolean active = true;

  @OneToMany(mappedBy = "requirement", cascade = CascadeType.ALL)
  private List<EnrollmentRequirementChange> changes = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Audited
  private DocumentRequirementOrigin origin = DocumentRequirementOrigin.ORIGINAL;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "request_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private @Nullable EnrollmentDocumentRequest request;

  public static EnrollmentDocumentRequirement additional(
      final EnrollmentApplication application,
      final DocumentDefinition document,
      final EnrollmentDocumentRequest request,
      final DocumentRequirementLevel level,
      final int order) {
    var value = new EnrollmentDocumentRequirement();
    value.application = application;
    value.document = document;
    value.institutionId = application.getInstitution().getId();
    value.origin = DocumentRequirementOrigin.ADDITIONAL;
    value.request = request;
    value.name = document.getName();
    value.instructions = document.getInstructions();
    value.allowedFormats = List.copyOf(document.getAllowedFormats());
    value.definitionRevision = document.getRevision();
    value.level = level;
    value.displayOrder = order;
    return value;
  }

  public void recordChange(
      final String action,
      final Instant at,
      final @Nullable UUID actorId,
      final String accountType) {
    changes.add(EnrollmentRequirementChange.create(this, action, at, actorId, accountType));
  }

  public boolean synchronize(final TrainingPathDocumentRequirement source) {
    boolean changed =
        !active
            || !name.equals(source.getName())
            || !instructions.equals(source.getInstructions())
            || !Objects.equals(specificInstructions, source.getSpecificInstructions())
            || level != source.getLevel()
            || !allowedFormats.equals(source.getAllowedFormats())
            || displayOrder != source.getDisplayOrder();

    copySource(source);
    active = true;
    return changed;
  }

  public boolean retire() {
    if (!active) {
      return false;
    }
    active = false;
    return true;
  }

  public static EnrollmentDocumentRequirement snapshot(
      EnrollmentApplication application, TrainingPathDocumentRequirement source) {
    var result = new EnrollmentDocumentRequirement();
    result.application = application;
    result.sourceRequirementId = source.getId();
    result.document = source.getDocument();
    result.institutionId = application.getInstitution().getId();
    result.copySource(source);
    return result;
  }

  private void copySource(final TrainingPathDocumentRequirement source) {
    name = source.getName();
    instructions = source.getInstructions();
    specificInstructions = source.getSpecificInstructions();
    level = source.getLevel();
    allowedFormats = List.copyOf(source.getAllowedFormats());
    displayOrder = source.getDisplayOrder();
    definitionRevision = source.getDocument().getRevision();
    assignmentRevision = source.getRevision();
  }
}
