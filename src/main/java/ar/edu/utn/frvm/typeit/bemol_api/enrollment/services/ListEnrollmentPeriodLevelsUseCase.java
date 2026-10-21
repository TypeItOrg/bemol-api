package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static ar.edu.utn.frvm.typeit.bemol_api.security.handlers.SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE;

import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicLevelRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicLevelResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.*;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListEnrollmentPeriodLevelsUseCase {
  private final AcademicLevelRepository levels;
  private final AcademicAccessGuard accessGuard;

  @Transactional(readOnly = true)
  public List<AcademicLevelResponse> execute(
      UUID institutionId, UUID studyPlanId, PermissionCode operation) {
    if (operation != PermissionCode.ENROLLMENT_PERIOD_CREATE
        && operation != PermissionCode.ENROLLMENT_PERIOD_UPDATE) {
      throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
    }
    accessGuard.require(operation, institutionId, ScopedResource.STUDY_PLAN, studyPlanId);
    return levels.findByStudyPlan_IdOrderByDisplayOrderAsc(studyPlanId).stream()
        .map(AcademicLevelResponse::from)
        .toList();
  }
}
