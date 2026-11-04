package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicSpaceNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.InstrumentNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.ShiftNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.TrainingPathNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.ActiveStatusRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateInstrumentRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateShiftRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateTrainingPathRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateAcademicSpaceStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateInstrumentStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateInstrumentUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateShiftStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateShiftUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateTrainingPathStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateTrainingPathUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailSender;
import ar.edu.utn.frvm.typeit.bemol_api.support.IntegrationTest;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class AcademicCatalogLifecyclePostgresIntegrationTest extends DatabaseMigrationTestSupport {
  @Autowired UpdateTrainingPathUseCase updatePath;
  @Autowired UpdateTrainingPathStatusUseCase updatePathStatus;
  @Autowired UpdateInstrumentUseCase updateInstrument;
  @Autowired UpdateInstrumentStatusUseCase updateInstrumentStatus;
  @Autowired UpdateShiftUseCase updateShift;
  @Autowired UpdateShiftStatusUseCase updateShiftStatus;
  @Autowired UpdateAcademicSpaceStatusUseCase updateSpaceStatus;
  @Autowired TransactionTemplate transaction;
  @MockitoBean MailSender mailSender;

  @ParameterizedTest
  @EnumSource(CatalogUpdate.class)
  void pendingOrdinaryUpdateCannotRestoreAConcurrentlyDeletedRecord(final CatalogUpdate update)
      throws Exception {
    final UUID institutionId = UUID.randomUUID(), resourceId = UUID.randomUUID();
    fixtures.insertTestInstitution(institutionId);
    insertInactiveCatalog(update, institutionId, resourceId);
    final var started = new CountDownLatch(1);

    try (final var executor = Executors.newSingleThreadExecutor()) {
      final var waiting =
          requireNonNull(
              transaction.execute(
                  status -> {
                    jdbcTemplate.queryForObject(
                        "SELECT "
                            + update.idColumn
                            + " FROM "
                            + update.table
                            + " WHERE "
                            + update.idColumn
                            + " = ? FOR UPDATE",
                        UUID.class,
                        resourceId);
                    final var future =
                        executor.submit(
                            () -> {
                              authenticatePlatformAdministrator();
                              started.countDown();
                              try {
                                executeUpdate(update, institutionId, resourceId);
                              } finally {
                                SecurityContextHolder.clearContext();
                              }
                            });
                    await(started);
                    assertThatThrownBy(() -> future.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                    jdbcTemplate.update(
                        "UPDATE "
                            + update.table
                            + " SET deleted_at = CURRENT_TIMESTAMP WHERE "
                            + update.idColumn
                            + " = ?",
                        resourceId);
                    return future;
                  }));

      assertThatThrownBy(() -> waiting.get(10, TimeUnit.SECONDS))
          .hasCauseInstanceOf(update.notFound);
    }

    final var stored =
        jdbcTemplate.queryForMap(
            "SELECT name, active, deleted_at FROM "
                + update.table
                + " WHERE "
                + update.idColumn
                + " = ?",
            resourceId);
    assertThat(stored).containsEntry("name", "Original").containsEntry("active", false);
    assertThat(stored.get("deleted_at")).isNotNull();
  }

  private void insertInactiveCatalog(
      final CatalogUpdate update, final UUID institutionId, final UUID resourceId) {
    switch (update) {
      case PATH_EDIT, PATH_STATUS ->
          fixtures.insertTrainingPath(resourceId, institutionId, "Original");
      case INSTRUMENT_EDIT, INSTRUMENT_STATUS ->
          fixtures.insertInstrument(resourceId, institutionId, "Original");
      case SHIFT_EDIT, SHIFT_STATUS ->
          fixtures.insertShift(resourceId, institutionId, "Original", false);
      case SPACE_STATUS -> fixtures.insertAcademicSpace(resourceId, institutionId, "Original");
    }
    jdbcTemplate.update(
        "UPDATE " + update.table + " SET active = false WHERE " + update.idColumn + " = ?",
        resourceId);
  }

  private void executeUpdate(
      final CatalogUpdate update, final UUID institutionId, final UUID resourceId) {
    switch (update) {
      case PATH_EDIT ->
          updatePath.execute(
              institutionId, resourceId, new UpdateTrainingPathRequest("Changed", null));
      case PATH_STATUS ->
          updatePathStatus.execute(institutionId, resourceId, new ActiveStatusRequest(true));
      case INSTRUMENT_EDIT ->
          updateInstrument.execute(
              institutionId, resourceId, new UpdateInstrumentRequest("Changed", null));
      case INSTRUMENT_STATUS ->
          updateInstrumentStatus.execute(institutionId, resourceId, new ActiveStatusRequest(true));
      case SHIFT_EDIT ->
          updateShift.execute(institutionId, resourceId, new UpdateShiftRequest("Changed", null));
      case SHIFT_STATUS ->
          updateShiftStatus.execute(institutionId, resourceId, new ActiveStatusRequest(true));
      case SPACE_STATUS ->
          updateSpaceStatus.execute(institutionId, resourceId, new ActiveStatusRequest(true));
    }
  }

  private static void await(final CountDownLatch latch) {
    try {
      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  enum CatalogUpdate {
    PATH_EDIT("training_paths", "training_path_id", TrainingPathNotFoundException.class),
    PATH_STATUS("training_paths", "training_path_id", TrainingPathNotFoundException.class),
    INSTRUMENT_EDIT("instruments", "instrument_id", InstrumentNotFoundException.class),
    INSTRUMENT_STATUS("instruments", "instrument_id", InstrumentNotFoundException.class),
    SHIFT_EDIT("shifts", "shift_id", ShiftNotFoundException.class),
    SHIFT_STATUS("shifts", "shift_id", ShiftNotFoundException.class),
    SPACE_STATUS("academic_spaces", "academic_space_id", AcademicSpaceNotFoundException.class);

    private final String table;
    private final String idColumn;
    private final Class<? extends Throwable> notFound;

    CatalogUpdate(
        final String table, final String idColumn, final Class<? extends Throwable> notFound) {
      this.table = table;
      this.idColumn = idColumn;
      this.notFound = notFound;
    }
  }
}
