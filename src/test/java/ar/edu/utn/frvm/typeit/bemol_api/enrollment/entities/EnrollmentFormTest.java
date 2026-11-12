package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormVersionStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormMessages;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrollmentFormTest {
  private static final Instant PUBLISHED_AT = Instant.parse("2026-11-06T12:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
  private final Institution institution = Institution.builder().id(new UUID(0, 100)).build();
  private final Institution anotherInstitution = Institution.builder().id(new UUID(0, 101)).build();

  @Test
  void formNamesAreTrimmedAndBounded() {
    final var form = EnrollmentForm.create(institution, "  Talleres  ");

    assertThat(form.getName()).isEqualTo("Talleres");
    assertThatThrownBy(() -> form.rename(" \n "))
        .isInstanceOf(EnrollmentFormException.class)
        .hasMessage(EnrollmentFormMessages.NAME_INVALID);
    assertThatThrownBy(() -> form.rename("x".repeat(151)))
        .isInstanceOf(EnrollmentFormException.class);
    assertThat(form.getName()).isEqualTo("Talleres");
  }

  @Test
  void namesRejectInvalidDatabaseTextWithoutChangingTheModel() {
    final var form = EnrollmentForm.create(institution, "Música 🎻");
    for (final var invalid : List.of("Modelo\u0000QA", "Modelo\uD800", "Modelo\uDC00")) {
      assertThatThrownBy(() -> form.rename(invalid))
          .isInstanceOf(EnrollmentFormException.class)
          .hasMessage(EnrollmentFormMessages.NAME_INVALID);
    }
    assertThat(form.getName()).isEqualTo("Música 🎻");
  }

  @Test
  void versionNumbersMustBePositive() {
    assertThatThrownBy(
            () ->
                EnrollmentFormVersion.draft(
                    EnrollmentForm.create(institution, "Talleres"),
                    0,
                    EnrollmentFormDefinitionTest.baseDefinition()))
        .isInstanceOf(EnrollmentFormException.class)
        .hasMessage(EnrollmentFormMessages.VERSION_NUMBER_INVALID);
  }

  @Test
  void publishedDefinitionCannotBeEditedOrRepublished() {
    final var version = draft(institution);
    final var definition = version.getDefinition();

    version.publish(PUBLISHED_AT, 0);

    assertThat(version.getStatus()).isEqualTo(EnrollmentFormVersionStatus.PUBLISHED);
    assertThat(version.getPublishedAt()).isEqualTo(PUBLISHED_AT);
    assertThatThrownBy(
            () -> version.replaceDefinition(new EnrollmentFormDefinition(List.of(), List.of()), 0))
        .isInstanceOf(EnrollmentFormException.class)
        .hasMessage(EnrollmentFormMessages.VERSION_NOT_EDITABLE);
    assertThatThrownBy(() -> version.publish(PUBLISHED_AT, 0))
        .isInstanceOf(EnrollmentFormException.class);
    assertThat(version.getDefinition()).isSameAs(definition);
  }

  @Test
  void publicationRejectsIncompleteDefinitionWithoutChangingState() {
    final var version = draft(institution);
    version.replaceDefinition(new EnrollmentFormDefinition(List.of(), List.of()), 0);

    assertThatThrownBy(() -> version.publish(PUBLISHED_AT, 0))
        .isInstanceOf(EnrollmentFormException.class)
        .hasMessage(EnrollmentFormMessages.ESSENTIAL_BLOCKS_REQUIRED);
    assertThat(version.getStatus()).isEqualTo(EnrollmentFormVersionStatus.DRAFT);
    assertThat(version.getPublishedAt()).isNull();
  }

  @Test
  void staleRevisionCannotEditPublishOrArchive() {
    final var version = draft(institution);

    assertThatThrownBy(() -> version.replaceDefinition(version.getDefinition(), 1))
        .isInstanceOf(EnrollmentFormException.class)
        .hasMessage(EnrollmentFormMessages.REVISION_CONFLICT);
    assertThatThrownBy(() -> version.publish(PUBLISHED_AT, 1))
        .isInstanceOf(EnrollmentFormException.class);
    version.publish(PUBLISHED_AT, 0);
    assertThatThrownBy(() -> version.archive(PUBLISHED_AT.plusSeconds(1), 1))
        .isInstanceOf(EnrollmentFormException.class);
    assertThat(version.getStatus()).isEqualTo(EnrollmentFormVersionStatus.PUBLISHED);
  }

  @Test
  void archivePreservesDefinitionAndPreventsFurtherAssignments() {
    final var version = draft(institution);
    version.publish(PUBLISHED_AT, 0);
    final var definition = version.getDefinition();

    assertThatThrownBy(() -> version.archive(PUBLISHED_AT.minusSeconds(1), 0))
        .isInstanceOf(EnrollmentFormException.class)
        .hasMessage(EnrollmentFormMessages.TRANSITION_DATE_INVALID);
    version.archive(PUBLISHED_AT.plusSeconds(1), 0);

    assertThat(version.getStatus()).isEqualTo(EnrollmentFormVersionStatus.ARCHIVED);
    assertThat(version.getDefinition()).isSameAs(definition);
    assertThatThrownBy(() -> version.requireAssignableTo(institution.getId()))
        .isInstanceOf(EnrollmentFormException.class)
        .hasMessage(EnrollmentFormMessages.VERSION_NOT_PUBLISHED);
    assertThatThrownBy(() -> version.replaceDefinition(definition, 0))
        .isInstanceOf(EnrollmentFormException.class);
  }

  @Test
  void newVersionDoesNotMutateThePublishedOne() {
    final var published = draft(institution);
    published.publish(PUBLISHED_AT, 0);
    final var next = EnrollmentFormVersion.draft(published.getForm(), 2, published.getDefinition());

    next.replaceDefinition(new EnrollmentFormDefinition(List.of(), List.of()), 0);

    assertThat(published.getDefinition().sections()).hasSize(4);
    assertThat(published.getStatus()).isEqualTo(EnrollmentFormVersionStatus.PUBLISHED);
    assertThat(next.getDefinition().sections()).isEmpty();
  }

  @Test
  void assignmentRequiresPublishedVersionAndMatchingInstitution() {
    final var version = draft(institution);
    final var path = TrainingPath.create(institution, "Taller", null);
    final var year = AcademicYear.create(institution, 2026, null, null, TODAY);

    assertThatThrownBy(() -> EnrollmentFormAssignment.create(institution, path, year, version))
        .isInstanceOf(EnrollmentFormException.class)
        .hasMessage(EnrollmentFormMessages.VERSION_NOT_PUBLISHED);
    version.publish(PUBLISHED_AT, 0);
    final var assignment = EnrollmentFormAssignment.create(institution, path, year, version);
    assertThat(assignment.getFormVersion()).isSameAs(version);

    final var foreignPath = TrainingPath.create(anotherInstitution, "Otro", null);
    assertThatThrownBy(
            () -> EnrollmentFormAssignment.create(institution, foreignPath, year, version))
        .isInstanceOf(EnrollmentFormException.class)
        .hasMessage(EnrollmentFormMessages.INSTITUTION_MISMATCH);
    final var foreignYear = AcademicYear.create(anotherInstitution, 2026, null, null, TODAY);
    assertThatThrownBy(
            () -> EnrollmentFormAssignment.create(institution, path, foreignYear, version))
        .isInstanceOf(EnrollmentFormException.class);
    assertThatThrownBy(() -> version.requireAssignableTo(anotherInstitution.getId()))
        .isInstanceOf(EnrollmentFormException.class);
  }

  @Test
  void reassignmentRejectsStaleRevisionAndForeignVersionWithoutChangingDestination() {
    final var current = draft(institution);
    current.publish(PUBLISHED_AT, 0);
    final var assignment =
        EnrollmentFormAssignment.create(
            institution,
            TrainingPath.create(institution, "Taller", null),
            AcademicYear.create(institution, 2026, null, null, TODAY),
            current);
    final var next = EnrollmentFormVersion.draft(current.getForm(), 2, current.getDefinition());
    next.publish(PUBLISHED_AT, 0);

    assertThatThrownBy(() -> assignment.assignVersion(next, 1))
        .isInstanceOf(EnrollmentFormException.class)
        .hasMessage(EnrollmentFormMessages.REVISION_CONFLICT);
    final var foreign = draft(anotherInstitution);
    foreign.publish(PUBLISHED_AT, 0);
    assertThatThrownBy(() -> assignment.assignVersion(foreign, 0))
        .isInstanceOf(EnrollmentFormException.class)
        .hasMessage(EnrollmentFormMessages.INSTITUTION_MISMATCH);
    assertThat(assignment.getFormVersion()).isSameAs(current);

    assignment.assignVersion(next, 0);
    assertThat(assignment.getFormVersion()).isSameAs(next);
  }

  private EnrollmentFormVersion draft(final Institution owner) {
    return EnrollmentFormVersion.draft(
        EnrollmentForm.create(owner, "Talleres"), 1, EnrollmentFormDefinitionTest.baseDefinition());
  }
}
