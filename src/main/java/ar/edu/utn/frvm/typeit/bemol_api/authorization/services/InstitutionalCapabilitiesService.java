package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import static ar.edu.utn.frvm.typeit.bemol_api.security.handlers.SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstitutionalCapabilitiesService {
  private final CachedAuthoritySnapshotResolver authorities;

  public InstitutionalCapabilities resolve(final UUID institutionId, final UUID personId) {
    return InstitutionalCapabilities.from(
        authorities.resolveFreshForPerson(personId, institutionId));
  }

  public void requireOwnEnrollmentRead(final UUID institutionId, final UUID personId) {
    if (!resolve(institutionId, personId).canViewOwnEnrollmentApplications()) {
      throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
    }
  }

  public void requireOwnEnrollmentManage(final UUID institutionId, final UUID personId) {
    if (!resolve(institutionId, personId).canManageOwnEnrollmentApplications()) {
      throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
    }
  }

  public void requireOwnFormation(final UUID institutionId, final UUID personId) {
    if (!resolve(institutionId, personId).canViewOwnFormation()) {
      throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
    }
  }

  public void requireTeachingRead(final UUID institutionId, final UUID personId) {
    if (!resolve(institutionId, personId).canViewTeaching()) {
      throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
    }
  }

  public void requireTeachingAcademicStatusUpdate(final UUID institutionId, final UUID personId) {
    if (!resolve(institutionId, personId).canUpdateTeachingAcademicStatus()) {
      throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
    }
  }
}
