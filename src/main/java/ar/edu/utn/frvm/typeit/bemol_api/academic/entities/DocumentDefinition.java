package ar.edu.utn.frvm.typeit.bemol_api.academic.entities;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.DocumentCatalogException;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditResource;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSubject;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "document_definitions")
@Getter
@NoArgsConstructor
public class DocumentDefinition implements AuditSubject {
  public static final AuditResource AUDIT_TYPE =
      new AuditResource("DOCUMENT_DEFINITION", "Documento");

  @Id @GeneratedUUIDv7 private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id")
  private Institution institution;

  @Column(nullable = false, length = 150)
  private String name;

  @Column(nullable = false, length = 1000)
  private String instructions;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "allowed_formats", nullable = false, columnDefinition = "jsonb")
  private List<String> allowedFormats;

  @Column(nullable = false)
  private boolean active;

  @Version private long revision;

  @Column(name = "used_at", insertable = false, updatable = false)
  private @Nullable Instant usedAt;

  public static DocumentDefinition create(final Institution institution) {
    var value = new DocumentDefinition();
    value.institution = institution;
    return value;
  }

  public void changeInstitution(final Institution destination) {
    if (usedAt != null) {
      throw new DocumentCatalogException(
          ErrorCategory.CONFLICT, AcademicMessages.DOCUMENT_INSTITUTION_CHANGE_BLOCKED);
    }
    this.institution = destination;
  }

  public void update(
      final String name,
      final String instructions,
      final List<String> formats,
      final boolean active) {
    this.name = name.trim();
    this.instructions = instructions.trim();
    this.allowedFormats = formats.stream().distinct().sorted().toList();
    this.active = active;
  }

  private AuditSnapshot.Builder auditFields() {
    return AuditSnapshot.fields()
        .expose("name", "Nombre", name)
        .compare("institutionId", "Institución", institution.getId())
        .expose("institutionName", "Institución", institution.getName())
        .compare("instructions", "Instrucciones", instructions)
        .expose("allowedFormats", "Formatos permitidos", allowedFormats)
        .expose("active", "Estado", active);
  }

  @Override
  public AuditSnapshot.Snapshot auditSnapshot() {
    return auditFields().build();
  }

  public AuditSnapshot.Snapshot auditSnapshot(
      final List<TrainingPathDocumentRequirement> assignments) {
    final var snapshots =
        assignments.stream()
            .sorted(Comparator.comparing(value -> value.getTrainingPath().getId()))
            .map(entry -> entry.auditSnapshot())
            .toList();
    final var comparison = snapshots.stream().map(entry -> entry.values()).toList();
    final var visible = snapshots.stream().map(entry -> entry.labeledValues()).toList();
    return auditFields()
        .expose("requirementsCount", "Requisitos documentales", assignments.size())
        .expose("requirements", "Asignaciones documentales", comparison, visible)
        .build();
  }

  public static boolean definitionChanged(
      final AuditSnapshot.Snapshot before, final AuditSnapshot.Snapshot after) {
    return List.of("name", "instructions", "allowedFormats", "active").stream()
        .anyMatch(field -> !Objects.equals(before.values().get(field), after.values().get(field)));
  }
}
