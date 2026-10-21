package ar.edu.utn.frvm.typeit.bemol_api.audit.envers;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** A synchronization belongs to one transaction, including suspension for REQUIRES_NEW. */
public final class EnversAuditTransaction implements TransactionSynchronization {
  private final UUID id = UUID.randomUUID();

  private EnversAuditTransaction() {}

  public static @Nullable UUID currentId() {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return null;
    }
    for (final var synchronization : TransactionSynchronizationManager.getSynchronizations()) {
      if (synchronization instanceof EnversAuditTransaction transaction) {
        return transaction.id;
      }
    }
    final var transaction = new EnversAuditTransaction();
    TransactionSynchronizationManager.registerSynchronization(transaction);
    return transaction.id;
  }
}
