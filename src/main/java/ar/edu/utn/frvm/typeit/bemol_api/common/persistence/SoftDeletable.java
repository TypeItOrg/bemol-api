package ar.edu.utn.frvm.typeit.bemol_api.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

@MappedSuperclass
@Getter
public abstract class SoftDeletable extends Auditable {

  @Column(name = "deleted_at")
  private @Nullable Instant deletedAt;

  public boolean isDeleted() {
    return deletedAt != null;
  }

  protected boolean markDeleted(final Instant deletedAt) {
    if (isDeleted()) {
      return false;
    }
    this.deletedAt = deletedAt;
    return true;
  }

  public boolean restore() {
    if (!isDeleted()) {
      return false;
    }
    deletedAt = null;
    return true;
  }
}
