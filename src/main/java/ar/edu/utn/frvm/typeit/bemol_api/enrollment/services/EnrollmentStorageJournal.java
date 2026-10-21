package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.common.storage.StorageService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentStorageJobRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentDocumentAudit.Actor;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentFilePolicy.StoredFile;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentStorageJournal {
  private final EnrollmentStorageJobRepository jobs;
  private final StorageService storage;
  private final Clock clock;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public UUID reserve(
      final UUID institutionId,
      final UUID applicationId,
      final StoredFile file,
      final Actor actor) {
    final var now = clock.instant();
    return jobs.insert(
        storage.destination(),
        file.storagePath(),
        institutionId,
        applicationId,
        null,
        actor,
        now,
        now.plus(Duration.ofHours(24)));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void lockReservation(final UUID id) {
    final var job =
        jobs.lock(id)
            .orElseThrow(
                () -> new IllegalStateException(EnrollmentMessages.STORAGE_JOB_UNAVAILABLE));
    if (!"PENDING".equals(job.state()) || !job.destination().equals(storage.destination())) {
      throw new IllegalStateException(EnrollmentMessages.STORAGE_JOB_UNAVAILABLE);
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void confirm(final UUID id, final UUID attachmentId) {
    jobs.activate(id, attachmentId, clock.instant());
  }
}
