package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
  void doesNotBindAnExistingDraftOrSubmittedApplication() {
    final var version = mock(EnrollmentFormVersion.class);
    final var existing =
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

    assertThatThrownBy(() -> existing.bindFormVersion(version))
        .isInstanceOf(EnrollmentFormException.class);
    assertThatThrownBy(() -> submitted.bindFormVersion(version))
        .isInstanceOf(EnrollmentFormException.class);
    assertThat(existing.getFormState().getFormVersion()).isNull();
    assertThat(submitted.getFormState().getFormVersion()).isNull();
  }
}
