package ar.edu.utn.frvm.typeit.boero_api.academic.interfaces;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.DocumentDefinition;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentDefinitionRepository
    extends JpaRepository<DocumentDefinition, UUID>, JpaSpecificationExecutor<DocumentDefinition> {
  @Override
  @EntityGraph(attributePaths = "institution")
  Page<DocumentDefinition> findAll(
      Specification<DocumentDefinition> specification, Pageable pageable);

  @Query(
      "SELECT COUNT(r) > 0 FROM EnrollmentDocumentRequirement r WHERE r.document.id = :documentId")
  boolean hasEnrollmentUsage(@Param("documentId") UUID documentId);

  Optional<DocumentDefinition> findByIdAndInstitutionId(UUID id, UUID institutionId);
}
