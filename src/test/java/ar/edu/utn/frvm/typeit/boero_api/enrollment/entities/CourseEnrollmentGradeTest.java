package ar.edu.utn.frvm.typeit.boero_api.enrollment.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentGradePublicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.GradeMessages;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CourseEnrollmentGradeTest {

  @Test
  @DisplayName("New grade starts as draft with trimmed evaluation")
  void create_startsAsDraft() {
    final var grade =
        CourseEnrollmentGrade.create(
            org.mockito.Mockito.mock(
                ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution.class),
            org.mockito.Mockito.mock(
                ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment.class),
            "  Parcial 1  ",
            new BigDecimal("7.50"),
            null);

    assertThat(grade.getEvaluation()).isEqualTo("Parcial 1");
    assertThat(grade.getValue()).isEqualByComparingTo("7.5");
    assertThat(grade.isDraft()).isTrue();
    assertThat(grade.publicationStatus()).isEqualTo(CourseEnrollmentGradePublicationStatus.DRAFT);
    assertThat(grade.hasPendingChanges()).isTrue();
  }

  @Test
  @DisplayName("Publish copies working copy and clears pending state")
  void publish_copiesWorkingCopy() {
    final var grade =
        CourseEnrollmentGrade.create(
            org.mockito.Mockito.mock(
                ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution.class),
            org.mockito.Mockito.mock(
                ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment.class),
            "Parcial 1",
            new BigDecimal("7"),
            null);

    grade.publish(java.util.UUID.randomUUID(), java.time.Instant.now());

    assertThat(grade.publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.PUBLISHED);
    assertThat(grade.hasPendingChanges()).isFalse();
  }

  @Test
  @DisplayName("Modifying published grade keeps published snapshot")
  void updatePublished_keepsSnapshot() {
    final var grade =
        CourseEnrollmentGrade.create(
            org.mockito.Mockito.mock(
                ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution.class),
            org.mockito.Mockito.mock(
                ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment.class),
            "Parcial 1",
            new BigDecimal("6"),
            null);
    grade.publish(java.util.UUID.randomUUID(), java.time.Instant.now());

    grade.updateWorkingCopy("Parcial 1", new BigDecimal("8"), null);

    assertThat(grade.getValue()).isEqualByComparingTo("8");
    assertThat(grade.getPublishedValue()).isEqualByComparingTo("6");
    assertThat(grade.publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.PENDING_CHANGES);
  }

  @Test
  @DisplayName("Pending deletion blocks edition")
  void markForDeletion_blocksEdition() {
    final var grade =
        CourseEnrollmentGrade.create(
            org.mockito.Mockito.mock(
                ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution.class),
            org.mockito.Mockito.mock(
                ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment.class),
            "Parcial 1",
            new BigDecimal("6"),
            null);
    grade.publish(java.util.UUID.randomUUID(), java.time.Instant.now());
    grade.markForDeletion(null);

    assertThat(grade.publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.PENDING_DELETION);

    assertThatThrownBy(() -> grade.updateWorkingCopy("Parcial 1", new BigDecimal("8"), null))
        .isInstanceOf(EnrollmentValidationException.class);
  }

  @Test
  @DisplayName("Pending deletion rejects explicit editability requirement")
  void requireEditable_rejectsPendingDeletion() {
    final var grade =
        CourseEnrollmentGrade.create(
            org.mockito.Mockito.mock(
                ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution.class),
            org.mockito.Mockito.mock(
                ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment.class),
            "Parcial 1",
            new BigDecimal("6"),
            null);
    grade.publish(java.util.UUID.randomUUID(), java.time.Instant.now());
    grade.markForDeletion(null);

    assertThatThrownBy(grade::requireEditable)
        .isInstanceOf(EnrollmentValidationException.class)
        .hasMessage(GradeMessages.GRADE_PENDING_DELETION_IMMUTABLE);
  }

  @Test
  @DisplayName("Exit restore returns to published snapshot, clears deletion and stamps actor")
  void revertToPublishedOnExit_restoresPublishedSnapshotAndClearsFlags() {
    final var grade =
        CourseEnrollmentGrade.create(
            org.mockito.Mockito.mock(
                ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution.class),
            org.mockito.Mockito.mock(
                ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment.class),
            "Parcial 1",
            new BigDecimal("6"),
            null);
    final var publisher = java.util.UUID.randomUUID();
    final var publishedAt = java.time.Instant.now().minusSeconds(60);
    final var exitActor = java.util.UUID.randomUUID();
    grade.publish(publisher, publishedAt);
    grade.updateWorkingCopy("Parcial 2", new BigDecimal("9"), null);
    grade.markForDeletion(null);

    grade.revertToPublishedOnExit(exitActor);

    assertThat(grade.getEvaluation()).isEqualTo("Parcial 1");
    assertThat(grade.getValue()).isEqualByComparingTo("6");
    assertThat(grade.getPublishedEvaluation()).isEqualTo("Parcial 1");
    assertThat(grade.getPublishedValue()).isEqualByComparingTo("6");
    assertThat(grade.getPublishedAt()).isEqualTo(publishedAt);
    assertThat(grade.getPublishedByPersonId()).isEqualTo(publisher);
    assertThat(grade.getUpdatedByPersonId()).isEqualTo(exitActor);
    assertThat(grade.publicationStatus())
        .isEqualTo(CourseEnrollmentGradePublicationStatus.PUBLISHED);
    assertThat(grade.hasPendingChanges()).isFalse();
  }

  @Test
  @DisplayName("Exit restore rejects grades without a published snapshot")
  void revertToPublishedOnExit_rejectsGradeWithoutPublishedSnapshot() {
    final var grade =
        CourseEnrollmentGrade.create(
            org.mockito.Mockito.mock(
                ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution.class),
            org.mockito.Mockito.mock(
                ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment.class),
            "Parcial 1",
            new BigDecimal("7"),
            null);

    assertThatThrownBy(() -> grade.revertToPublishedOnExit(null))
        .isInstanceOf(EnrollmentValidationException.class)
        .hasMessage(GradeMessages.GRADE_WITHOUT_PUBLISHED_VERSION);
  }

  @Test
  @DisplayName("Rejects out of range and too many decimals")
  void normalizeValue_rejectsInvalid() {
    assertThatThrownBy(() -> CourseEnrollmentGrade.normalizeValue(new BigDecimal("0.99")))
        .isInstanceOf(EnrollmentValidationException.class);
    assertThatThrownBy(() -> CourseEnrollmentGrade.normalizeValue(new BigDecimal("10.01")))
        .isInstanceOf(EnrollmentValidationException.class);
    assertThatThrownBy(() -> CourseEnrollmentGrade.normalizeValue(new BigDecimal("7.555")))
        .isInstanceOf(EnrollmentValidationException.class);
    assertThatThrownBy(() -> CourseEnrollmentGrade.normalizeEvaluation("   "))
        .isInstanceOf(EnrollmentValidationException.class);
  }
}
