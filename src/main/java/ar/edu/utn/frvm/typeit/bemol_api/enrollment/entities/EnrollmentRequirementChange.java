package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "enrollment_requirement_changes")
@Getter
@NoArgsConstructor
public class EnrollmentRequirementChange {
  @Id @GeneratedUUIDv7 private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "requirement_id")
  private EnrollmentDocumentRequirement requirement;

  @Column(nullable = false)
  private String action;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "actor_id")
  private @Nullable UUID actorId;

  @Column(name = "account_type", nullable = false)
  private String accountType;

  @Column(nullable = false, length = 150)
  private String name;

  public static EnrollmentRequirementChange create(
      final EnrollmentDocumentRequirement requirement,
      final String action,
      final Instant at,
      final @Nullable UUID actorId,
      final String accountType) {
    var value = new EnrollmentRequirementChange();
    value.requirement = requirement;
    value.action = action;
    value.occurredAt = at;
    value.actorId = actorId;
    value.accountType = accountType;
    value.name = requirement.getName();
    return value;
  }
}
