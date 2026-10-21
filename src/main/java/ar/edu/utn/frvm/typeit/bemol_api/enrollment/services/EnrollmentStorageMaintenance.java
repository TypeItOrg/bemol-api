package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.common.storage.StorageService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentStorageJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@Slf4j
@RequiredArgsConstructor
public class EnrollmentStorageMaintenance implements ApplicationRunner {
  private final EnrollmentStorageJobRepository jobs;
  private final StorageService storage;
  private final EnrollmentStorageCleanupUseCase cleanup;
  private volatile boolean ready;

  @Override
  public void run(final ApplicationArguments args) {
    if (jobs.hasDifferentDestination(storage.destination())) {
      throw new IllegalStateException(EnrollmentMessages.STORAGE_DESTINATION_PENDING);
    }
    ready = true;
  }

  @Scheduled(initialDelay = 60000, fixedDelay = 60000)
  public void cleanPendingObjects() {
    if (!ready) {
      return;
    }
    try {
      for (int count = 0; count < 100; count++) {
        if (!cleanup.processNext()) {
          break;
        }
      }
    } catch (RuntimeException exception) {
      // No HTTP handler observes background failures; leave one operational signal without
      // payloads.
      log.error(
          "[EnrollmentStorage] Cleanup processing failed, errorType: {}",
          exception.getClass().getSimpleName());
    }
  }
}
