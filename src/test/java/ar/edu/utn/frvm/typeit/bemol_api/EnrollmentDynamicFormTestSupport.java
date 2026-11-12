package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.ApprovalMode;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.RequirementType;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AssignPersonSystemRoleUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionRoleProvisioner;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Option;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Question;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Section;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPeriod;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPeriodOffering;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormNativeBlock;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormQuestionType;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentPeriodStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseSelectionDto;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentApplicationResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentDraftData;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormAssignmentCreateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormCreateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormDefinitionUpdateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormRevisionRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.StartEnrollmentApplicationRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.UpdateEnrollmentDraftRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentApplicationService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentFormAssignmentUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentFormCatalogUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentFormVersionUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionTemplate;

abstract class EnrollmentDynamicFormTestSupport extends DatabaseMigrationTestSupport {
  protected static final UUID TEXT = UUID.fromString("10000000-0000-0000-0000-000000000001");
  protected static final UUID LONG_TEXT = UUID.fromString("10000000-0000-0000-0000-000000000002");
  protected static final UUID NUMBER = UUID.fromString("10000000-0000-0000-0000-000000000003");
  protected static final UUID DATE = UUID.fromString("10000000-0000-0000-0000-000000000004");
  protected static final UUID BOOLEAN = UUID.fromString("10000000-0000-0000-0000-000000000005");
  protected static final UUID SINGLE = UUID.fromString("10000000-0000-0000-0000-000000000006");
  protected static final UUID MULTIPLE = UUID.fromString("10000000-0000-0000-0000-000000000007");
  protected static final UUID OPTION_A = UUID.fromString("20000000-0000-0000-0000-000000000001");
  protected static final UUID OPTION_B = UUID.fromString("20000000-0000-0000-0000-000000000002");
  protected static final UUID MULTI_A = UUID.fromString("20000000-0000-0000-0000-000000000003");
  protected static final UUID MULTI_B = UUID.fromString("20000000-0000-0000-0000-000000000004");
  private static final AtomicInteger DOCUMENTS = new AtomicInteger(99230000);

  @Autowired protected EntityManager entities;
  @Autowired protected TransactionTemplate transaction;
  @Autowired protected EnrollmentApplicationService applications;
  @Autowired protected EnrollmentFormCatalogUseCase forms;
  @Autowired protected EnrollmentFormVersionUseCase versions;
  @Autowired protected EnrollmentFormAssignmentUseCase assignments;
  @Autowired protected InstitutionRoleProvisioner roles;
  @Autowired protected AssignPersonSystemRoleUseCase assignRole;
  @Autowired protected Clock clock;

  @BeforeEach
  void authenticateFormOperations() {
    authenticatePlatformAdministrator();
  }

  protected Fixture fixture() {
    final var context = academicContext();
    final var form =
        forms.create(context.institution(), new EnrollmentFormCreateRequest("Dinámico"));
    final var draft =
        versions.list(context.institution(), form.id(), Pageable.ofSize(1)).getContent().getFirst();
    final var definition = definition(forms.base(context.institution()));
    final var edited =
        versions.replace(
            context.institution(),
            form.id(),
            draft.id(),
            new EnrollmentFormDefinitionUpdateRequest(definition, draft.revision()));
    final var published =
        versions.publish(
            context.institution(),
            form.id(),
            edited.id(),
            new EnrollmentFormRevisionRequest(edited.revision()));
    final var assignment =
        assignments.create(
            context.institution(),
            new EnrollmentFormAssignmentCreateRequest(
                context.path(), context.year(), published.id()));
    final var application =
        applications.startOrGetApplication(
            context.institution(),
            context.person(),
            new StartEnrollmentApplicationRequest(context.path(), null, context.year()));
    return new Fixture(
        context.institution(),
        context.path(),
        context.year(),
        context.period(),
        context.course(),
        context.person(),
        requireNonNull(application.applicationId()),
        form.id(),
        published.id(),
        assignment.id());
  }

  protected Context academicContext() {
    return tx(
        () -> {
          final var institution =
              InstitutionalTestData.createInstitution(entities, "dynamic-" + UUID.randomUUID());
          roles.provision(institution);
          final var person =
              persist(
                  InstitutionalTestData.person(
                      institution,
                      Integer.toString(DOCUMENTS.incrementAndGet()),
                      LocalDate.of(2000, 1, 1)));
          assignRole.execute(person, SystemRoleCode.APPLICANT, false);
          final var path = persist(TrainingPath.create(institution, "Taller", null));
          final var plan =
              persist(StudyPlan.create(institution, path, "Plan", LocalDate.of(2026, 1, 1), null));
          plan.activate();
          final var space =
              persist(
                  AcademicSpace.create(
                      institution,
                      "Curso",
                      null,
                      AcademicSpaceType.SUBJECT,
                      AcademicSpaceFormat.GRUPAL,
                      false));
          final var placement =
              persist(
                  StudyPlanSpace.create(
                      institution,
                      plan,
                      space,
                      null,
                      RequirementType.REQUIRED,
                      1,
                      ApprovalMode.PROMOTION));
          final var year =
              persist(
                  AcademicYear.create(
                      institution,
                      2026,
                      LocalDate.of(2026, 1, 1),
                      LocalDate.of(2026, 12, 31),
                      LocalDate.of(2026, 1, 1)));
          year.transitionTo(AcademicYearStatus.ACTIVE);
          final var course = persist(Course.create(institution, placement, year, null));
          final var period =
              persist(
                  EnrollmentPeriod.builder()
                      .institution(institution)
                      .academicYear(year)
                      .name("Ingreso")
                      .startDate(clock.instant().minusSeconds(86400))
                      .endDate(clock.instant().plusSeconds(86400))
                      .status(EnrollmentPeriodStatus.OPEN)
                      .build());
          entities.flush();
          period.markScopeConfigured();
          final var offering = EnrollmentPeriodOffering.create(period, plan);
          offering.selectLevels(List.of(), true);
          period.getOfferings().add(offering);
          persist(offering);
          return new Context(
              institution.getId(),
              path.getId(),
              year.getId(),
              period.getId(),
              course.getId(),
              person.getId());
        });
  }

  protected EnrollmentApplicationResponse read(final Fixture fixture) {
    return applications.getApplicationById(
        fixture.institution(), fixture.person(), fixture.application());
  }

  protected EnrollmentApplicationResponse save(
      final Fixture fixture,
      final long revision,
      final Map<String, Object> answers,
      final boolean selectCourse) {
    return applications.updateDraft(
        fixture.person(),
        fixture.application(),
        UpdateEnrollmentDraftRequest.builder()
            .expectedRevision(revision)
            .data(
                EnrollmentDraftData.builder()
                    .customAnswers(answers)
                    .courses(
                        selectCourse
                            ? List.of(new CourseSelectionDto(fixture.course(), null))
                            : null)
                    .build())
            .build());
  }

  protected Map<String, Object> completeAnswers() {
    return Map.of(
        TEXT.toString(),
        "Respuesta privada breve",
        LONG_TEXT.toString(),
        "Detalle privado\ncon otra línea",
        NUMBER.toString(),
        0,
        DATE.toString(),
        "2024-02-29",
        BOOLEAN.toString(),
        false,
        SINGLE.toString(),
        OPTION_A.toString(),
        MULTIPLE.toString(),
        List.of(MULTI_A.toString(), MULTI_B.toString()));
  }

  protected Map<String, Object> answers(final EnrollmentApplicationResponse response) {
    return requireNonNull(requireNonNull(response.data()).customAnswers());
  }

  protected void assertCompleteAnswers(final Map<String, Object> answers) {
    final var expected = new HashMap<>(completeAnswers());
    expected.remove(NUMBER.toString());
    assertThat(answers).containsAllEntriesOf(expected).hasSize(completeAnswers().size());
    assertThat(answers.get(NUMBER.toString()))
        .isInstanceOfSatisfying(Number.class, number -> assertThat(number.doubleValue()).isZero());
  }

  protected long count(final String sql, final UUID id) {
    return requireNonNull(jdbcTemplate.queryForObject(sql, Long.class, id));
  }

  protected <T> T tx(final Supplier<T> action) {
    return requireNonNull(transaction.execute(status -> action.get()));
  }

  protected <T> T persist(final T entity) {
    entities.persist(entity);
    return entity;
  }

  protected EnrollmentFormDefinition definition(final EnrollmentFormDefinition base) {
    final var sections =
        new ArrayList<>(
            base.sections().stream()
                .filter(
                    section ->
                        section.nativeBlock() != EnrollmentFormNativeBlock.SCHOOLING
                            && section.nativeBlock() != EnrollmentFormNativeBlock.HEALTH_INCLUSION
                            && section.nativeBlock() != EnrollmentFormNativeBlock.PREFERENCES)
                .toList());
    sections.add(
        new Section(
            UUID.fromString("30000000-0000-0000-0000-000000000001"),
            "Sección propia",
            null,
            List.of(
                question(TEXT, EnrollmentFormQuestionType.SHORT_TEXT, true, List.of()),
                question(LONG_TEXT, EnrollmentFormQuestionType.LONG_TEXT, false, List.of()),
                question(NUMBER, EnrollmentFormQuestionType.NUMBER, true, List.of()),
                question(DATE, EnrollmentFormQuestionType.DATE, false, List.of()),
                question(BOOLEAN, EnrollmentFormQuestionType.BOOLEAN, true, List.of()),
                question(
                    SINGLE,
                    EnrollmentFormQuestionType.SINGLE_CHOICE,
                    false,
                    List.of(new Option(OPTION_A, "Primera"), new Option(OPTION_B, "Segunda"))),
                question(
                    MULTIPLE,
                    EnrollmentFormQuestionType.MULTIPLE_CHOICE,
                    false,
                    List.of(new Option(MULTI_A, "Lunes"), new Option(MULTI_B, "Martes"))))));
    return new EnrollmentFormDefinition(sections, List.of());
  }

  protected Question question(
      final UUID id,
      final EnrollmentFormQuestionType type,
      final boolean required,
      final List<Option> options) {
    return new Question(id, "Pregunta " + type.name(), null, type, required, options);
  }

  protected record Context(
      UUID institution, UUID path, UUID year, UUID period, UUID course, UUID person) {}

  protected record Fixture(
      UUID institution,
      UUID path,
      UUID year,
      UUID period,
      UUID course,
      UUID person,
      UUID application,
      UUID form,
      UUID version,
      UUID assignment) {}
}
