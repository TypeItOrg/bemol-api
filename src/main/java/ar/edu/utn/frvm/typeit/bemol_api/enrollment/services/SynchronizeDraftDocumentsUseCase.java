package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.TrainingPathDocumentRequirementRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentApplicationRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class SynchronizeDraftDocumentsUseCase {
  private final EnrollmentApplicationRepository applications;
  private final TrainingPathDocumentRequirementRepository assignments;
  private final EnrollmentDocumentRequirementsService documents;
  private final JdbcTemplate jdbc;
  private final EntityManager entityManager;
  private final Clock clock;

  @Transactional(propagation = Propagation.MANDATORY)
  public int execute(final UUID institutionId, final Set<UUID> pathIds) {
    int count = 0;
    for (var pathId : pathIds.stream().sorted().toList()) {
      int offset = 0;
      while (true) {
        var ids =
            jdbc.query(
                "select enrollment_application_id from enrollment_applications where institution_id=? and training_path_id=? and status='DRAFT' and deleted_at is null order by enrollment_application_id limit 100 offset ? for update",
                (rs, row) -> Objects.requireNonNull(rs.getObject(1, UUID.class)),
                institutionId,
                pathId,
                offset);
        if (ids.isEmpty()) {
          break;
        }
        var sources = assignments.findByTrainingPathIdOrderByDisplayOrderAscIdAsc(pathId);
        for (var application : applications.findWithRequirementsByIds(ids)) {
          documents.synchronize(application, sources, clock.instant());
        }
        entityManager.flush();
        entityManager.clear();
        count += ids.size();
        offset += ids.size();
      }
    }
    return count;
  }
}
