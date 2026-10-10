package ar.edu.utn.frvm.typeit.boero_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.boero_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.SoftDeletable;
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
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "applicant_responsibles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ApplicantResponsible extends SoftDeletable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "applicant_responsible_id")
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "enrollment_application_id", nullable = false)
  private EnrollmentApplication enrollmentApplication;

  // Un DRAFT es parcial por definición: submitApplication() exige estos campos
  // recién al enviar, así que la columna acepta NULL (migración 20260908150000).
  @Column(name = "full_name", length = 200)
  private @Nullable String fullName;

  @Column(name = "document_number", length = 20)
  private @Nullable String documentNumber;

  @Column(name = "occupation", length = 100)
  private @Nullable String occupation;

  @Column(name = "phone_number", length = 50)
  private @Nullable String phoneNumber;

  @Column(name = "email", length = 150)
  private @Nullable String email;

  @Column(name = "education_level", length = 50)
  private @Nullable String educationLevel;
}
