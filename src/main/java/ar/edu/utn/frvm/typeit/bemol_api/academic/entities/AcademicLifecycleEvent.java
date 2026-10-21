package ar.edu.utn.frvm.typeit.bemol_api.academic.entities;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicLifecycleAction;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicLifecycleResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.AccountType;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "academic_lifecycle_events")
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PACKAGE)
public class AcademicLifecycleEvent {

  @Id
  @GeneratedUUIDv7
  @Column(name = "academic_lifecycle_event_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  private Institution institution;

  @Enumerated(EnumType.STRING)
  @Column(name = "resource_type", nullable = false, length = 30)
  private AcademicLifecycleResource resourceType;

  @Column(name = "resource_id", nullable = false)
  private UUID resourceId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AcademicLifecycleAction action;

  @Enumerated(EnumType.STRING)
  @Column(name = "actor_type", nullable = false, length = 20)
  private AccountType actorType;

  @Column(name = "actor_id", nullable = false)
  private UUID actorId;

  @Column(length = 500)
  private @Nullable String reason;

  @Column(name = "request_id", length = 36)
  private String requestId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public static AcademicLifecycleEvent create(
      final Institution institution,
      final AcademicLifecycleResource resourceType,
      final UUID resourceId,
      final AcademicLifecycleAction action,
      final AccountType actorType,
      final UUID actorId,
      final @Nullable String reason,
      final String requestId,
      final Instant createdAt) {
    return AcademicLifecycleEvent.builder()
        .institution(institution)
        .resourceType(resourceType)
        .resourceId(resourceId)
        .action(action)
        .actorType(actorType)
        .actorId(actorId)
        .reason(reason)
        .requestId(requestId)
        .createdAt(createdAt)
        .build();
  }
}
