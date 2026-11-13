package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicYearRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassTeacherRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCapabilitiesService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ScopedAuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.common.time.BusinessDateProvider;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.audit.EnrollmentAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.ApplicantEducationBackground;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.ApplicantPreference;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormBaseDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormVersion;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPeriod;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.ApplicationNotEditableException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentApplicationNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentPeriodClosedException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseEnrollmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentApplicationCourseRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentApplicationRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentFormAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.AcademicBackgroundDto;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentApplicationResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentDraftData;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.PersonalDataDto;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.StartEnrollmentApplicationRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.UpdateEnrollmentDraftRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.StudentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class EnrollmentApplicationServiceTest {

  @Mock private EnrollmentApplicationRepository applicationRepository;
  @Mock private EnrollmentFormAssignmentRepository formAssignments;
  @Mock private PersonRepository personRepository;
  @Mock private PersonGuardianRepository personGuardianRepository;
  @Mock private PersonRoleAssignmentRepository personRoleAssignmentRepository;

  @Mock private TrainingPathRepository trainingPathRepository;
  @Mock private AcademicYearRepository academicYearRepository;
  @Mock private AcademicYear activeYear;

  @Mock private CourseRepository courseRepository;
  @Mock private CourseClassTeacherRepository courseClassTeacherRepository;
  @Mock private EnrollmentApplicationCourseRepository applicationCourseRepository;
  @Mock private CourseEnrollmentRepository courseEnrollmentRepository;
  @Mock private StudentRepository studentRepository;
  @Mock private EnrollmentApplicationCourseApprovalService applicationCourseApprovalService;

  @Mock private AuditEventRecorder auditEventRecorder;

  @Mock private EnrollmentApplicationPeriodService applicationPeriods;
  @Mock private EnrollmentDocumentRequirementsService documentRequirements;
  @Mock private EnrollmentAdmissionHistory admissionHistory;
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC);

  private EnrollmentApplicationService service;

  private final UUID institutionId = UUID.randomUUID();
  private final UUID personId = UUID.randomUUID();
  private final UUID academicYearId = UUID.randomUUID();
  private final UUID periodId = UUID.randomUUID();
  private final UUID applicationId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    Mockito.lenient()
        .when(
            applicationRepository.findOwnedInstitutionId(
                ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Optional.of(institutionId));
    Mockito.lenient()
        .when(
            academicYearRepository.findAllByInstitutionIdAndStatus(
                institutionId, AcademicYearStatus.ACTIVE))
        .thenReturn(List.of(activeYear));
    Mockito.lenient().when(activeYear.getId()).thenReturn(academicYearId);
    Mockito.lenient()
        .when(activeYear.getInstitution())
        .thenReturn(Institution.builder().id(institutionId).build());
    Mockito.lenient().when(activeYear.getStatus()).thenReturn(AcademicYearStatus.ACTIVE);
    final var authorization = Mockito.mock(ScopedAuthorizationService.class);
    final var responses =
        new EnrollmentApplicationResponseFactory(
            applicationPeriods,
            Mockito.mock(EnrollmentDocumentAuthorization.class),
            Mockito.mock(EnrollmentDocumentAudit.class),
            documentRequirements,
            admissionHistory,
            Mockito.mock(EnrollmentApplicationCourseAuthorization.class));
    final var courses =
        new EnrollmentCourseSelectionService(
            courseRepository,
            courseClassTeacherRepository,
            personRepository,
            applicationCourseRepository,
            courseEnrollmentRepository,
            studentRepository,
            applicationPeriods,
            Mockito.mock(AcademicEligibilityService.class));
    service =
        new EnrollmentApplicationService(
            applicationRepository,
            applicationPeriods,
            responses,
            personRepository,
            trainingPathRepository,
            new EnrollmentDraftUpdater(courses),
            courses,
            new EnrollmentSubmissionValidator(new BusinessDateProvider(CLOCK)),
            applicationCourseApprovalService,
            CLOCK,
            Mockito.mock(EnrollmentInstitutionLock.class),
            documentRequirements,
            admissionHistory,
            new QueryEnrollmentApplicationsUseCase(
                Mockito.mock(InstitutionalCapabilitiesService.class),
                applicationRepository,
                responses,
                authorization),
            academicYearRepository,
            auditEventRecorder,
            org.mockito.Mockito.mock(
                ar.edu.utn.frvm.typeit.bemol_api.institutional.services.OperatingContextResolver
                    .class),
            Mockito.mock(InstitutionalCapabilitiesService.class),
            formAssignments,
            new EnrollmentCustomAnswersValidator());
  }

  @Test
  void startRequiresTrainingPath() {
    when(personRepository.findByIdAndInstitution_Id(personId, institutionId))
        .thenReturn(Optional.of(Mockito.mock(Person.class)));
    assertThatThrownBy(
            () ->
                service.startOrGetApplication(
                    institutionId,
                    personId,
                    new StartEnrollmentApplicationRequest(null, academicYearId, null)))
        .isInstanceOf(EnrollmentValidationException.class)
        .hasMessage(EnrollmentMessages.COURSE_SELECTION_REQUIRED);
  }

  @Test
  @DisplayName(
      "Should bind a training-path draft to the active academic year without a single period")
  void startOrGetApplication_usesCoursePeriods() {
    final var path = givenAvailableTrainingPath();
    when(applicationRepository.saveAndFlush(any(EnrollmentApplication.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    final var response =
        service.startOrGetApplication(
            institutionId,
            personId,
            new StartEnrollmentApplicationRequest(path.getId(), null, null));

    final var saved = ArgumentCaptor.forClass(EnrollmentApplication.class);
    verify(applicationRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getTrainingPath()).isSameAs(path);
    assertThat(saved.getValue().getAcademicYear()).isSameAs(activeYear);
    assertThat(saved.getValue().getEnrollmentPeriod()).isNull();
    assertThat(saved.getValue().getFormState().getFormVersion()).isNull();
    assertThat(saved.getValue().getFormState().getCustomAnswers()).isEmpty();
    assertThat(saved.getValue().getFormState().getDraftRevision()).isZero();
    assertThat(response.getStatus()).isEqualTo(EnrollmentApplicationStatus.DRAFT);
    verify(documentRequirements).snapshot(saved.getValue());
    verify(admissionHistory).record(saved.getValue());
  }

  @Test
  void startPinsThePublishedVersionAssignedToTheTrainingPathAndYear() {
    final var path = givenAvailableTrainingPath();
    final var assignment = Mockito.mock(EnrollmentFormAssignment.class);
    final var version = Mockito.mock(EnrollmentFormVersion.class);
    when(version.getDefinition()).thenReturn(EnrollmentFormBaseDefinition.create());
    when(assignment.getFormVersion()).thenReturn(version);
    when(formAssignments.findByInstitutionIdAndTrainingPathIdAndAcademicYearId(
            institutionId, path.getId(), academicYearId))
        .thenReturn(Optional.of(assignment));
    when(applicationRepository.saveAndFlush(any(EnrollmentApplication.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.startOrGetApplication(
        institutionId, personId, new StartEnrollmentApplicationRequest(path.getId(), null, null));

    final var saved = ArgumentCaptor.forClass(EnrollmentApplication.class);
    verify(applicationRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getFormState().getFormVersion()).isSameAs(version);
    verify(version).requireAssignableTo(institutionId);
  }

  @Test
  @DisplayName("Should reject starting when the training path has no open offered courses")
  void startOrGetApplication_rejectsClosedCourses() {
    final var person = Person.builder().id(personId).build();
    final var path = Mockito.mock(TrainingPath.class);
    when(path.getId()).thenReturn(UUID.randomUUID());
    when(personRepository.findByIdAndInstitution_Id(personId, institutionId))
        .thenReturn(Optional.of(person));
    when(trainingPathRepository.findByIdAndInstitution_IdAndActiveTrueAndDeletedAtIsNull(
            path.getId(), institutionId))
        .thenReturn(Optional.of(path));
    when(applicationPeriods.hasOpenCourses(institutionId, path.getId(), academicYearId))
        .thenReturn(false);

    assertThatThrownBy(
            () ->
                service.startOrGetApplication(
                    institutionId,
                    personId,
                    new StartEnrollmentApplicationRequest(path.getId(), null, null)))
        .isInstanceOf(EnrollmentPeriodClosedException.class);
    verify(applicationRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("Should advance the draft revision and reject a stale revision")
  void updateDraft_rejectsStaleRevision() {
    Institution institution = Mockito.mock(Institution.class);

    EnrollmentApplication application =
        EnrollmentApplication.builder()
            .id(applicationId)
            .institution(institution)
            .applicantPerson(Mockito.mock(Person.class))
            .academicYear(Mockito.mock(AcademicYear.class))
            .enrollmentPeriod(
                EnrollmentPeriod.builder()
                    .id(periodId)
                    .institution(institution)
                    .academicYear(Mockito.mock(AcademicYear.class))
                    .build())
            .status(EnrollmentApplicationStatus.DRAFT)
            .build();

    when(applicationRepository.findAccessibleForUpdate(applicationId, personId))
        .thenReturn(Optional.of(application));
    when(applicationRepository.saveAndFlush(any(EnrollmentApplication.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    UpdateEnrollmentDraftRequest request =
        UpdateEnrollmentDraftRequest.builder()
            .data(
                EnrollmentDraftData.builder()
                    .academicBackground(
                        AcademicBackgroundDto.builder().secondarySchool("Colegio Nacional").build())
                    .build())
            .expectedRevision(0L)
            .build();

    assertThatCode(() -> service.updateDraft(personId, applicationId, request))
        .doesNotThrowAnyException();
    assertThat(application.getFormState().getDraftRevision()).isEqualTo(1);
    assertThatThrownBy(() -> service.updateDraft(personId, applicationId, request))
        .isInstanceOf(EnrollmentFormException.class)
        .hasMessage(EnrollmentFormMessages.DRAFT_REVISION_CONFLICT);
  }

  @Test
  @DisplayName("Should reuse an existing draft without clearing its academic year or period")
  void startOrGetApplication_reusesDraft() {
    final var path = givenAvailableTrainingPath();
    final var draft =
        EnrollmentApplication.builder()
            .id(applicationId)
            .institution(path.getInstitution())
            .applicantPerson(Person.builder().id(personId).build())
            .trainingPath(path)
            .academicYear(activeYear)
            .enrollmentPeriod(
                EnrollmentPeriod.builder()
                    .id(periodId)
                    .institution(path.getInstitution())
                    .academicYear(activeYear)
                    .build())
            .status(EnrollmentApplicationStatus.DRAFT)
            .build();
    when(applicationRepository.findDraftsForTrainingPathAndAcademicYear(
            institutionId, personId, path.getId(), academicYearId, Pageable.ofSize(1)))
        .thenReturn(List.of(draft));

    final var response =
        service.startOrGetApplication(
            institutionId,
            personId,
            new StartEnrollmentApplicationRequest(path.getId(), null, null));

    assertThat(response.getApplicationId()).isEqualTo(applicationId);
    assertThat(draft.getAcademicYear()).isSameAs(activeYear);
    assertThat(draft.getEnrollmentPeriod())
        .isNotNull()
        .extracting(period -> period.getId())
        .isEqualTo(periodId);
    verify(documentRequirements, never()).snapshot(any());
    verify(formAssignments, never())
        .findByInstitutionIdAndTrainingPathIdAndAcademicYearId(any(), any(), any());
    assertThat(draft.getFormState().getFormVersion()).isNull();
  }

  @Test
  void reusingAnInstitutionalFormDraftDoesNotResolveTheCurrentAssignment() {
    final var path = givenAvailableTrainingPath();
    final var version = Mockito.mock(EnrollmentFormVersion.class);
    when(version.getDefinition()).thenReturn(EnrollmentFormBaseDefinition.create());
    final var draft =
        EnrollmentApplication.builder()
            .institution(path.getInstitution())
            .applicantPerson(Person.builder().id(personId).build())
            .trainingPath(path)
            .academicYear(activeYear)
            .status(EnrollmentApplicationStatus.DRAFT)
            .build();
    draft.bindFormVersion(version);
    when(applicationRepository.findDraftsForTrainingPathAndAcademicYear(
            institutionId, personId, path.getId(), academicYearId, Pageable.ofSize(1)))
        .thenReturn(List.of(draft));

    service.startOrGetApplication(
        institutionId, personId, new StartEnrollmentApplicationRequest(path.getId(), null, null));

    assertThat(draft.getFormState().getFormVersion()).isSameAs(version);
    verify(formAssignments, never())
        .findByInstitutionIdAndTrainingPathIdAndAcademicYearId(any(), any(), any());
    verify(applicationRepository, never()).saveAndFlush(any());
  }

  private TrainingPath givenAvailableTrainingPath() {
    final var institution = Institution.builder().id(institutionId).build();
    final var person = Person.builder().id(personId).institution(institution).build();
    final var path = Mockito.mock(TrainingPath.class);
    when(path.getId()).thenReturn(UUID.randomUUID());
    when(path.getInstitution()).thenReturn(institution);
    when(path.getName()).thenReturn("Trayecto");
    when(personRepository.findByIdAndInstitution_Id(personId, institutionId))
        .thenReturn(Optional.of(person));
    when(trainingPathRepository.findByIdAndInstitution_IdAndActiveTrueAndDeletedAtIsNull(
            path.getId(), institutionId))
        .thenReturn(Optional.of(path));
    when(applicationPeriods.hasOpenCourses(institutionId, path.getId(), academicYearId))
        .thenReturn(true);
    return path;
  }

  @Test
  @DisplayName("Should ignore personalData and persist editable applicant sections")
  void updateDraft_success() {
    Person person = Mockito.mock(Person.class);
    when(person.getId()).thenReturn(personId);
    when(person.getFirstName()).thenReturn("Juan");
    when(person.getLastName()).thenReturn("Pérez");
    when(person.getDocumentNumber()).thenReturn("12345678");

    Institution institution = Mockito.mock(Institution.class);
    when(institution.getId()).thenReturn(institutionId);

    AcademicYear academicYear = Mockito.mock(AcademicYear.class);
    when(academicYear.getId()).thenReturn(academicYearId);

    EnrollmentPeriod period =
        EnrollmentPeriod.builder()
            .id(periodId)
            .institution(institution)
            .academicYear(academicYear)
            .build();

    EnrollmentApplication application =
        EnrollmentApplication.builder()
            .id(applicationId)
            .institution(institution)
            .applicantPerson(person)
            .academicYear(academicYear)
            .enrollmentPeriod(period)
            .status(EnrollmentApplicationStatus.DRAFT)
            .build();

    when(applicationRepository.findAccessibleForUpdate(applicationId, personId))
        .thenReturn(Optional.of(application));
    when(applicationRepository.saveAndFlush(any(EnrollmentApplication.class)))
        .thenReturn(application);

    UpdateEnrollmentDraftRequest request =
        UpdateEnrollmentDraftRequest.builder()
            .data(
                EnrollmentDraftData.builder()
                    .personalData(PersonalDataDto.builder().firstName("Mariano").build())
                    .academicBackground(
                        AcademicBackgroundDto.builder().secondarySchool("Colegio Nacional").build())
                    .build())
            .expectedRevision(0L)
            .build();

    service.updateDraft(personId, applicationId, request);

    assertThat(application.getApplicantPerson()).isSameAs(person);
    assertThat(requireNonNull(application.getEducationBackground()).getSecondarySchool())
        .isEqualTo("Colegio Nacional");
    verify(applicationRepository).saveAndFlush(application);
    verify(auditEventRecorder)
        .recordCurrentActor(
            eq(institution),
            eq(personId),
            eq(EnrollmentAuditAction.ENROLLMENT_APPLICATION_DRAFT_UPDATED),
            eq(EnrollmentApplication.AUDIT_TYPE),
            eq(applicationId),
            eq(ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin.SELF_SERVICE),
            any());
  }

  @Test
  @DisplayName("Should throw ApplicationNotEditableException when application status is not DRAFT")
  void updateDraft_notEditable() {
    Person person = Mockito.mock(Person.class);

    EnrollmentApplication application =
        EnrollmentApplication.builder()
            .id(applicationId)
            .institution(Institution.builder().id(institutionId).build())
            .applicantPerson(person)
            .status(EnrollmentApplicationStatus.SUBMITTED)
            .build();

    when(applicationRepository.findAccessibleForUpdate(applicationId, personId))
        .thenReturn(Optional.of(application));

    UpdateEnrollmentDraftRequest request =
        UpdateEnrollmentDraftRequest.builder()
            .data(
                EnrollmentDraftData.builder()
                    .personalData(PersonalDataDto.builder().firstName("Juan").build())
                    .build())
            .build();

    assertThatThrownBy(() -> service.updateDraft(personId, applicationId, request))
        .isInstanceOf(ApplicationNotEditableException.class);
  }

  @Test
  @DisplayName("Should successfully cancel draft application")
  void cancelApplication_success() {
    Person person = Mockito.mock(Person.class);
    when(person.getId()).thenReturn(personId);

    Institution institution = Mockito.mock(Institution.class);
    when(institution.getId()).thenReturn(institutionId);

    AcademicYear academicYear = Mockito.mock(AcademicYear.class);
    when(academicYear.getId()).thenReturn(academicYearId);

    EnrollmentPeriod period =
        EnrollmentPeriod.builder()
            .id(periodId)
            .institution(institution)
            .academicYear(academicYear)
            .build();

    EnrollmentApplication application =
        EnrollmentApplication.builder()
            .id(applicationId)
            .institution(institution)
            .applicantPerson(person)
            .academicYear(academicYear)
            .enrollmentPeriod(period)
            .status(EnrollmentApplicationStatus.DRAFT)
            .build();

    when(applicationRepository.findAccessibleForUpdate(applicationId, personId))
        .thenReturn(Optional.of(application));
    when(applicationRepository.save(any(EnrollmentApplication.class))).thenReturn(application);

    EnrollmentApplicationResponse response = service.cancelApplication(personId, applicationId);

    assertThat(response.getStatus()).isEqualTo(EnrollmentApplicationStatus.CANCELLED);
    verify(auditEventRecorder)
        .record(
            institution,
            personId,
            personId,
            EnrollmentAuditAction.ENROLLMENT_APPLICATION_CANCELLED,
            EnrollmentApplication.AUDIT_TYPE,
            applicationId);
    verify(applicationRepository).save(application);
  }

  @Test
  @DisplayName("Should throw ApplicationNotEditableException when cancelling non-draft application")
  void cancelApplication_notDraft() {
    Person person = Mockito.mock(Person.class);

    EnrollmentApplication application =
        EnrollmentApplication.builder()
            .id(applicationId)
            .institution(Institution.builder().id(institutionId).build())
            .applicantPerson(person)
            .status(EnrollmentApplicationStatus.SUBMITTED)
            .build();

    when(applicationRepository.findAccessibleForUpdate(applicationId, personId))
        .thenReturn(Optional.of(application));

    assertThatThrownBy(() -> service.cancelApplication(personId, applicationId))
        .isInstanceOf(ApplicationNotEditableException.class);
  }

  @Test
  @DisplayName("Should require at least one selected course to submit")
  void submitApplication_requiresSelectedCourses() {
    Person person = Mockito.mock(Person.class);
    when(person.getId()).thenReturn(personId);
    when(person.getBirthDate()).thenReturn(LocalDate.of(2000, 1, 1));
    when(person.getFirstName()).thenReturn("Juan");
    when(person.getLastName()).thenReturn("Pérez");
    when(person.getDocumentNumber()).thenReturn("12345678");
    ApplicantEducationBackground edu =
        ApplicantEducationBackground.builder().secondarySchool("Colegio San Martín").build();
    ApplicantPreference pref = ApplicantPreference.builder().preferredShift("TARDE").build();

    EnrollmentApplication application =
        EnrollmentApplication.builder()
            .id(applicationId)
            .institution(Institution.builder().id(institutionId).build())
            .applicantPerson(person)
            .educationBackground(edu)
            .preference(pref)
            .status(EnrollmentApplicationStatus.DRAFT)
            .build();

    when(applicationRepository.findAccessibleForUpdate(applicationId, personId))
        .thenReturn(Optional.of(application));

    assertThatThrownBy(() -> service.submitApplication(personId, applicationId, 0L))
        .isInstanceOf(EnrollmentValidationException.class)
        .asInstanceOf(InstanceOfAssertFactories.type(EnrollmentValidationException.class))
        .extracting(
            mappedEnrollmentValidationException ->
                mappedEnrollmentValidationException.fieldErrors())
        .satisfies(fieldErrors -> assertThat(fieldErrors).containsKey("courses"));
  }

  @Test
  @DisplayName(
      "Should throw EnrollmentValidationException reporting each missing field when mandatory"
          + " fields are missing upon submission")
  void submitApplication_missingFields() {
    Person person = Mockito.mock(Person.class);
    when(person.getId()).thenReturn(personId);
    when(person.getFirstName()).thenReturn("");
    when(person.getLastName()).thenReturn(null);
    when(person.getDocumentNumber()).thenReturn(null);
    when(person.getEmail()).thenReturn(null);

    EnrollmentApplication application =
        EnrollmentApplication.builder()
            .id(applicationId)
            .institution(Institution.builder().id(institutionId).build())
            .applicantPerson(person)
            .status(EnrollmentApplicationStatus.DRAFT)
            .build();

    when(applicationRepository.findAccessibleForUpdate(applicationId, personId))
        .thenReturn(Optional.of(application));

    assertThatThrownBy(() -> service.submitApplication(personId, applicationId, 0L))
        .isInstanceOf(EnrollmentValidationException.class)
        .asInstanceOf(InstanceOfAssertFactories.type(EnrollmentValidationException.class))
        .extracting(
            mappedEnrollmentValidationException ->
                mappedEnrollmentValidationException.fieldErrors())
        .satisfies(
            fieldErrors -> {
              assertThat(fieldErrors)
                  .containsKeys(
                      "personalData.firstName",
                      "personalData.lastName",
                      "personalData.documentNumber",
                      "personalData.birthDate",
                      "personalData.email");
              assertThat(fieldErrors)
                  .containsKeys(
                      "academicBackground.currentlyStudying", "preference.preferredShift");
            });
  }

  @Test
  @DisplayName(
      "Should throw EnrollmentValidationException when a minor applicant has no responsible/tutor"
          + " data")
  void submitApplication_minorWithoutResponsible_reportsError() {
    Person person = Mockito.mock(Person.class);
    when(person.getId()).thenReturn(UUID.randomUUID());
    when(person.getFirstName()).thenReturn("Juan");
    when(person.getLastName()).thenReturn("Pérez");
    when(person.getDocumentNumber()).thenReturn("12345678");
    when(person.getBirthDate()).thenReturn(LocalDate.of(2018, 1, 1));

    ApplicantEducationBackground edu =
        ApplicantEducationBackground.builder().secondarySchool("Colegio San Martín").build();
    ApplicantPreference pref = ApplicantPreference.builder().preferredShift("TARDE").build();

    EnrollmentApplication application =
        EnrollmentApplication.builder()
            .id(applicationId)
            .institution(Institution.builder().id(institutionId).build())
            .applicantPerson(person)
            .educationBackground(edu)
            .preference(pref)
            .status(EnrollmentApplicationStatus.DRAFT)
            .build();

    when(applicationRepository.findAccessibleForUpdate(applicationId, personId))
        .thenReturn(Optional.of(application));

    assertThatThrownBy(() -> service.submitApplication(personId, applicationId, 0L))
        .isInstanceOf(EnrollmentValidationException.class)
        .asInstanceOf(InstanceOfAssertFactories.type(EnrollmentValidationException.class))
        .extracting(
            mappedEnrollmentValidationException ->
                mappedEnrollmentValidationException.fieldErrors())
        .satisfies(fieldErrors -> assertThat(fieldErrors).containsKey("responsible"));
  }

  @Test
  @DisplayName("Should reject a minor who applies for themself")
  void submitApplication_selfServiceMinor_reportsError() {
    assertBirthDateError(
        personId, LocalDate.now(CLOCK).minusYears(16), EnrollmentMessages.APPLICANT_MUST_BE_ADULT);
  }

  @Test
  @DisplayName("Should not reject an adult applicant represented by a tutor because of their age")
  void submitApplication_tutorForAdult_doesNotReportBirthDateError() {
    Person person = Mockito.mock(Person.class);
    when(person.getId()).thenReturn(UUID.randomUUID());
    when(person.getBirthDate()).thenReturn(LocalDate.of(1990, 1, 1));

    EnrollmentApplication application =
        EnrollmentApplication.builder()
            .id(applicationId)
            .institution(Institution.builder().id(institutionId).build())
            .applicantPerson(person)
            .status(EnrollmentApplicationStatus.DRAFT)
            .build();
    when(applicationRepository.findAccessibleForUpdate(applicationId, personId))
        .thenReturn(Optional.of(application));

    assertThatThrownBy(() -> service.submitApplication(personId, applicationId, 0L))
        .isInstanceOf(EnrollmentValidationException.class)
        .asInstanceOf(InstanceOfAssertFactories.type(EnrollmentValidationException.class))
        .extracting(EnrollmentValidationException::fieldErrors)
        .satisfies(
            fieldErrors -> assertThat(fieldErrors).doesNotContainKey("personalData.birthDate"));
  }

  private void assertBirthDateError(
      final UUID applicantId, final LocalDate birthDate, final String expectedMessage) {
    Person person = Mockito.mock(Person.class);
    when(person.getId()).thenReturn(applicantId);
    when(person.getBirthDate()).thenReturn(birthDate);

    EnrollmentApplication application =
        EnrollmentApplication.builder()
            .id(applicationId)
            .institution(Institution.builder().id(institutionId).build())
            .applicantPerson(person)
            .status(EnrollmentApplicationStatus.DRAFT)
            .build();
    when(applicationRepository.findAccessibleForUpdate(applicationId, personId))
        .thenReturn(Optional.of(application));

    assertThatThrownBy(() -> service.submitApplication(personId, applicationId, 0L))
        .isInstanceOf(EnrollmentValidationException.class)
        .asInstanceOf(InstanceOfAssertFactories.type(EnrollmentValidationException.class))
        .extracting(EnrollmentValidationException::fieldErrors)
        .satisfies(
            fieldErrors ->
                assertThat(fieldErrors).containsEntry("personalData.birthDate", expectedMessage));
  }

  @Test
  @DisplayName("Should deny access to an application belonging to a different applicant")
  void getApplicationById_deniesForeignApplicant() {
    UUID otherPersonId = UUID.randomUUID();
    Person owner = Mockito.mock(Person.class);
    when(owner.getId()).thenReturn(personId);

    EnrollmentApplication application =
        EnrollmentApplication.builder()
            .id(applicationId)
            .applicantPerson(owner)
            .status(EnrollmentApplicationStatus.SUBMITTED)
            .build();

    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));

    assertThatThrownBy(() -> service.getApplicationById(otherPersonId, applicationId))
        .isInstanceOf(EnrollmentApplicationNotFoundException.class);
  }

  @Test
  @DisplayName("Should deny institutional access to an application from a different institution")
  void getApplicationById_deniesForeignInstitution() {
    UUID otherInstitutionId = UUID.randomUUID();
    Institution institution = Mockito.mock(Institution.class);
    when(institution.getId()).thenReturn(institutionId);

    Person owner = Mockito.mock(Person.class);

    EnrollmentApplication application =
        EnrollmentApplication.builder()
            .id(applicationId)
            .institution(institution)
            .applicantPerson(owner)
            .status(EnrollmentApplicationStatus.SUBMITTED)
            .build();

    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));

    assertThatThrownBy(() -> service.getApplicationById(otherInstitutionId, null, applicationId))
        .isInstanceOf(EnrollmentApplicationNotFoundException.class);
  }
}
