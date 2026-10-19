package ar.edu.utn.frvm.typeit.boero_api.auth.entities;

import ar.edu.utn.frvm.typeit.boero_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.GeneratedUUIDv7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@Entity
@Table(
    name = "passkey_credentials",
    uniqueConstraints =
        @UniqueConstraint(
            name = "passkey_credentials_credential_id_key",
            columnNames = "credential_id"),
    indexes = {
      @Index(name = "passkey_credentials_user_active_idx", columnList = "user_id"),
      @Index(name = "passkey_credentials_user_id_idx", columnList = "user_id")
    })
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PasskeyCredential extends Auditable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "passkey_credential_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private User user;

  @Column(name = "credential_id", nullable = false, columnDefinition = "TEXT")
  private String credentialId;

  @Column(name = "public_key_cose", nullable = false)
  private byte[] publicKeyCose;

  @Column(name = "signature_count", nullable = false)
  @Builder.Default
  private long signatureCount = 0L;

  @Column(name = "transports", length = 255)
  private @Nullable String transports;

  @Column(name = "backup_eligible", nullable = false)
  @Builder.Default
  private boolean backupEligible = false;

  @Column(name = "backup_state", nullable = false)
  @Builder.Default
  private boolean backupState = false;

  @Column(name = "uv_initialized", nullable = false)
  @Builder.Default
  private boolean uvInitialized = false;

  @Column(name = "user_handle", nullable = false)
  private byte[] userHandle;

  @Column(name = "attestation_object")
  private byte @Nullable [] attestationObject;

  @Column(name = "attestation_client_data_json")
  private byte @Nullable [] attestationClientDataJson;

  @Column(name = "label", nullable = false, length = 100)
  private String label;

  @Column(name = "last_used_at")
  private @Nullable Instant lastUsedAt;

  @Column(name = "revoked_at")
  @Audited
  private @Nullable Instant revokedAt;

  public boolean isActive() {
    return revokedAt == null;
  }

  public void rename(final String label) {
    final String trimmed = label == null ? "" : label.trim();
    final boolean validLength = !trimmed.isEmpty() && trimmed.length() <= 100;
    if (!validLength) {
      throw new IllegalArgumentException("El nombre debe tener entre 1 y 100 caracteres.");
    }
    this.label = trimmed;
  }

  public boolean revoke(final Instant now) {
    if (!isActive()) {
      return false;
    }
    this.revokedAt = now;
    return true;
  }

  public void markUsed(final Instant now, final long signatureCount) {
    this.lastUsedAt = now;
    this.signatureCount = signatureCount;
  }

  public Set<String> transportSet() {
    if (transports == null || transports.isBlank()) {
      return Collections.emptySet();
    }
    final Set<String> parsed =
        Arrays.stream(transports.split(","))
            .map(mappedString -> mappedString.trim())
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    return Collections.unmodifiableSet(parsed);
  }

  public void applyTransports(final Set<String> transportValues) {
    if (transportValues == null || transportValues.isEmpty()) {
      this.transports = null;
      return;
    }
    this.transports = String.join(",", transportValues);
  }
}
