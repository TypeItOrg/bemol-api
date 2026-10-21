package ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentAttachment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.DocumentVersionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentAttachmentRepository extends JpaRepository<EnrollmentAttachment, UUID> {
  Optional<EnrollmentAttachment> findByIdAndEnrollmentApplicationIdAndDeletedAtIsNull(
      UUID id, UUID applicationId);

  List<EnrollmentAttachment> findByEnrollmentApplicationIdAndDeletedAtIsNull(UUID applicationId);

  Optional<EnrollmentAttachment>
      findByEnrollmentApplicationIdAndRequirementIdAndVersionStatusAndDeletedAtIsNull(
          UUID applicationId, UUID requirementId, DocumentVersionStatus status);

  Page<EnrollmentAttachment> findByEnrollmentApplicationIdAndRequirementIdAndDeletedAtIsNull(
      UUID applicationId, UUID requirementId, Pageable pageable);
}
