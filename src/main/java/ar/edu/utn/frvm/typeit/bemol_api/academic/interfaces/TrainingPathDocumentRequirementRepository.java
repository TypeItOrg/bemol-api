package ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPathDocumentRequirement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingPathDocumentRequirementRepository
    extends JpaRepository<TrainingPathDocumentRequirement, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<
            TrainingPathDocumentRequirement> {
  List<TrainingPathDocumentRequirement> findByTrainingPathIdOrderByDisplayOrderAscIdAsc(UUID id);

  long countByDocumentId(UUID documentId);

  @EntityGraph(attributePaths = "trainingPath")
  List<TrainingPathDocumentRequirement> findByDocumentId(UUID documentId);

  Optional<TrainingPathDocumentRequirement> findByTrainingPathIdAndDocumentId(
      UUID pathId, UUID documentId);

  Optional<TrainingPathDocumentRequirement> findByIdAndTrainingPathId(UUID id, UUID pathId);
}
