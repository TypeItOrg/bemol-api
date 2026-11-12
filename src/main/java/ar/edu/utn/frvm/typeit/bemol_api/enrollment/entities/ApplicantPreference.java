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
@Table(name = "applicant_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ApplicantPreference extends SoftDeletable implements AuditSubject {

  @Id
  @GeneratedUUIDv7
  @Column(name = "applicant_preference_id")
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "enrollment_application_id", nullable = false)
  private EnrollmentApplication enrollmentApplication;

  // Un DRAFT es parcial por definición: submitApplication() exige este campo
  // recién al enviar, así que la columna acepta NULL (migración 20260908150000).
  @Column(name = "preferred_shift", length = 150)
  private String preferredShift;

  @Column(name = "allows_image_use", nullable = false)
  @Builder.Default
  private boolean allowsImageUse = false;

  @Column(name = "is_reenrolling", nullable = false)
  @Builder.Default
  private boolean isReenrolling = false;

  @Column(name = "previous_teacher", length = 150)
  private String previousTeacher;

  public void setIsReenrolling(boolean isReenrolling) {
    this.isReenrolling = isReenrolling;
  }

  @Override
  public AuditSnapshot.Snapshot auditSnapshot() {
    return AuditSnapshot.fields()
        .compare("preferredShift", "Turno preferido", preferredShift)
        .compare("allowsImageUse", "Autoriza uso de imagen", allowsImageUse)
        .compare("reenrolling", "Reinscripción", isReenrolling)
        .compare("previousTeacher", "Docente anterior", previousTeacher)
        .build();
  }
}
