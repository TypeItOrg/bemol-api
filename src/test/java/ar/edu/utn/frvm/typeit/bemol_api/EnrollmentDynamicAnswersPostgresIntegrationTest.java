package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.DocumentDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPathDocumentRequirement;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Section;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.DocumentRequirementLevel;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EducationLevel;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormQuestionType;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.AcademicBackgroundDto;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseSelectionDto;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentDraftData;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormAssignmentUpdateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormDefinitionUpdateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormRevisionRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormVersionCreateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.PreferenceDto;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.StartEnrollmentApplicationRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.UpdateEnrollmentDraftRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;

class EnrollmentDynamicAnswersPostgresIntegrationTest extends EnrollmentDynamicFormTestSupport {
  @Test
  void roundTripsAllTypesWithoutLeakingAnswersToAuditOrSummary() {
    final var fixture = fixture();
    final var saved = save(fixture, 0, completeAnswers(), true);

    assertThat(saved.draftRevision()).isEqualTo(1);
    final var loaded = read(fixture);
    assertThat(loaded.formVersionId()).isEqualTo(fixture.version());
    assertThat(loaded.formDefinition()).isEqualTo(saved.formDefinition());
    assertThat(answers(loaded)).satisfies(this::assertCompleteAnswers);
    assertThat(requireNonNull(loaded.data()).academicBackground()).isNull();
    assertThat(requireNonNull(loaded.data()).healthInclusion()).isNull();
    assertThat(requireNonNull(loaded.data()).preference()).isNull();

    final var unchanged = save(fixture, loaded.draftRevision(), completeAnswers(), true);
    assertThat(unchanged.draftRevision()).isEqualTo(loaded.draftRevision());
    final var summary =
        applications
            .listApplications(fixture.institution(), null, null, null, Pageable.ofSize(20))
            .items()
            .getFirst();
    assertThat(summary.formDefinition()).isNull();
    assertThat(requireNonNull(summary.data()).customAnswers()).isNull();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT details::text FROM audit_events WHERE entity_id=?",
                String.class,
                fixture.application()))
        .allSatisfy(
            details ->
                assertThat(details)
                    .doesNotContain("Respuesta privada breve", "Detalle privado", "2024-02-29"));
    assertThat(
            count(
                "SELECT count(*) FROM audit_events WHERE entity_id=? AND action='ENROLLMENT_APPLICATION_DRAFT_UPDATED'",
                fixture.application()))
        .isEqualTo(1);
  }

  @Test
  void omissionPreservesAnswersAndAnExplicitEmptyMapClearsThem() {
    final var fixture = fixture();
    final var saved = save(fixture, 0, completeAnswers(), false);
    final var onlyCourses =
        applications.updateDraft(
            fixture.person(),
            fixture.application(),
            UpdateEnrollmentDraftRequest.builder()
                .expectedRevision(saved.draftRevision())
                .data(
                    EnrollmentDraftData.builder()
                        .courses(List.of(new CourseSelectionDto(fixture.course(), null)))
                        .build())
                .build());
    assertThat(answers(onlyCourses)).satisfies(this::assertCompleteAnswers);
    assertThat(onlyCourses.draftRevision()).isEqualTo(2);

    final var cleared = save(fixture, onlyCourses.draftRevision(), Map.of(), false);
    assertThat(answers(cleared)).isEmpty();
    assertThat(answers(read(fixture))).isEmpty();
    assertThat(cleared.draftRevision()).isEqualTo(3);
    assertThatThrownBy(
            () ->
                applications.submitApplication(
                    fixture.person(), fixture.application(), cleared.draftRevision()))
        .isInstanceOfSatisfying(
            EnrollmentValidationException.class,
            exception ->
                assertThat(requireNonNull(exception.fieldErrors()))
                    .containsKeys(
                        "customAnswers." + TEXT,
                        "customAnswers." + BOOLEAN,
                        "customAnswers." + NUMBER));
    assertThat(read(fixture).status()).isEqualTo(EnrollmentApplicationStatus.DRAFT);
    assertThat(read(fixture).draftRevision()).isEqualTo(3);
  }

  @ParameterizedTest
  @MethodSource("invalidAnswers")
  void invalidAnswersDoNotReplaceThePreviousSnapshot(final Map<String, Object> invalid) {
    final var fixture = fixture();
    final var saved = save(fixture, 0, completeAnswers(), true);

    assertThatThrownBy(() -> save(fixture, saved.draftRevision(), invalid, false))
        .isInstanceOf(EnrollmentValidationException.class);

    final var after = read(fixture);
    assertThat(answers(after)).satisfies(this::assertCompleteAnswers);
    assertThat(after.draftRevision()).isEqualTo(saved.draftRevision());
  }

  @Test
  void removingNativeBlocksRemovesTheirRequirementsButRejectsTheirIncomingPayloads() {
    final var fixture = fixture();
    final var saved = save(fixture, 0, completeAnswers(), true);
    final var request =
        UpdateEnrollmentDraftRequest.builder()
            .expectedRevision(saved.draftRevision())
            .data(
                EnrollmentDraftData.builder()
                    .customAnswers(Map.of(TEXT.toString(), "No debe guardarse"))
                    .academicBackground(
                        AcademicBackgroundDto.builder().currentlyStudying(false).build())
                    .preference(PreferenceDto.builder().preferredShift("MORNING").build())
                    .build())
            .build();

    assertThatThrownBy(
            () -> applications.updateDraft(fixture.person(), fixture.application(), request))
        .isInstanceOf(EnrollmentValidationException.class);
    assertThat(answers(read(fixture))).satisfies(this::assertCompleteAnswers);

    final var submitted =
        applications.submitApplication(
            fixture.person(), fixture.application(), saved.draftRevision());
    assertThat(submitted.status()).isEqualTo(EnrollmentApplicationStatus.SUBMITTED);
    assertThat(submitted.draftRevision()).isEqualTo(saved.draftRevision() + 1);
    assertCompleteAnswers(answers(submitted));
    assertThat(requireNonNull(submitted.data()).academicBackground()).isNull();
    assertThat(requireNonNull(submitted.data()).preference()).isNull();
  }

  @Test
  void staleSavesAndSubmissionsNeverOverwriteARecentlySavedDraft() {
    final var fixture = fixture();
    assertThatThrownBy(
            () ->
                applications.updateDraft(
                    fixture.person(),
                    fixture.application(),
                    UpdateEnrollmentDraftRequest.builder()
                        .data(
                            EnrollmentDraftData.builder().customAnswers(completeAnswers()).build())
                        .build()))
        .isInstanceOfSatisfying(
            ApplicationException.class,
            error -> assertThat(error.category()).isEqualTo(ErrorCategory.INVALID_INPUT));
    final var saved = save(fixture, 0, completeAnswers(), true);

    assertThatThrownBy(() -> save(fixture, 0, Map.of(), false))
        .isInstanceOfSatisfying(
            ApplicationException.class,
            error -> assertThat(error.category()).isEqualTo(ErrorCategory.CONFLICT));
    assertThatThrownBy(
            () -> applications.submitApplication(fixture.person(), fixture.application(), 0L))
        .isInstanceOfSatisfying(
            ApplicationException.class,
            error -> assertThat(error.category()).isEqualTo(ErrorCategory.CONFLICT));
    assertThatThrownBy(
            () -> applications.submitApplication(fixture.person(), fixture.application()))
        .isInstanceOfSatisfying(
            ApplicationException.class,
            error -> assertThat(error.category()).isEqualTo(ErrorCategory.INVALID_INPUT));

    final var after = read(fixture);
    assertThat(after.status()).isEqualTo(EnrollmentApplicationStatus.DRAFT);
    assertThat(after.draftRevision()).isEqualTo(saved.draftRevision());
    assertThat(answers(after)).satisfies(this::assertCompleteAnswers);
  }

  @Test
  void newAssignmentsAndArchivalDoNotChangeTheDefinitionUsedByExistingDrafts() {
    final var fixture = fixture();
    final var original = read(fixture);
    final var draft =
        versions.createDraft(
            fixture.institution(),
            fixture.form(),
            new EnrollmentFormVersionCreateRequest(fixture.version()));
    final var extraId = UUID.randomUUID();
    final var sections = new ArrayList<>(draft.definition().sections());
    sections.add(
        new Section(
            UUID.randomUUID(),
            "Nueva sección",
            null,
            List.of(question(extraId, EnrollmentFormQuestionType.SHORT_TEXT, true, List.of()))));
    final var edited =
        versions.replace(
            fixture.institution(),
            fixture.form(),
            draft.id(),
            new EnrollmentFormDefinitionUpdateRequest(
                new EnrollmentFormDefinition(sections, List.of()), draft.revision()));
    final var published =
        versions.publish(
            fixture.institution(),
            fixture.form(),
            edited.id(),
            new EnrollmentFormRevisionRequest(edited.revision()));
    assignments.replace(
        fixture.institution(),
        fixture.assignment(),
        new EnrollmentFormAssignmentUpdateRequest(published.id(), 0L));
    final var first = versions.get(fixture.institution(), fixture.form(), fixture.version());
    versions.archive(
        fixture.institution(),
        fixture.form(),
        first.id(),
        new EnrollmentFormRevisionRequest(first.revision()));

    final var reopened =
        applications.startOrGetApplication(
            fixture.institution(),
            fixture.person(),
            new StartEnrollmentApplicationRequest(fixture.path(), null, fixture.year()));
    assertThat(reopened.formVersionId()).isEqualTo(fixture.version());
    assertThat(reopened.formDefinition()).isEqualTo(original.formDefinition());
    assertThatThrownBy(() -> save(fixture, 0, Map.of(extraId.toString(), "No corresponde"), false))
        .isInstanceOf(EnrollmentValidationException.class);
    final var saved = save(fixture, 0, completeAnswers(), true);
    assertThat(
            applications
                .submitApplication(fixture.person(), fixture.application(), saved.draftRevision())
                .status())
        .isEqualTo(EnrollmentApplicationStatus.SUBMITTED);
  }

  @Test
  void customAnswersCannotReplaceCourseAndIdentityRequirements() {
    final var fixture = fixture();
    final var saved = save(fixture, 0, completeAnswers(), false);
    assertThatThrownBy(
            () ->
                applications.submitApplication(
                    fixture.person(), fixture.application(), saved.draftRevision()))
        .isInstanceOfSatisfying(
            EnrollmentValidationException.class,
            error -> assertThat(requireNonNull(error.fieldErrors())).containsKey("courses"));
    final var withCourse = save(fixture, saved.draftRevision(), completeAnswers(), true);
    jdbcTemplate.update("UPDATE people SET birth_date=NULL WHERE person_id=?", fixture.person());

    assertThatThrownBy(
            () ->
                applications.submitApplication(
                    fixture.person(), fixture.application(), withCourse.draftRevision()))
        .isInstanceOfSatisfying(
            EnrollmentValidationException.class,
            error ->
                assertThat(requireNonNull(error.fieldErrors()))
                    .containsKey("personalData.birthDate"));
    assertThat(read(fixture).status()).isEqualTo(EnrollmentApplicationStatus.DRAFT);
  }

  @Test
  void thePinnedFormDoesNotFreezeTheCurrentDocumentRequirements() {
    final var fixture = fixture();
    final var saved = save(fixture, 0, completeAnswers(), true);
    tx(
        () -> {
          final var institution =
              requireNonNull(entities.find(Institution.class, fixture.institution()));
          final var document = DocumentDefinition.create(institution);
          document.update(
              "Documento obligatorio", "Instrucciones", List.of("application/pdf"), true);
          persist(document);
          final var requirement =
              TrainingPathDocumentRequirement.create(
                  requireNonNull(entities.find(TrainingPath.class, fixture.path())), document);
          requirement.update(DocumentRequirementLevel.AT_SUBMISSION, 0, true, null);
          return persist(requirement);
        });

    assertThatThrownBy(
            () ->
                applications.submitApplication(
                    fixture.person(), fixture.application(), saved.draftRevision()))
        .isInstanceOf(EnrollmentValidationException.class);
    assertThat(read(fixture).status()).isEqualTo(EnrollmentApplicationStatus.DRAFT);
    assertThat(read(fixture).draftRevision()).isEqualTo(saved.draftRevision());
    assertThat(answers(read(fixture))).satisfies(this::assertCompleteAnswers);
  }

  @Test
  void legacyClientsCanStillSaveAndSubmitWithoutARevision() {
    final var context = academicContext();
    final var draft =
        applications.startOrGetApplication(
            context.institution(),
            context.person(),
            new StartEnrollmentApplicationRequest(context.path(), null, context.year()));
    final UUID id = requireNonNull(draft.applicationId());
    assertThat(draft.formVersionId()).isNull();
    assertThat(draft.formDefinition()).isEqualTo(forms.base(context.institution()));
    final var saved =
        applications.updateDraft(
            context.person(),
            id,
            UpdateEnrollmentDraftRequest.builder()
                .data(
                    EnrollmentDraftData.builder()
                        .academicBackground(
                            AcademicBackgroundDto.builder()
                                .currentlyStudying(false)
                                .educationLevel(EducationLevel.NO_SCHOOLING)
                                .build())
                        .preference(PreferenceDto.builder().preferredShift("MORNING").build())
                        .courses(List.of(new CourseSelectionDto(context.course(), null)))
                        .build())
                .build());
    assertThat(saved.draftRevision()).isPositive();
    assertThatThrownBy(
            () ->
                applications.updateDraft(
                    context.person(),
                    id,
                    UpdateEnrollmentDraftRequest.builder()
                        .expectedRevision(0L)
                        .data(EnrollmentDraftData.builder().build())
                        .build()))
        .isInstanceOfSatisfying(
            ApplicationException.class,
            error -> assertThat(error.category()).isEqualTo(ErrorCategory.CONFLICT));
    assertThat(applications.submitApplication(context.person(), id).status())
        .isEqualTo(EnrollmentApplicationStatus.SUBMITTED);
  }

  @Test
  void concurrentSavesWithTheSameRevisionHaveExactlyOneWinner() throws Exception {
    final var fixture = fixture();
    final var start = new CountDownLatch(1);
    try (final var executor = Executors.newFixedThreadPool(2)) {
      final var attempts =
          Stream.of("Primera privada", "Segunda privada")
              .map(
                  value ->
                      executor.submit(
                          () -> {
                            start.await();
                            authenticatePlatformAdministrator();
                            try {
                              final var answers = new HashMap<>(completeAnswers());
                              answers.put(TEXT.toString(), value);
                              save(fixture, 0, answers, false);
                              return true;
                            } catch (ApplicationException conflict) {
                              assertThat(conflict.category()).isEqualTo(ErrorCategory.CONFLICT);
                              return false;
                            } finally {
                              SecurityContextHolder.clearContext();
                            }
                          }))
              .toList();
      start.countDown();
      int winners = 0;
      for (final var attempt : attempts) {
        if (attempt.get(20, TimeUnit.SECONDS)) {
          winners++;
        }
      }
      assertThat(winners).isEqualTo(1);
    }
    assertThat(read(fixture).draftRevision()).isEqualTo(1);
    assertThat(answers(read(fixture)).get(TEXT.toString()))
        .isIn("Primera privada", "Segunda privada");
    assertThat(
            count(
                "SELECT count(*) FROM audit_events WHERE entity_id=? AND action='ENROLLMENT_APPLICATION_DRAFT_UPDATED'",
                fixture.application()))
        .isEqualTo(1);
  }

  private static Stream<Arguments> invalidAnswers() {
    return Stream.of(
        Arguments.of(Map.of(NUMBER.toString(), "12")),
        Arguments.of(Map.of(BOOLEAN.toString(), "false")),
        Arguments.of(Map.of(SINGLE.toString(), MULTI_A.toString())),
        Arguments.of(Map.of(UUID.randomUUID().toString(), "Otra pregunta")),
        Arguments.of(Map.of(MULTIPLE.toString(), List.of(MULTI_A.toString(), MULTI_A.toString()))));
  }
}
