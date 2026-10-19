package ar.edu.utn.frvm.typeit.boero_api.academic.entities;

import ar.edu.utn.frvm.typeit.boero_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.DocumentRequirementLevel;
import jakarta.persistence.*;
import java.util.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "training_path_document_requirements")
@Getter
@NoArgsConstructor
public class TrainingPathDocumentRequirement {
  @Id @GeneratedUUIDv7 private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "training_path_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private TrainingPath trainingPath;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "document_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private DocumentDefinition document;

  @Column(name = "institution_id", nullable = false)
  @Audited
  private UUID institutionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Audited
  private DocumentRequirementLevel level;

  @Column(name = "display_order", nullable = false)
  @Audited
  private int displayOrder;

  @Column(nullable = false)
  @Audited
  private boolean active;

  @Column(name = "specific_instructions", length = 1000)
  private @Nullable String specificInstructions;

  @Version private long revision;

  public static TrainingPathDocumentRequirement create(
      final TrainingPath path, final DocumentDefinition document) {
    var value = new TrainingPathDocumentRequirement();
    value.trainingPath = path;
    value.document = document;
    value.institutionId = path.getInstitution().getId();
    return value;
  }

  public void update(
      final DocumentRequirementLevel level,
      final int order,
      final boolean active,
      final @Nullable String instructions) {
    this.level = level;
    this.displayOrder = order;
    this.active = active;
    this.specificInstructions =
        instructions == null || instructions.isBlank() ? null : instructions.trim();
  }

  public String getName() {
    return document.getName();
  }

  public String getInstructions() {
    return document.getInstructions();
  }

  public List<String> getAllowedFormats() {
    return document.getAllowedFormats();
  }

  public boolean isEffectiveActive() {
    return active && document.isActive() && trainingPath.getDeletedAt() == null;
  }
}
