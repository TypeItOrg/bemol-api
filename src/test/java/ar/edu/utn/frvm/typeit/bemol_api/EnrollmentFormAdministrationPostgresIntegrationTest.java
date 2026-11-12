package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ApplicationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Question;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Section;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormVersion;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormNativeBlock;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormQuestionType;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormVersionStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentFormAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormAssignmentCreateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormAssignmentUpdateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormCreateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormDefinitionUpdateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormRenameRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormRevisionRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormVersionCreateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormVersionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentFormAssignmentUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentFormCatalogUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentFormVersionUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

class EnrollmentFormAdministrationPostgresIntegrationTest extends DatabaseMigrationTestSupport {
  private static final Pageable PAGE = Pageable.ofSize(20);

  @Autowired private EnrollmentFormCatalogUseCase catalog;
  @Autowired private EnrollmentFormVersionUseCase versions;
  @Autowired private EnrollmentFormAssignmentUseCase assignments;
  @Autowired private EnrollmentFormAssignmentRepository assignmentRepository;
  @Autowired private EntityManager entities;
  @Autowired private TransactionTemplate transaction;

  @BeforeEach
  void authenticate() {
    authenticatePlatformAdministrator();
  }

  @Test
  void administersVersionsWithoutReinterpretingAssignmentsOrExistingApplications() {
    final var context = context(AcademicYearStatus.ACTIVE);
    final var form =
        catalog.create(context.institution(), new EnrollmentFormCreateRequest("Talleres"));
    final var firstDraft = firstDraft(context.institution(), form.id());
    assertThat(firstDraft.versionNumber()).isEqualTo(1);
    assertThat(firstDraft.definition()).isEqualTo(catalog.base(context.institution()));
    final var definition = withQuestion(firstDraft.definition(), "Pregunta privada de prueba");
    final var edited =
        versions.replace(
            context.institution(),
            form.id(),
            firstDraft.id(),
            new EnrollmentFormDefinitionUpdateRequest(definition, firstDraft.revision()));
    final var first =
        versions.publish(
            context.institution(),
            form.id(),
            edited.id(),
            new EnrollmentFormRevisionRequest(edited.revision()));
    final var assignment =
        assignments.create(
            context.institution(),
            new EnrollmentFormAssignmentCreateRequest(context.path(), context.year(), first.id()));
    final UUID application = pinApplication(context, first.id());

    final var secondDraft =
        versions.createDraft(
            context.institution(), form.id(), new EnrollmentFormVersionCreateRequest(first.id()));
    assertThat(secondDraft.versionNumber()).isEqualTo(2);
    assertThat(secondDraft.definition()).isEqualTo(definition);
    final var secondEdited =
        versions.replace(
            context.institution(),
            form.id(),
            secondDraft.id(),
            new EnrollmentFormDefinitionUpdateRequest(
                withQuestion(secondDraft.definition(), "Pregunta nueva"), secondDraft.revision()));
    final var second =
        versions.publish(
            context.institution(),
            form.id(),
            secondEdited.id(),
            new EnrollmentFormRevisionRequest(secondEdited.revision()));

    assertThat(assignments.list(context.institution(), context.year(), PAGE).getContent())
        .singleElement()
        .satisfies(value -> assertThat(value.formVersionId()).isEqualTo(first.id()));
    assertThat(versions.get(context.institution(), form.id(), first.id()).definition())
        .isEqualTo(definition);
    final var reassigned =
        assignments.replace(
            context.institution(),
            assignment.id(),
            new EnrollmentFormAssignmentUpdateRequest(second.id(), assignment.revision()));
    assertThat(reassigned.revision()).isGreaterThan(assignment.revision());
    versions.archive(
        context.institution(),
        form.id(),
        first.id(),
        new EnrollmentFormRevisionRequest(first.revision()));
    assignments.delete(context.institution(), assignment.id(), reassigned.revision());

    assertThat(
            assignmentRepository.findByInstitutionIdAndTrainingPathIdAndAcademicYearId(
                context.institution(), context.path(), context.year()))
        .isEmpty();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT form_version_id FROM enrollment_applications WHERE enrollment_application_id=?",
                UUID.class,
                application))
        .isEqualTo(first.id());
    assertThat(versions.get(context.institution(), form.id(), first.id()).status())
        .isEqualTo(EnrollmentFormVersionStatus.ARCHIVED);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT action FROM audit_events WHERE institution_id=?",
                String.class,
                context.institution()))
        .contains(
            "ENROLLMENT_FORM_CREATED",
            "ENROLLMENT_FORM_VERSION_UPDATED",
            "ENROLLMENT_FORM_VERSION_PUBLISHED",
            "ENROLLMENT_FORM_ASSIGNED",
            "ENROLLMENT_FORM_VERSION_ARCHIVED",
            "ENROLLMENT_FORM_UNASSIGNED");
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT details::text FROM audit_events WHERE institution_id=?",
                String.class,
                context.institution()))
        .allSatisfy(
            details ->
                assertThat(details)
                    .doesNotContain(
                        "Pregunta privada de prueba",
                        "Pregunta nueva",
                        "questions",
                        "answers",
                        "definition"));
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT DISTINCT actor_kind FROM audit_events WHERE institution_id=?",
                String.class,
                context.institution()))
        .containsExactly("PLATFORM");
  }

  @Test
  void staleEditsAndNoOpsDoNotOverwriteDataOrAppendSpuriousAuditEvents() {
    final var context = context(AcademicYearStatus.ACTIVE);
    final var form =
        catalog.create(context.institution(), new EnrollmentFormCreateRequest("Original"));
    final var renamed =
        catalog.rename(
            context.institution(),
            form.id(),
            new EnrollmentFormRenameRequest("Actualizado", form.revision()));
    error(
        () ->
            catalog.rename(
                context.institution(),
                form.id(),
                new EnrollmentFormRenameRequest("Viejo", form.revision())),
        ErrorCategory.CONFLICT);
    assertThat(catalog.get(context.institution(), form.id()).name()).isEqualTo("Actualizado");
    final var draft = firstDraft(context.institution(), form.id());
    final var definition = withQuestion(draft.definition(), "Definición actual");
    final var edited =
        versions.replace(
            context.institution(),
            form.id(),
            draft.id(),
            new EnrollmentFormDefinitionUpdateRequest(definition, draft.revision()));
    error(
        () ->
            versions.replace(
                context.institution(),
                form.id(),
                draft.id(),
                new EnrollmentFormDefinitionUpdateRequest(draft.definition(), draft.revision())),
        ErrorCategory.CONFLICT);
    error(
        () ->
            versions.publish(
                context.institution(),
                form.id(),
                draft.id(),
                new EnrollmentFormRevisionRequest(draft.revision())),
        ErrorCategory.CONFLICT);
    assertThat(versions.get(context.institution(), form.id(), draft.id()).definition())
        .isEqualTo(definition);
    final long audits = count("audit_events", context.institution());

    assertThat(
            catalog
                .rename(
                    context.institution(),
                    form.id(),
                    new EnrollmentFormRenameRequest(renamed.name(), renamed.revision()))
                .revision())
        .isEqualTo(renamed.revision());
    assertThat(
            versions
                .replace(
                    context.institution(),
                    form.id(),
                    draft.id(),
                    new EnrollmentFormDefinitionUpdateRequest(definition, edited.revision()))
                .revision())
        .isEqualTo(edited.revision());
    assertThat(count("audit_events", context.institution())).isEqualTo(audits);
  }

  @Test
  void publicationRejectsMissingCoreBlocksAndLeavesDraftAndAuditUnchanged() {
    final var context = context(AcademicYearStatus.ACTIVE);
    final var form =
        catalog.create(context.institution(), new EnrollmentFormCreateRequest("Incompleto"));
    final var draft = firstDraft(context.institution(), form.id());
    final var incomplete =
        new EnrollmentFormDefinition(
            draft.definition().sections().stream()
                .filter(section -> section.nativeBlock() != EnrollmentFormNativeBlock.PERSONAL_DATA)
                .toList(),
            List.of());
    final var edited =
        versions.replace(
            context.institution(),
            form.id(),
            draft.id(),
            new EnrollmentFormDefinitionUpdateRequest(incomplete, draft.revision()));
    final long audits = count("audit_events", context.institution());

    error(
        () ->
            versions.publish(
                context.institution(),
                form.id(),
                draft.id(),
                new EnrollmentFormRevisionRequest(edited.revision())),
        ErrorCategory.INVALID_INPUT);
    error(
        () ->
            versions.createDraft(
                context.institution(),
                form.id(),
                new EnrollmentFormVersionCreateRequest(draft.id())),
        ErrorCategory.CONFLICT);
    final var stored = versions.get(context.institution(), form.id(), draft.id());
    assertThat(stored.status()).isEqualTo(EnrollmentFormVersionStatus.DRAFT);
    assertThat(stored.revision()).isEqualTo(edited.revision());
    assertThat(stored.definition()).isEqualTo(incomplete);
    assertThat(count("audit_events", context.institution())).isEqualTo(audits);
  }

  @Test
  void staleAssignmentsAndArchivalOfAssignedVersionsLeaveBothRecordsUnchanged() {
    final var context = context(AcademicYearStatus.ACTIVE);
    final var first = published(context);
    final var assignment =
        assignments.create(
            context.institution(),
            new EnrollmentFormAssignmentCreateRequest(context.path(), context.year(), first.id()));
    final var draft =
        versions.createDraft(
            context.institution(),
            first.formId(),
            new EnrollmentFormVersionCreateRequest(first.id()));
    final var second =
        versions.publish(
            context.institution(),
            first.formId(),
            draft.id(),
            new EnrollmentFormRevisionRequest(draft.revision()));
    final long audits = count("audit_events", context.institution());

    error(
        () ->
            versions.archive(
                context.institution(),
                first.formId(),
                first.id(),
                new EnrollmentFormRevisionRequest(first.revision())),
        ErrorCategory.CONFLICT);
    error(
        () ->
            assignments.create(
                context.institution(),
                new EnrollmentFormAssignmentCreateRequest(
                    context.path(), context.year(), second.id())),
        ErrorCategory.CONFLICT);
    error(
        () ->
            assignments.replace(
                context.institution(),
                assignment.id(),
                new EnrollmentFormAssignmentUpdateRequest(second.id(), assignment.revision() + 1)),
        ErrorCategory.CONFLICT);
    error(
        () -> assignments.delete(context.institution(), assignment.id(), assignment.revision() + 1),
        ErrorCategory.CONFLICT);
    assertThat(versions.get(context.institution(), first.formId(), first.id()).status())
        .isEqualTo(EnrollmentFormVersionStatus.PUBLISHED);
    assertThat(assignments.list(context.institution(), null, PAGE).getContent())
        .containsExactly(assignment);
    assertThat(count("audit_events", context.institution())).isEqualTo(audits);
  }

  @Test
  void isolatesModelsVersionsAndAssignmentsAcrossInstitutionsAndParentModels() {
    final var first = context(AcademicYearStatus.ACTIVE);
    final var other = context(AcademicYearStatus.ACTIVE);
    final var version = published(first);
    final var foreign = published(other);
    final var foreignAssignment =
        assignments.create(
            other.institution(),
            new EnrollmentFormAssignmentCreateRequest(other.path(), other.year(), foreign.id()));

    error(() -> catalog.get(first.institution(), foreign.formId()), ErrorCategory.NOT_FOUND);
    error(() -> catalog.list(UUID.randomUUID(), PAGE), ErrorCategory.NOT_FOUND);
    error(
        () -> versions.list(first.institution(), foreign.formId(), PAGE), ErrorCategory.NOT_FOUND);
    error(
        () -> versions.get(first.institution(), version.formId(), foreign.id()),
        ErrorCategory.NOT_FOUND);
    error(
        () ->
            versions.createDraft(
                first.institution(),
                version.formId(),
                new EnrollmentFormVersionCreateRequest(foreign.id())),
        ErrorCategory.NOT_FOUND);
    error(
        () ->
            assignments.create(
                first.institution(),
                new EnrollmentFormAssignmentCreateRequest(
                    other.path(), first.year(), version.id())),
        ErrorCategory.NOT_FOUND);
    error(
        () ->
            assignments.create(
                first.institution(),
                new EnrollmentFormAssignmentCreateRequest(
                    first.path(), other.year(), version.id())),
        ErrorCategory.NOT_FOUND);
    error(
        () ->
            assignments.create(
                first.institution(),
                new EnrollmentFormAssignmentCreateRequest(
                    first.path(), first.year(), foreign.id())),
        ErrorCategory.NOT_FOUND);
    error(
        () ->
            assignments.replace(
                first.institution(),
                foreignAssignment.id(),
                new EnrollmentFormAssignmentUpdateRequest(
                    version.id(), foreignAssignment.revision())),
        ErrorCategory.NOT_FOUND);
    error(
        () ->
            assignments.delete(
                first.institution(), foreignAssignment.id(), foreignAssignment.revision()),
        ErrorCategory.NOT_FOUND);
    assertThat(catalog.list(first.institution(), PAGE).getContent())
        .singleElement()
        .satisfies(value -> assertThat(value.id()).isEqualTo(version.formId()));
    assertThat(assignments.list(first.institution(), null, PAGE)).isEmpty();
    assertThat(assignments.list(other.institution(), other.year(), PAGE).getContent())
        .containsExactly(foreignAssignment);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rejectsInactiveOrDeletedPathsWithoutPreventingRetirement(final boolean deleted) {
    final var context = context(AcademicYearStatus.ACTIVE);
    final var version = published(context);
    final var assignment =
        assignments.create(
            context.institution(),
            new EnrollmentFormAssignmentCreateRequest(
                context.path(), context.year(), version.id()));
    transaction.executeWithoutResult(
        status -> {
          final var path = requireNonNull(entities.find(TrainingPath.class, context.path()));
          path.updateStatus(false);
          if (deleted) {
            path.delete(Instant.parse("2026-11-06T20:00:00Z"));
          }
        });

    error(
        () ->
            assignments.replace(
                context.institution(),
                assignment.id(),
                new EnrollmentFormAssignmentUpdateRequest(version.id(), assignment.revision())),
        ErrorCategory.NOT_FOUND);
    assignments.delete(context.institution(), assignment.id(), assignment.revision());
    error(
        () ->
            assignments.create(
                context.institution(),
                new EnrollmentFormAssignmentCreateRequest(
                    context.path(), context.year(), version.id())),
        ErrorCategory.NOT_FOUND);
    assertThat(assignments.list(context.institution(), null, PAGE)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rejectsClosedOrDeletedYearsWithoutPreventingRetirement(final boolean deleted) {
    final var context = context(deleted ? AcademicYearStatus.PLANNED : AcademicYearStatus.ACTIVE);
    final var version = published(context);
    final var assignment =
        assignments.create(
            context.institution(),
            new EnrollmentFormAssignmentCreateRequest(
                context.path(), context.year(), version.id()));
    transaction.executeWithoutResult(
        status -> {
          final var year = requireNonNull(entities.find(AcademicYear.class, context.year()));
          if (deleted) {
            year.delete(Instant.parse("2026-11-06T20:00:00Z"));
          } else {
            year.transitionTo(AcademicYearStatus.CLOSED);
          }
        });
    final var category = deleted ? ErrorCategory.NOT_FOUND : ErrorCategory.CONFLICT;

    error(
        () ->
            assignments.replace(
                context.institution(),
                assignment.id(),
                new EnrollmentFormAssignmentUpdateRequest(version.id(), assignment.revision())),
        category);
    assignments.delete(context.institution(), assignment.id(), assignment.revision());
    error(
        () ->
            assignments.create(
                context.institution(),
                new EnrollmentFormAssignmentCreateRequest(
                    context.path(), context.year(), version.id())),
        category);
    assertThat(assignments.list(context.institution(), null, PAGE)).isEmpty();
  }

  @Test
  void previewIsValidatedTenantScopedAndDoesNotPersistFormsOrAudit() {
    final var context = context(AcademicYearStatus.ACTIVE);
    final var definition = withQuestion(catalog.base(context.institution()), "Vista previa");
    final long audits = count("audit_events", context.institution());

    assertThat(catalog.preview(context.institution(), definition)).isEqualTo(definition);
    error(
        () ->
            catalog.preview(
                context.institution(), new EnrollmentFormDefinition(List.of(), List.of())),
        ErrorCategory.INVALID_INPUT);
    error(() -> catalog.base(UUID.randomUUID()), ErrorCategory.NOT_FOUND);
    assertThat(count("enrollment_forms", context.institution())).isZero();
    assertThat(count("enrollment_form_versions", context.institution())).isZero();
    assertThat(count("enrollment_form_assignments", context.institution())).isZero();
    assertThat(count("audit_events", context.institution())).isEqualTo(audits);
  }

  @Test
  void concurrentCreationKeepsOneDraftAndOneAssignment() throws Exception {
    final var context = context(AcademicYearStatus.ACTIVE);
    final var version = published(context);

    race(
        () ->
            versions.createDraft(
                context.institution(),
                version.formId(),
                new EnrollmentFormVersionCreateRequest(version.id())));
    assertThat(versions.list(context.institution(), version.formId(), PAGE).getContent())
        .filteredOn(value -> value.status() == EnrollmentFormVersionStatus.DRAFT)
        .hasSize(1);
    race(
        () ->
            assignments.create(
                context.institution(),
                new EnrollmentFormAssignmentCreateRequest(
                    context.path(), context.year(), version.id())));
    assertThat(assignments.list(context.institution(), context.year(), PAGE).getContent())
        .hasSize(1);
  }

  private void race(final Supplier<?> operation) throws Exception {
    final var authentication =
        requireNonNull(SecurityContextHolder.getContext().getAuthentication());
    final var ready = new CountDownLatch(2);
    final var start = new CountDownLatch(1);
    try (final var executor = Executors.newFixedThreadPool(2)) {
      final java.util.concurrent.Callable<Boolean> competing =
          () -> {
            final var securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);
            try {
              ready.countDown();
              assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
              operation.get();
              return true;
            } catch (ApplicationException exception) {
              assertThat(exception.category()).isEqualTo(ErrorCategory.CONFLICT);
              return false;
            } finally {
              SecurityContextHolder.clearContext();
            }
          };
      final var first = executor.submit(competing);
      final var second = executor.submit(competing);
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
  }

  private EnrollmentFormVersionResponse published(final Context context) {
    final var form =
        catalog.create(context.institution(), new EnrollmentFormCreateRequest("Modelo"));
    final var draft = firstDraft(context.institution(), form.id());
    return versions.publish(
        context.institution(),
        form.id(),
        draft.id(),
        new EnrollmentFormRevisionRequest(draft.revision()));
  }

  private EnrollmentFormVersionResponse firstDraft(final UUID institutionId, final UUID formId) {
    final var version = versions.list(institutionId, formId, PAGE).getContent().getFirst();
    return versions.get(institutionId, formId, version.id());
  }

  private Context context(final AcademicYearStatus state) {
    return requireNonNull(
        transaction.execute(
            status -> {
              final var institution =
                  InstitutionalTestData.createInstitution(entities, "Form administration");
              final var path = TrainingPath.create(institution, "Talleres", null);
              entities.persist(path);
              final var year =
                  AcademicYear.create(
                      institution,
                      2026,
                      LocalDate.of(2026, 3, 1),
                      LocalDate.of(2026, 12, 31),
                      LocalDate.of(2026, 11, 6));
              if (state == AcademicYearStatus.ACTIVE) {
                year.transitionTo(state);
              }
              entities.persist(year);
              entities.flush();
              return new Context(institution.getId(), path.getId(), year.getId());
            }));
  }

  private UUID pinApplication(final Context context, final UUID versionId) {
    return requireNonNull(
        transaction.execute(
            status -> {
              final var institution =
                  requireNonNull(entities.find(Institution.class, context.institution()));
              final var path = requireNonNull(entities.find(TrainingPath.class, context.path()));
              final var year = requireNonNull(entities.find(AcademicYear.class, context.year()));
              final var person = InstitutionalTestData.person(institution, "55111991");
              entities.persist(person);
              final var application =
                  EnrollmentApplication.createForTrainingPath(institution, person, path, year);
              application.bindFormVersion(
                  requireNonNull(entities.find(EnrollmentFormVersion.class, versionId)));
              entities.persist(application);
              entities.flush();
              return application.getId();
            }));
  }

  private EnrollmentFormDefinition withQuestion(
      final EnrollmentFormDefinition base, final String label) {
    final var sections = new ArrayList<>(base.sections());
    sections.add(
        new Section(
            UUID.randomUUID(),
            "Preguntas propias",
            null,
            List.of(
                new Question(
                    UUID.randomUUID(),
                    label,
                    null,
                    EnrollmentFormQuestionType.SHORT_TEXT,
                    false,
                    List.of()))));
    return new EnrollmentFormDefinition(sections, base.pdfSections());
  }

  private long count(final String table, final UUID institutionId) {
    return requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM " + table + " WHERE institution_id=?",
            Long.class,
            institutionId));
  }

  private void error(final Runnable operation, final ErrorCategory category) {
    assertThatThrownBy(operation::run)
        .isInstanceOfSatisfying(
            ApplicationException.class,
            exception -> assertThat(exception.category()).isEqualTo(category));
  }

  private record Context(UUID institution, UUID path, UUID year) {}
}
