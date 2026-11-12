package ar.edu.utn.frvm.typeit.bemol_api.academic.entities;

import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSubject;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.DocumentRequirementLevel;
import jakarta.persistence.*;
import java.util.*;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "training_path_document_requirements")
@Getter
@NoArgsConstructor
public class TrainingPathDocumentRequirement implements AuditSubject {
  @Id @GeneratedUUIDv7 private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "training_path_id")
  private TrainingPath trainingPath;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "document_id")
  private DocumentDefinition document;

  @Column(name = "institution_id", nullable = false)
  private UUID institutionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DocumentRequirementLevel level;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(nullable = false)
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

  @Override
  public AuditSnapshot.Snapshot auditSnapshot() {
    return AuditSnapshot.fields()
        .compare("trainingPathId", "Trayecto formativo", trainingPath.getId())
        .expose("trainingPathName", "Trayecto formativo", trainingPath.getName())
        .expose(
            "level", "Nivel", level.name(), Map.of("code", level.name(), "label", level.getLabel()))
        .expose("displayOrder", "Orden", displayOrder)
        .expose("active", "Estado", active)
        .compare("specificInstructions", "Instrucciones específicas", specificInstructions)
        .build();
  }
}
