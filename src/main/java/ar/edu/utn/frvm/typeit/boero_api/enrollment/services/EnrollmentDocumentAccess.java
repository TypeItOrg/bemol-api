package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentDocumentAction;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.ApplicationNotEditableException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentApplicationNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentApplicationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EnrollmentDocumentAccess {
  private final EnrollmentApplicationRepository applications;
  private final EnrollmentDocumentAuthorization authorization;
  private final EnrollmentInstitutionLock institutionLock;
  private final EntityManager entityManager;
  private final EnrollmentDocumentRequirementsService documents;

  @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
  public EnrollmentApplication activeApplication(final UUID applicationId) {
    return applications
        .findById(applicationId)
        .filter(application -> application.getDeletedAt() == null)
        .orElseThrow(() -> new EnrollmentApplicationNotFoundException(applicationId));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public EnrollmentApplication lockApplication(
      final UUID applicationId,
      final @Nullable Authentication authentication,
      final PermissionCode permission,
      final EnrollmentDocumentAction action,
      final @Nullable UUID attachmentId) {
    final var application = activeApplication(applicationId);
    authorization.require(application, authentication, permission, action, attachmentId);
    final var institutionId = application.getInstitution().getId();
    institutionLock.lock(institutionId);

    final var locked =
        applications
            .findForAttachmentUpdate(applicationId, institutionId)
            .orElseThrow(() -> new EnrollmentApplicationNotFoundException(applicationId));
    // Recheck authorization and lifecycle after acquiring the tenant-scoped write lock.
    entityManager.refresh(locked, LockModeType.PESSIMISTIC_WRITE);
    authorization.require(locked, authentication, permission, action, attachmentId);
    if (locked.getDeletedAt() != null) {
      throw new EnrollmentApplicationNotFoundException(applicationId);
    }

    return locked;
  }

  public void requireEditable(final EnrollmentApplication application) {
    if (!documents.editable(application)) {
      throw new ApplicationNotEditableException(application.getId());
    }
  }
}
