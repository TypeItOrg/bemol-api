package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.common.storage.StorageService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentDocumentAction;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentStorageJobRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class EnrollmentStorageCleanupUseCase {
  private final EnrollmentStorageJobRepository jobs;
  private final StorageService storage;
  private final EnrollmentDocumentAudit audit;
  private final Clock clock;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean processNext() {
    final var next = jobs.next(clock.instant());
    if (next.isEmpty()) {
      return false;
    }
    final var job = next.get();
    if (!job.destination().equals(storage.destination())) {
      jobs.retry(job, clock.instant(), "DESTINATION_MISMATCH");
      return true;
    }
    final var reference = jobs.activeReference(job.storagePath());
    if (reference.isPresent()) {
      jobs.activate(job.id(), reference.get(), clock.instant());
      return true;
    }

    try {
      storage.deletePhysicalFile(job.storagePath());
    } catch (RuntimeException exception) {
      jobs.retry(job, clock.instant(), "STORAGE_DELETE_FAILED");
      audit.record(
          job.actor(),
          job.institutionId(),
          job.applicationId(),
          job.attachmentId(),
          EnrollmentDocumentAction.PHYSICAL_DELETE,
          "RETRY");
      log.debug("[EnrollmentStorage] Cleanup deferred, jobId: {}", job.id());
      return true;
    }

    jobs.complete(job.id(), clock.instant());
    audit.record(
        job.actor(),
        job.institutionId(),
        job.applicationId(),
        job.attachmentId(),
        EnrollmentDocumentAction.PHYSICAL_DELETE,
        "SUCCESS");
    log.info("[EnrollmentStorage] Cleanup completed, jobId: {}", job.id());
    return true;
  }
}
