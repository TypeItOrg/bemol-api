package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSubject;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "enrollment_document_requests")
@Getter
@NoArgsConstructor
public class EnrollmentDocumentRequest implements AuditSubject {
  @Id @GeneratedUUIDv7 private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "application_id")
  private EnrollmentApplication application;

  @Column(name = "institution_id", nullable = false)
  private UUID institutionId;

  @Column(nullable = false, length = 2000)
  private String reason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "actor_id", nullable = false)
  private UUID actorId;

  @Column(name = "account_type", nullable = false)
  private String accountType;

  @Column(name = "actor_name", nullable = false, length = 300)
  private String actorName;

  public static EnrollmentDocumentRequest create(
      final EnrollmentApplication application,
      final String reason,
      final Instant at,
      final UUID actorId,
      final String accountType,
      final String actorName) {
    var value = new EnrollmentDocumentRequest();
    value.application = application;
    value.institutionId = application.getInstitution().getId();
    value.reason = reason.trim();
    value.createdAt = at;
    value.actorId = actorId;
    value.accountType = accountType;
    value.actorName = actorName;
    return value;
  }

  private AuditSnapshot.Builder auditFields() {
    return AuditSnapshot.fields().compare("requestReason", "Motivo del pedido", reason);
  }

  @Override
  public AuditSnapshot.Snapshot auditSnapshot() {
    return auditFields().build();
  }

  public AuditSnapshot.Snapshot auditSnapshot(final int requirementCount) {
    return auditFields()
        .expose("requirementCount", "Requisitos solicitados", requirementCount)
        .build();
  }
}
