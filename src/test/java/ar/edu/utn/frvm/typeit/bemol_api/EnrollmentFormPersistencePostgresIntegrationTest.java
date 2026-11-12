package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.GlobalExceptionHandler;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentForm;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Option;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.PdfSection;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Question;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Section;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormVersion;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormNativeBlock;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormQuestionType;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentFormAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentFormVersionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

class EnrollmentFormPersistencePostgresIntegrationTest extends DatabaseMigrationTestSupport {
  private static final Instant PUBLICATION = Instant.parse("2026-11-06T20:00:00Z");

  @Autowired private EntityManager entityManager;
  @Autowired private EnrollmentFormAssignmentRepository assignments;
  @Autowired private EnrollmentFormVersionRepository versions;

  @Test
  @Transactional
  void roundTripsTypedDefinitionsAndKeepsThePinnedVersionAfterReassignment() {
    final var context = context();
    final var institution = context.institution();
    final var path = context.path();
    final var year = context.year();
    final var person = context.person();

    final var form = EnrollmentForm.create(institution, "Talleres");
    entityManager.persist(form);
    final var definition = definition("Disponibilidad de instrumento");
    final var first = EnrollmentFormVersion.draft(form, 1, definition);
    entityManager.persist(first);
    entityManager.flush();
    first.publish(PUBLICATION, first.getRevision());
    entityManager.flush();
    final var assignment = EnrollmentFormAssignment.create(institution, path, year, first);
    entityManager.persist(assignment);

    final var application =
        EnrollmentApplication.createForTrainingPath(institution, person, path, year);
    application.bindFormVersion(first);
    entityManager.persist(application);
    entityManager.flush();
    final var firstId = first.getId();
    final var applicationId = application.getId();
    final var assignmentId = assignment.getId();
    final var formId = form.getId();
    final var institutionId = institution.getId();
    final var pathId = path.getId();
    final var yearId = year.getId();
    entityManager.clear();

    final var resolved =
        assignments
            .findByInstitutionIdAndTrainingPathIdAndAcademicYearId(institutionId, pathId, yearId)
            .orElseThrow();
    assertThat(resolved.getFormVersion().getDefinition()).isEqualTo(definition);
    assertThat(resolved.getFormVersion().getForm().getName()).isEqualTo("Talleres");
    assertThat(
            assignments.findByInstitutionIdAndTrainingPathIdAndAcademicYearId(
                UUID.randomUUID(), pathId, yearId))
        .isEmpty();
    assertThat(
            assignments.findByInstitutionIdAndTrainingPathIdAndAcademicYearId(
                institutionId, pathId, UUID.randomUUID()))
        .isEmpty();

    final var second =
        EnrollmentFormVersion.draft(
            entityManager.getReference(EnrollmentForm.class, formId),
            2,
            definition("Experiencia previa"));
    entityManager.persist(second);
    entityManager.flush();
    second.publish(PUBLICATION.plusSeconds(1), second.getRevision());
    entityManager.flush();
    resolved.assignVersion(second, resolved.getRevision());
    entityManager.flush();
    final var original = requireNonNull(entityManager.find(EnrollmentFormVersion.class, firstId));
    original.archive(PUBLICATION.plusSeconds(2), original.getRevision());
    entityManager.flush();
    entityManager.clear();

    final var stored =
        requireNonNull(entityManager.find(EnrollmentApplication.class, applicationId));
    assertThat(requireNonNull(stored.getFormState().getFormVersion()).getId()).isEqualTo(firstId);
    assertThat(requireNonNull(stored.getFormState().getFormVersion()).getDefinition())
        .isEqualTo(definition);
    assertThat(stored.getFormState().getCustomAnswers()).isEmpty();
    assertThat(stored.getFormState().getDraftRevision()).isZero();
    assertThat(
            requireNonNull(entityManager.find(EnrollmentFormAssignment.class, assignmentId))
                .getFormVersion()
                .getVersionNumber())
        .isEqualTo(2);
  }

  @Test
  @Transactional
  void translatesTheRealPostgresTriggerViolationIntoAConflict() {
    final var context = context();
    final var form = EnrollmentForm.create(context.institution(), "Formulario con asignación");
    entityManager.persist(form);
    final var version = EnrollmentFormVersion.draft(form, 1, definition("Instrumento"));
    versions.saveAndFlush(version);
    version.publish(PUBLICATION, version.getRevision());
    versions.saveAndFlush(version);
    assignments.saveAndFlush(
        EnrollmentFormAssignment.create(
            context.institution(), context.path(), context.year(), version));
    version.archive(PUBLICATION.plusSeconds(1), version.getRevision());

    assertThatThrownBy(() -> versions.saveAndFlush(version))
        .isInstanceOfSatisfying(
            DataIntegrityViolationException.class,
            exception -> {
              final var response =
                  new GlobalExceptionHandler().handlePersistenceException(exception);
              assertThat(response.getStatusCode().value()).isEqualTo(409);
              assertThat(requireNonNull(response.getBody()).message())
                  .isEqualTo(EnrollmentFormMessages.VERSION_IN_USE);
            });
  }

  private Context context() {
    final var institution =
        InstitutionalTestData.createInstitution(entityManager, "Forms institution");
    final var path = TrainingPath.create(institution, "Talleres", null);
    entityManager.persist(path);
    final var year =
        AcademicYear.create(
            institution,
            2026,
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 12, 31),
            LocalDate.of(2026, 11, 6));
    year.transitionTo(AcademicYearStatus.ACTIVE);
    entityManager.persist(year);
    final var person = InstitutionalTestData.person(institution, "50100101");
    entityManager.persist(person);
    return new Context(institution, path, year, person);
  }

  private record Context(
      Institution institution, TrainingPath path, AcademicYear year, Person person) {}

  private EnrollmentFormDefinition definition(final String label) {
    final var sections = new ArrayList<Section>();
    for (final var block :
        List.of(
            EnrollmentFormNativeBlock.PERSONAL_DATA,
            EnrollmentFormNativeBlock.RESPONSIBLE,
            EnrollmentFormNativeBlock.ACADEMIC_SELECTION,
            EnrollmentFormNativeBlock.DOCUMENTATION)) {
      sections.add(new Section(UUID.randomUUID(), block.name(), block, List.of()));
    }
    final var question =
        new Question(
            UUID.randomUUID(),
            label,
            "Información adicional",
            EnrollmentFormQuestionType.SINGLE_CHOICE,
            false,
            List.of(new Option(UUID.randomUUID(), "Sí"), new Option(UUID.randomUUID(), "No")));
    final var custom = new Section(UUID.randomUUID(), "Preguntas propias", null, List.of(question));
    sections.add(custom);
    return new EnrollmentFormDefinition(
        sections, List.of(new PdfSection(custom.id(), List.of(question.id()))));
  }
}
