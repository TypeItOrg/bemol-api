package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.TrainingPathDocumentRequirementRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.*;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.*;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrainingPathDocumentRequirementService {
  private final TrainingPathDocumentRequirementRepository requirements;
  private final AcademicAccessGuard access;
  private final DocumentCatalogUseCase catalog;

  @Transactional(readOnly = true)
  public List<DocumentRequirementResponse> list(final UUID institutionId, final UUID pathId) {
    access.require(
        PermissionCode.TRAINING_PATH_READ, institutionId, ScopedResource.TRAINING_PATH, pathId);
    return requirements.findByTrainingPathIdOrderByDisplayOrderAscIdAsc(pathId).stream()
        .map(DocumentRequirementResponse::from)
        .toList();
  }

  @Transactional
  public DocumentRequirementResponse save(
      final UUID institutionId,
      final UUID pathId,
      final @Nullable UUID id,
      final DocumentRequirementRequest request) {
    return catalog.saveAssignment(institutionId, pathId, id, request);
  }
}
