package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.*;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.*;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.ScopedResourceNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.*;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.*;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.util.*;
import org.junit.jupiter.api.Test;

class EnrollmentPeriodAccessServiceTest {
  private final ScopedAuthorizationService authorization = mock(ScopedAuthorizationService.class);
  private final EnrollmentPeriodAccessService service =
      new EnrollmentPeriodAccessService(authorization);

  @Test
  void partialReadDoesNotExposeHiddenOfferingsOrMutationCapabilities() {
    var first = UUID.randomUUID();
    var second = UUID.randomUUID();
    var period = period(first, second);
    when(authorization.managementAccess(any()))
        .thenReturn(new PermissionAccess(AccessScope.TRAINING_PATHS, Set.of(first)));
    var response = service.response(period);
    assertThat(response.offerings()).extracting(o -> o.trainingPathId()).containsExactly(first);
    assertThat(response.limitedView()).isTrue();
    assertThat(response.canUpdate()).isFalse();
    assertThat(response.canChangeStatus()).isFalse();
    assertThat(response.canDelete()).isFalse();
    assertThatThrownBy(() -> service.requireManage(period, PermissionCode.ENROLLMENT_PERIOD_UPDATE))
        .isInstanceOf(ScopedResourceNotFoundException.class);
  }

  @Test
  void completeSelectionMayManageCurrentOfferingsButNotAnEmptyPeriod() {
    var first = UUID.randomUUID();
    var second = UUID.randomUUID();
    when(authorization.managementAccess(any()))
        .thenReturn(new PermissionAccess(AccessScope.TRAINING_PATHS, Set.of(first, second)));
    assertThat(service.canManage(period(first, second), PermissionCode.ENROLLMENT_PERIOD_UPDATE))
        .isTrue();
    assertThat(service.canManage(period(), PermissionCode.ENROLLMENT_PERIOD_UPDATE)).isFalse();
    when(authorization.managementAccess(any())).thenReturn(PermissionAccess.institution());
    assertThat(service.canManage(period(), PermissionCode.ENROLLMENT_PERIOD_UPDATE)).isTrue();
  }

  @Test
  void creationResponseUsesItsOperationWithoutGrantingReadAccess() {
    var path = UUID.randomUUID();
    var period = period(path);
    when(authorization.managementAccess(any())).thenReturn(PermissionAccess.none());
    when(authorization.managementAccess(PermissionCode.ENROLLMENT_PERIOD_CREATE))
        .thenReturn(new PermissionAccess(AccessScope.TRAINING_PATHS, Set.of(path)));
    assertThat(
            service
                .responseAfterMutation(period, PermissionCode.ENROLLMENT_PERIOD_CREATE)
                .offerings())
        .hasSize(1);
    assertThatThrownBy(() -> service.response(period))
        .isInstanceOf(ScopedResourceNotFoundException.class);
  }

  private EnrollmentPeriod period(UUID... paths) {
    var institution = Institution.builder().id(UUID.randomUUID()).build();
    var year = mock(AcademicYear.class);
    when(year.getYear()).thenReturn(2026);
    var period =
        EnrollmentPeriod.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .academicYear(year)
            .name("Periodo")
            .build();
    for (var id : paths) {
      var path = mock(TrainingPath.class);
      when(path.getId()).thenReturn(id);
      var plan = mock(StudyPlan.class);
      when(plan.getId()).thenReturn(UUID.randomUUID());
      when(plan.getTrainingPath()).thenReturn(path);
      period.getOfferings().add(EnrollmentPeriodOffering.create(period, plan));
    }
    return period;
  }
}
