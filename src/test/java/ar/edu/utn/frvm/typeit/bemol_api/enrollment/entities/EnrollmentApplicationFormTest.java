package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormMessages;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrollmentApplicationFormTest {
  private final Institution institution = Institution.builder().id(UUID.randomUUID()).build();

  @Test
  void baseFormHasNoInstitutionalVersionOrAdditionalAnswers() {
    final var application = EnrollmentApplication.builder().build();

    assertThat(application.getFormState().getFormVersion()).isNull();
    assertThat(application.getFormState().getCustomAnswers()).isEmpty();
    assertThat(application.getFormState().getDraftRevision()).isZero();
  }

  @Test
  void bindsOnlyOnceBeforePersistenceAndChecksTheVersionInstitutionAndState() {
    final var version = mock(EnrollmentFormVersion.class);
    final var application =
        EnrollmentApplication.builder()
            .institution(institution)
            .status(EnrollmentApplicationStatus.DRAFT)
            .build();

    application.bindFormVersion(version);

    assertThat(application.getFormState().getFormVersion()).isSameAs(version);
    verify(version).requireAssignableTo(institution.getId());
    assertThatThrownBy(() -> application.bindFormVersion(version))
        .isInstanceOf(EnrollmentFormException.class)
        .hasMessage(EnrollmentFormMessages.APPLICATION_FORM_IMMUTABLE);
  }

  @Test
  void doesNotBindAnExistingLegacyDraftOrSubmittedApplication() {
    final var version = mock(EnrollmentFormVersion.class);
    final var legacy =
        EnrollmentApplication.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .status(EnrollmentApplicationStatus.DRAFT)
            .build();
    final var submitted =
        EnrollmentApplication.builder()
            .institution(institution)
            .status(EnrollmentApplicationStatus.SUBMITTED)
            .build();

    assertThatThrownBy(() -> legacy.bindFormVersion(version))
        .isInstanceOf(EnrollmentFormException.class);
    assertThatThrownBy(() -> submitted.bindFormVersion(version))
        .isInstanceOf(EnrollmentFormException.class);
    assertThat(legacy.getFormState().getFormVersion()).isNull();
    assertThat(submitted.getFormState().getFormVersion()).isNull();
  }

  @Test
  void aPinnedFormCannotMoveToAnotherTrainingPathButCanChangePlanWithinItsPath() {
    final var path = mock(TrainingPath.class);
    when(path.getId()).thenReturn(UUID.randomUUID());
    final var samePathPlan = mock(StudyPlan.class);
    when(samePathPlan.getTrainingPath()).thenReturn(path);
    final var otherPath = mock(TrainingPath.class);
    when(otherPath.getId()).thenReturn(UUID.randomUUID());
    final var otherPlan = mock(StudyPlan.class);
    when(otherPlan.getTrainingPath()).thenReturn(otherPath);
    final var application =
        EnrollmentApplication.builder()
            .institution(institution)
            .trainingPathId(path.getId())
            .trainingPath(path)
            .status(EnrollmentApplicationStatus.DRAFT)
            .build();
    application.bindFormVersion(mock(EnrollmentFormVersion.class));

    application.changeStudyPlan(samePathPlan);

    assertThat(application.getStudyPlan()).isSameAs(samePathPlan);
    assertThatThrownBy(() -> application.changeStudyPlan(otherPlan))
        .isInstanceOf(EnrollmentFormException.class)
        .hasMessage(EnrollmentFormMessages.APPLICATION_FORM_CONTEXT_IMMUTABLE);
    assertThat(application.getTrainingPathId()).isEqualTo(path.getId());
    assertThat(application.getStudyPlan()).isSameAs(samePathPlan);
  }
}
