package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "institution_enrollment_locks")
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class InstitutionEnrollmentLock {

  @Id
  @Column(name = "institution_id")
  private UUID institutionId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public static InstitutionEnrollmentLock create(
      final UUID institutionId, final Instant createdAt) {
    return InstitutionEnrollmentLock.builder()
        .institutionId(institutionId)
        .createdAt(createdAt)
        .build();
  }
}
