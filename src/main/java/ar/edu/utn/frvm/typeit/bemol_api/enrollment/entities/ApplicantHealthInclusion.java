package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSubject;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.SoftDeletable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "applicant_health_inclusions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ApplicantHealthInclusion extends SoftDeletable implements AuditSubject {

  @Id
  @GeneratedUUIDv7
  @Column(name = "applicant_health_inclusion_id")
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "enrollment_application_id", nullable = false)
  private EnrollmentApplication enrollmentApplication;

  @Column(name = "receives_reasonable_adjustments", nullable = false)
  @Builder.Default
  private boolean receivesReasonableAdjustments = false;

  @Column(name = "adjustment_details", columnDefinition = "text")
  private String adjustmentDetails;

  @Override
  public AuditSnapshot.Snapshot auditSnapshot() {
    return AuditSnapshot.fields()
        .compare(
            "receivesReasonableAdjustments",
            "Recibe ajustes razonables",
            receivesReasonableAdjustments)
        .compare("adjustmentDetails", "Detalles de ajustes", adjustmentDetails)
        .build();
  }
}
