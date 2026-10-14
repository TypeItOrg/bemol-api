package ar.edu.utn.frvm.typeit.boero_api.enrollment.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.ApplicationNotEditableException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.InvalidEnrollmentApplicationStateException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.MissingRejectionReasonException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EnrollmentApplicationTest {

  private static final Instant RESOLVED_AT = Instant.parse("2026-09-06T10:00:00Z");
  private static final UUID RESOLVER_PERSON_ID = UUID.randomUUID();

  @ParameterizedTest
  @CsvSource({
    "DRAFT, true", "SUBMITTED, false", "CANCELLED, false",
    "PROVISIONALLY_APPROVED, false", "APPROVED, false", "REJECTED, false"
  })
  void editableOnlyWhileDraft(final EnrollmentApplicationStatus status, final boolean editable) {
    final var application = EnrollmentApplication.builder().status(status).build();

    assertThat(application.isEditable()).isEqualTo(editable);
  }

  @Test
  void managesSelectedSpacesAndTheirOwningApplication() {
    final var application = draft();
    final var selectedSpace =
        EnrollmentApplicationSpace.builder().studyPlanSpace(new StudyPlanSpace()).build();

    application.addSelectedSpace(selectedSpace);

    assertThat(application.getSelectedSpaces()).containsExactly(selectedSpace);
    assertThat(selectedSpace.getEnrollmentApplication()).isSameAs(application);

    application.clearSelectedSpaces();

    assertThat(application.getSelectedSpaces()).isEmpty();
  }

  @Test
  void submit_transitionsDraftToSubmitted() {
    final var application = draft();

    application.submit();

    assertThat(application.getStatus()).isEqualTo(EnrollmentApplicationStatus.SUBMITTED);
  }

  @Test
  void submit_rejectsAlreadySubmittedApplication() {
    final var application = submitted();

    assertThatThrownBy(application::submit)
        .isInstanceOf(InvalidEnrollmentApplicationStateException.class);
  }

  @Test
  void cancel_transitionsDraftToCancelled() {
    final var application = draft();

    application.cancel();

    assertThat(application.getStatus()).isEqualTo(EnrollmentApplicationStatus.CANCELLED);
  }

  @Test
  void cancel_rejectsNonDraftApplication() {
    final var application = submitted();

    assertThatThrownBy(application::cancel).isInstanceOf(ApplicationNotEditableException.class);
  }

  @Test
  void approve_transitionsSubmittedToApproved() {
    final var application = submitted();

    application.approve(RESOLVED_AT, RESOLVER_PERSON_ID);

    assertThat(application.isApproved()).isTrue();
    assertThat(application.getStatus()).isEqualTo(EnrollmentApplicationStatus.APPROVED);
    assertThat(application.getResolvedAt()).isEqualTo(RESOLVED_AT);
    assertThat(application.getResolvedByPersonId()).isEqualTo(RESOLVER_PERSON_ID);
  }

  @Test
  void approve_rejectsDraftApplication() {
    final var application = draft();

    assertThatThrownBy(() -> application.approve(RESOLVED_AT, RESOLVER_PERSON_ID))
        .isInstanceOf(InvalidEnrollmentApplicationStateException.class);
  }

  @Test
  void approve_rejectsAlreadyResolvedApplication() {
    final var application = submitted();
    application.approve(RESOLVED_AT, RESOLVER_PERSON_ID);

    assertThatThrownBy(() -> application.approve(RESOLVED_AT.plusSeconds(3600), RESOLVER_PERSON_ID))
        .isInstanceOf(InvalidEnrollmentApplicationStateException.class);
  }

  @Test
  void reject_transitionsSubmittedToRejectedStoringReason() {
    final var application = submitted();

    application.reject("Documentación incompleta", RESOLVED_AT, RESOLVER_PERSON_ID);

    assertThat(application.getStatus()).isEqualTo(EnrollmentApplicationStatus.REJECTED);
    assertThat(application.getRejectionReason()).isEqualTo("Documentación incompleta");
    assertThat(application.getResolvedAt()).isEqualTo(RESOLVED_AT);
    assertThat(application.getResolvedByPersonId()).isEqualTo(RESOLVER_PERSON_ID);
  }

  @Test
  void reject_requiresNonBlankReason() {
    final var application = submitted();

    assertThatThrownBy(() -> application.reject("   ", RESOLVED_AT, RESOLVER_PERSON_ID))
        .isInstanceOf(MissingRejectionReasonException.class);
    assertThatThrownBy(() -> application.reject(null, RESOLVED_AT, RESOLVER_PERSON_ID))
        .isInstanceOf(MissingRejectionReasonException.class);
    assertThat(application.getStatus()).isEqualTo(EnrollmentApplicationStatus.SUBMITTED);
  }

  @Test
  void reject_rejectsAlreadyResolvedApplication() {
    final var application = submitted();
    application.reject("Documentación incompleta", RESOLVED_AT, RESOLVER_PERSON_ID);

    assertThatThrownBy(
            () ->
                application.reject(
                    "Otro motivo", RESOLVED_AT.plusSeconds(3600), RESOLVER_PERSON_ID))
        .isInstanceOf(InvalidEnrollmentApplicationStateException.class);
  }

  @Test
  void updateEducationBackground_createsBackgroundOnFirstUpdate() {
    final var application = draft();

    application.updateEducationBackground("Escuela N° 1");

    assertThat(application.getEducationBackground()).isNotNull();
    assertThat(application.getEducationBackground().getSecondarySchool()).isEqualTo("Escuela N° 1");
  }

  @Test
  void updateEducationBackground_rejectsNonEditableApplication() {
    final var application = submitted();

    assertThatThrownBy(() -> application.updateEducationBackground("Escuela N° 1"))
        .isInstanceOf(InvalidEnrollmentApplicationStateException.class);
  }

  @Test
  void resolvedApplicationIsNotEditable() {
    final var application = submitted();
    application.approve(RESOLVED_AT, RESOLVER_PERSON_ID);

    assertThat(application.isEditable()).isFalse();
    assertThat(application.isPendingEvaluation()).isFalse();
    assertThat(application.isResolved()).isTrue();
  }

  private static EnrollmentApplication draft() {
    return EnrollmentApplication.builder().status(EnrollmentApplicationStatus.DRAFT).build();
  }

  private static EnrollmentApplication submitted() {
    final var application = draft();
    application.submit();
    return application;
  }
}
