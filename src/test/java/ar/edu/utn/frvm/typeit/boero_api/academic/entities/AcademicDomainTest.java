package ar.edu.utn.frvm.typeit.boero_api.academic.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.ApprovalMode;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.RequirementType;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.StudyPlanStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicValidationException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.InvalidAcademicStateException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AcademicDomainTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

  @Test
  @DisplayName("Should allow an academic year to progress to closed")
  void academicYearProgressesToClosed() {
    final var year = currentAcademicYear();

    year.transitionTo(AcademicYearStatus.ACTIVE);
    year.transitionTo(AcademicYearStatus.CLOSED);

    assertThat(year.getStatus()).isEqualTo(AcademicYearStatus.CLOSED);
  }

  @Test
  @DisplayName("Should reject reopening a closed academic year")
  void academicYearRejectsReopening() {
    final var year = currentAcademicYear();
    year.transitionTo(AcademicYearStatus.ACTIVE);
    year.transitionTo(AcademicYearStatus.CLOSED);

    assertThatThrownBy(() -> year.transitionTo(AcademicYearStatus.ACTIVE))
        .isInstanceOf(InvalidAcademicStateException.class);
  }

  @Test
  @DisplayName("Should freeze a study plan after activation")
  void studyPlanFreezesAfterActivation() {
    final var institution = institution();
    final var path = TrainingPath.create(institution, "Profesorado", null);
    final var plan =
        StudyPlan.create(
            institution, path, "Plan 2026", LocalDate.of(2026, 3, 1), LocalDate.of(2030, 12, 31));
    plan.activate();

    assertThatThrownBy(
            () -> plan.updateDraft("Plan nuevo", plan.getEffectiveFrom(), plan.getEffectiveTo()))
        .isInstanceOf(InvalidAcademicStateException.class);
    assertThat(plan.getStatus()).isEqualTo(StudyPlanStatus.ACTIVE);
  }

  @Test
  @DisplayName("Should reject course activation when its study plan is inactive")
  void courseRejectsActivationWhenStudyPlanIsInactive() {
    final var institution = institution();
    final var path = TrainingPath.create(institution, "Profesorado", null);
    final var plan =
        StudyPlan.create(
            institution, path, "Plan 2026", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 31));
    plan.activate();
    plan.deactivate(LocalDate.of(2026, 12, 31));
    final var academicYear =
        AcademicYear.create(
            institution,
            currentYear(),
            LocalDate.of(currentYear(), 3, 1),
            LocalDate.of(currentYear(), 12, 15),
            TODAY);
    academicYear.transitionTo(AcademicYearStatus.ACTIVE);
    final var academicSpace =
        AcademicSpace.create(
            institution,
            "Programación",
            null,
            AcademicSpaceType.SUBJECT,
            AcademicSpaceFormat.INDIVIDUAL);
    final var course =
        Course.create(
            institution,
            StudyPlanSpace.create(
                institution,
                plan,
                academicSpace,
                null,
                RequirementType.REQUIRED,
                1,
                ApprovalMode.FINAL_EXAM),
            academicYear);
    course.deactivate();

    assertThatThrownBy(() -> course.updateStatus(CourseStatus.ACTIVE))
        .isInstanceOf(InvalidAcademicStateException.class);
  }

  @Test
  @DisplayName("Should reject an incomplete academic year date pair")
  void academicYearRejectsIncompleteDatePair() {
    final int currentYear = currentYear();
    assertThatThrownBy(
            () ->
                AcademicYear.create(
                    institution(), currentYear, LocalDate.of(currentYear, 3, 1), null, TODAY))
        .isInstanceOf(AcademicValidationException.class)
        .satisfies(
            exception ->
                assertThat(((AcademicValidationException) exception).fieldErrors())
                    .containsKey("endDate"));
  }

  @Test
  @DisplayName("Should allow an academic year to finish during the following calendar year")
  void academicYearAllowsFollowingYearEndDate() {
    final int currentYear = currentYear();

    final var academicYear =
        AcademicYear.create(
            institution(),
            currentYear,
            LocalDate.of(currentYear, 8, 1),
            LocalDate.of(currentYear + 1, 7, 31),
            TODAY);

    assertThat(academicYear.getEndDate()).isEqualTo(LocalDate.of(currentYear + 1, 7, 31));
  }

  @Test
  @DisplayName("Should reject a start date outside the academic year")
  void academicYearRejectsStartDateFromAnotherYear() {
    final int currentYear = currentYear();

    assertThatThrownBy(
            () ->
                AcademicYear.create(
                    institution(),
                    currentYear,
                    LocalDate.of(currentYear - 1, 12, 1),
                    LocalDate.of(currentYear, 12, 15),
                    TODAY))
        .isInstanceOf(AcademicValidationException.class)
        .satisfies(
            exception ->
                assertThat(((AcademicValidationException) exception).fieldErrors())
                    .containsKey("startDate"));
  }

  @Test
  @DisplayName("Should reject an end date more than one calendar year later")
  void academicYearRejectsEndDateAfterFollowingYear() {
    final int currentYear = currentYear();

    assertThatThrownBy(
            () ->
                AcademicYear.create(
                    institution(),
                    currentYear,
                    LocalDate.of(currentYear, 3, 1),
                    LocalDate.of(currentYear + 2, 3, 1),
                    TODAY))
        .isInstanceOf(AcademicValidationException.class)
        .satisfies(
            exception ->
                assertThat(((AcademicValidationException) exception).fieldErrors())
                    .containsKey("endDate"));
  }

  @Test
  @DisplayName("Should reject an academic year outside the supported range")
  void academicYearRejectsUnsupportedYear() {
    assertThatThrownBy(() -> AcademicYear.create(institution(), 1999, null, null, TODAY))
        .isInstanceOf(AcademicValidationException.class)
        .satisfies(
            exception ->
                assertThat(((AcademicValidationException) exception).fieldErrors())
                    .containsKey("year"));
  }

  @Test
  @DisplayName("Should allow an open-ended study plan validity")
  void studyPlanAllowsOpenEndedValidity() {
    final var institution = institution();
    final var path = TrainingPath.create(institution, "Profesorado", null);

    final var plan =
        StudyPlan.create(institution, path, "Plan 2027", LocalDate.of(2027, 3, 1), null);

    assertThat(plan.getEffectiveFrom()).isEqualTo(LocalDate.of(2027, 3, 1));
    assertThat(plan.getEffectiveTo()).isNull();
  }

  @Test
  @DisplayName("Should reject a study plan end date without a start date")
  void studyPlanRejectsEndDateWithoutStartDate() {
    final var institution = institution();
    final var path = TrainingPath.create(institution, "Profesorado", null);

    assertThatThrownBy(
            () ->
                StudyPlan.create(institution, path, "Plan 2027", null, LocalDate.of(2027, 12, 15)))
        .isInstanceOf(AcademicValidationException.class)
        .satisfies(
            exception ->
                assertThat(((AcademicValidationException) exception).fieldErrors())
                    .containsKey("effectiveFrom"));
  }

  private static Institution institution() {
    return Institution.builder().id(UUID.randomUUID()).build();
  }

  private static AcademicYear currentAcademicYear() {
    final int currentYear = currentYear();
    return AcademicYear.create(
        institution(),
        currentYear,
        LocalDate.of(currentYear, 3, 1),
        LocalDate.of(currentYear, 12, 15),
        TODAY);
  }

  private static int currentYear() {
    return TODAY.getYear();
  }
}
