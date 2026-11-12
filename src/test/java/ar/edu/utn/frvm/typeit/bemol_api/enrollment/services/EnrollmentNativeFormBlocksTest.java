package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.common.time.BusinessDateProvider;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.ApplicantEducationBackground;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.ApplicantHealthInclusion;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.ApplicantPreference;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.ApplicantResponsible;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplicationCourse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Section;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormVersion;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormNativeBlock;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.AcademicBackgroundDto;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentApplicationResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentDraftData;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.HealthInclusionDto;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.PreferenceDto;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.ResponsibleDto;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Country;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrollmentNativeFormBlocksTest {
  private static final UUID INSTITUTION_ID =
      UUID.fromString("8c68b48b-ea9c-4f64-941f-7dc4ed621266");
  private static final UUID PERSON_ID = UUID.fromString("0bcd7b5c-314a-424a-99e8-0928d53c164f");
  private final EnrollmentLegacySpaceSelectionService spaces =
      mock(EnrollmentLegacySpaceSelectionService.class);
  private final EnrollmentCourseSelectionService courses =
      mock(EnrollmentCourseSelectionService.class);
  private final EnrollmentDraftUpdater updater = new EnrollmentDraftUpdater(spaces, courses);
  private final EnrollmentSubmissionValidator validator =
      new EnrollmentSubmissionValidator(
          new BusinessDateProvider(
              Clock.fixed(Instant.parse("2026-11-06T15:00:00Z"), ZoneOffset.UTC)));

  @Test
  void rejectsAllDisabledPayloadsBeforeMutatingOtherSections() {
    final var application = configured();
    application.setResponsible(ApplicantResponsible.builder().fullName("Original").build());
    final var input =
        EnrollmentDraftData.builder()
            .academicBackground(new AcademicBackgroundDto())
            .healthInclusion(new HealthInclusionDto())
            .preference(new PreferenceDto())
            .responsible(ResponsibleDto.builder().fullName("Changed").build())
            .build();

    assertThatThrownBy(() -> updater.update(application, input))
        .isInstanceOfSatisfying(
            EnrollmentValidationException.class,
            error ->
                assertThat(error.fieldErrors())
                    .containsOnlyKeys("academicBackground", "healthInclusion", "preference"));
    assertThat(application.getResponsible().getFullName()).isEqualTo("Original");
    assertThat(application.getEducationBackground()).isNull();
    assertThat(application.getHealthInclusion()).isNull();
    assertThat(application.getPreference()).isNull();
    verifyNoInteractions(spaces, courses);
  }

  @Test
  void laterDisabledBlockDoesNotPartiallyReplaceEnabledSchooling() {
    final var application = configured(EnrollmentFormNativeBlock.SCHOOLING);
    application.setEducationBackground(
        ApplicantEducationBackground.builder().secondarySchool("Original").build());
    final var input =
        EnrollmentDraftData.builder()
            .academicBackground(AcademicBackgroundDto.builder().secondarySchool("Changed").build())
            .healthInclusion(new HealthInclusionDto())
            .build();

    assertThatThrownBy(() -> updater.update(application, input))
        .isInstanceOf(EnrollmentValidationException.class);
    assertThat(requireNonNull(application.getEducationBackground()).getSecondarySchool())
        .isEqualTo("Original");
    verifyNoInteractions(spaces, courses);
  }

  @Test
  void standaloneShiftUpdatesOnlyItsValueAndPreservesHistoricalPreferences() {
    final var application = configured(EnrollmentFormNativeBlock.PREFERRED_SHIFT);
    final var previous =
        ApplicantPreference.builder()
            .preferredShift("Mañana")
            .allowsImageUse(true)
            .isReenrolling(true)
            .previousTeacher("Anterior")
            .build();
    application.setPreference(previous);

    updater.update(
        application,
        EnrollmentDraftData.builder()
            .preference(PreferenceDto.builder().preferredShift("Tarde").build())
            .build());

    assertThat(application.getPreference()).isSameAs(previous);
    assertThat(previous.getPreferredShift()).isEqualTo("Tarde");
    assertThat(previous.isAllowsImageUse()).isTrue();
    assertThat(previous.isReenrolling()).isTrue();
    assertThat(previous.getPreviousTeacher()).isEqualTo("Anterior");
  }

  @Test
  void standaloneShiftRejectsEvenNegativeAnswersToUnavailablePreferenceQuestions() {
    final var application = configured(EnrollmentFormNativeBlock.PREFERRED_SHIFT);

    assertThatThrownBy(
            () ->
                updater.update(
                    application,
                    EnrollmentDraftData.builder()
                        .preference(
                            PreferenceDto.builder()
                                .preferredShift("Tarde")
                                .allowsImageUse(false)
                                .build())
                        .build()))
        .isInstanceOf(EnrollmentValidationException.class);

    assertThat(application.getPreference()).isNull();
    verifyNoInteractions(spaces, courses);
  }

  @Test
  void disabledSchoolingAndPreferencesDoNotBlockSubmission() {
    final var application = validApplicant(configured());

    assertThatCode(() -> validator.validate(application, PERSON_ID)).doesNotThrowAnyException();
  }

  @Test
  void legacyBaseStillRequiresSchoolingAndPreferredShift() {
    final var application =
        validApplicant(
            EnrollmentApplication.builder().status(EnrollmentApplicationStatus.DRAFT).build());

    assertThatThrownBy(() -> validator.validate(application, PERSON_ID))
        .isInstanceOfSatisfying(
            EnrollmentValidationException.class,
            error ->
                assertThat(error.fieldErrors())
                    .containsKeys(
                        "academicBackground.currentlyStudying", "preference.preferredShift"));
  }

  @Test
  void configuredFormsDoNotRemoveEssentialApplicantAndCourseValidation() {
    final var application = configured();

    assertThatThrownBy(() -> validator.validate(application, PERSON_ID))
        .isInstanceOfSatisfying(
            EnrollmentValidationException.class,
            error -> assertThat(error.fieldErrors()).containsOnlyKeys("applicant", "courses"));
  }

  @Test
  void standaloneShiftIgnoresHistoricalReenrollmentButStillRequiresItsOwnAnswer() {
    final var application = validApplicant(configured(EnrollmentFormNativeBlock.PREFERRED_SHIFT));
    final var preference =
        ApplicantPreference.builder().preferredShift("Tarde").isReenrolling(true).build();
    application.setPreference(preference);

    assertThatCode(() -> validator.validate(application, PERSON_ID)).doesNotThrowAnyException();
    preference.setPreferredShift(" ");
    assertThatThrownBy(() -> validator.validate(application, PERSON_ID))
        .isInstanceOfSatisfying(
            EnrollmentValidationException.class,
            error -> assertThat(error.fieldErrors()).containsOnlyKeys("preference.preferredShift"));
  }

  @Test
  void enabledPreferencesKeepTheirPreviousTeacherRule() {
    final var application = validApplicant(configured(EnrollmentFormNativeBlock.PREFERENCES));
    application.setPreference(
        ApplicantPreference.builder().preferredShift("Tarde").isReenrolling(true).build());

    assertThatThrownBy(() -> validator.validate(application, PERSON_ID))
        .isInstanceOfSatisfying(
            EnrollmentValidationException.class,
            error ->
                assertThat(error.fieldErrors()).containsOnlyKeys("preference.previousTeacher"));
  }

  @Test
  void detailOmitsDisabledNativeSectionsWithoutDeletingTheirHistoricalValues() {
    final var application = configured();
    final var education =
        ApplicantEducationBackground.builder().secondarySchool("Escuela anterior").build();
    final var health =
        ApplicantHealthInclusion.builder().adjustmentDetails("Dato anterior").build();
    final var preference = ApplicantPreference.builder().preferredShift("Tarde").build();
    application.setEducationBackground(education);
    application.setHealthInclusion(health);
    application.setPreference(preference);

    final var data =
        requireNonNull(EnrollmentApplicationResponse.from(application, false, false).data());

    assertThat(data.academicBackground()).isNull();
    assertThat(data.healthInclusion()).isNull();
    assertThat(data.preference()).isNull();
    assertThat(application.getEducationBackground()).isSameAs(education);
    assertThat(application.getHealthInclusion()).isSameAs(health);
    assertThat(application.getPreference()).isSameAs(preference);
  }

  @Test
  void standaloneShiftDetailDoesNotInventNegativeAnswersForOtherQuestions() {
    final var application = configured(EnrollmentFormNativeBlock.PREFERRED_SHIFT);
    application.setPreference(ApplicantPreference.builder().preferredShift("Tarde").build());

    final var data =
        requireNonNull(EnrollmentApplicationResponse.from(application, false, false).data());
    final var preference = requireNonNull(data.preference());

    assertThat(preference.preferredShift()).isEqualTo("Tarde");
    assertThat(preference.allowsImageUse()).isNull();
    assertThat(preference.isReenrolling()).isNull();
    assertThat(preference.previousTeacher()).isNull();
  }

  @Test
  void legacyBaseKeepsEmptyNativeResponseObjects() {
    final var data =
        requireNonNull(
            EnrollmentApplicationResponse.from(
                    EnrollmentApplication.builder().build(), false, false)
                .data());

    assertThat(data.academicBackground()).isNotNull();
    assertThat(data.healthInclusion()).isNotNull();
    assertThat(data.preference()).isNotNull();
  }

  private EnrollmentApplication configured(final EnrollmentFormNativeBlock... optional) {
    final var blocks =
        new ArrayList<>(
            List.of(
                EnrollmentFormNativeBlock.PERSONAL_DATA,
                EnrollmentFormNativeBlock.RESPONSIBLE,
                EnrollmentFormNativeBlock.ACADEMIC_SELECTION,
                EnrollmentFormNativeBlock.DOCUMENTATION));
    blocks.addAll(List.of(optional));
    final var sections =
        blocks.stream()
            .map(
                block ->
                    new Section(
                        UUID.nameUUIDFromBytes(block.name().getBytes(StandardCharsets.UTF_8)),
                        block.name(),
                        block,
                        List.of()))
            .toList();
    final var version = mock(EnrollmentFormVersion.class);
    when(version.getDefinition()).thenReturn(new EnrollmentFormDefinition(sections, List.of()));
    final var application =
        EnrollmentApplication.builder()
            .institution(Institution.builder().id(INSTITUTION_ID).build())
            .status(EnrollmentApplicationStatus.DRAFT)
            .build();
    application.bindFormVersion(version);
    return application;
  }

  private EnrollmentApplication validApplicant(final EnrollmentApplication application) {
    final var person =
        Person.builder()
            .id(PERSON_ID)
            .firstName("Ana")
            .lastName("Pérez")
            .documentNumber("50111111")
            .phoneNumber("03534100000")
            .email("ana@example.com")
            .birthDate(LocalDate.of(2000, 1, 1))
            .nationalityCountry(Country.builder().isoCode("ARG").name("Argentina").build())
            .build();
    final var result =
        EnrollmentApplication.builder()
            .institution(application.getInstitution())
            .applicantPerson(person)
            .status(EnrollmentApplicationStatus.DRAFT)
            .formState(application.getFormState())
            .build();
    result.addCourseSelection(EnrollmentApplicationCourse.builder().build());
    return result;
  }
}
