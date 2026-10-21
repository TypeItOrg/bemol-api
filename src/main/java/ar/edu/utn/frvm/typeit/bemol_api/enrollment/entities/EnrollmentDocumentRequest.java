package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

@Entity
@Table(name = "enrollment_document_requests")
@Getter
@NoArgsConstructor
public class EnrollmentDocumentRequest {
  @Id @GeneratedUUIDv7 private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "application_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private EnrollmentApplication application;

  @Column(name = "institution_id", nullable = false)
  @Audited
  private UUID institutionId;

  @Column(nullable = false, length = 2000)
  private String reason;

  @Column(name = "created_at", nullable = false)
  @Audited
  private Instant createdAt;

  @Column(name = "actor_id", nullable = false)
  @Audited
  private UUID actorId;

  @Column(name = "account_type", nullable = false)
  @Audited
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
}
