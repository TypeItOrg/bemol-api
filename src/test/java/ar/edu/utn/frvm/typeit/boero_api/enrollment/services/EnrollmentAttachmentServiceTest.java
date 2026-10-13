package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.TrainingPathDocumentRequirementRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AuthorityResolver;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionalAuthoritySnapshot;
import ar.edu.utn.frvm.typeit.boero_api.common.storage.StorageService;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentAttachment;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentDocumentRequirement;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.DocumentRequirementLevel;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.DocumentReviewStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.DocumentVersionStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.ApplicationNotEditableException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.AttachmentNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentApplicationRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentAttachmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollmentAttachmentResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.support.EnrollmentDocumentTestData;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnrollmentAttachmentServiceTest {

  @Mock private EnrollmentApplicationRepository applicationRepository;
  @Mock private EnrollmentAttachmentRepository attachmentRepository;
  @Mock private StorageService storage;
  @Mock private EnrollmentDocumentAudit audit;
  @Mock private EnrollmentStorageJournal journal;
  @Mock private EnrollmentInstitutionLock institutionLock;
  @Mock private EntityManager entityManager;
  @Mock private EnrollmentApplicationPeriodService periods;
  @Mock private TrainingPathDocumentRequirementRepository definitions;
  @Mock private EnrollmentDocumentRequirement requirement;
  @Mock private AuthorizationService authorizationService;
  @Mock private AuthorityResolver authorityResolver;

  private EnrollmentAttachmentService service;

  private final UUID institutionId = UUID.randomUUID();
  private final UUID applicantPersonId = UUID.randomUUID();
  private final UUID applicationId = UUID.randomUUID();
  private final UUID attachmentId = UUID.randomUUID();

  private final UUID requirementId = UUID.randomUUID();
  private final UUID reservationId = UUID.randomUUID();
  private EnrollmentApplication application;

  @BeforeEach
  void setUp() {
    final var authorization =
        new EnrollmentDocumentAuthorization(
            authorityResolver, authorizationService, audit, applicationRepository);
    final var documents =
        new EnrollmentDocumentRequirementsService(
            definitions, attachmentRepository, authorization, periods);
    final var access =
        new EnrollmentDocumentAccess(
            applicationRepository, authorization, institutionLock, entityManager, documents);
    service =
        new EnrollmentAttachmentService(
            attachmentRepository,
            storage,
            authorization,
            audit,
            Clock.systemUTC(),
            documents,
            access,
            new UploadEnrollmentAttachmentUseCase(
                access, attachmentRepository, storage, authorization, audit, journal, documents));

    Institution institution = Mockito.mock(Institution.class);
    Mockito.lenient().when(institution.getId()).thenReturn(institutionId);

    Person applicant = Mockito.mock(Person.class);
    Mockito.lenient().when(applicant.getId()).thenReturn(applicantPersonId);

    application =
        EnrollmentApplication.builder()
            .id(applicationId)
            .institution(institution)
            .trainingPathId(UUID.randomUUID())
            .applicantPerson(applicant)
            .status(EnrollmentApplicationStatus.DRAFT)
            .build();
    application.addDocumentRequirement(requirement);
    when(requirement.getId()).thenReturn(requirementId);
    when(requirement.isActive()).thenReturn(true);
    when(requirement.getAllowedFormats()).thenReturn(List.of("application/pdf", "image/png"));
    when(requirement.getLevel()).thenReturn(DocumentRequirementLevel.AT_SUBMISSION);
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
    when(periods.isOpen(application)).thenReturn(true);
    when(journal.reserve(eq(institutionId), eq(applicationId), any(), any()))
        .thenReturn(reservationId);
  }

  private TestingAuthenticationToken authenticationFor(UUID personId) {
    JwtAuthenticatedUser principal =
        JwtAuthenticatedUser.builder()
            .userId(UUID.randomUUID())
            .personId(personId)
            .documentNumber("12345678")
            .institutionId(institutionId)
            .sessionId(UUID.randomUUID())
            .tokenId("jti")
            .build();
    return new TestingAuthenticationToken(principal, "", "ROLE_USER");
  }

  @Test
  @DisplayName("Should successfully upload attachment for draft application")
  void uploadAttachment_success() throws Exception {
    when(applicationRepository.findForAttachmentUpdate(applicationId, institutionId))
        .thenReturn(Optional.of(application));
    when(attachmentRepository
            .findByEnrollmentApplicationIdAndRequirementIdAndVersionStatusAndDeletedAtIsNull(
                applicationId, requirementId, DocumentVersionStatus.CURRENT))
        .thenReturn(Optional.empty());

    MockMultipartFile file = EnrollmentDocumentTestData.pdf("dni.pdf");

    EnrollmentAttachmentResponse response =
        service.uploadAttachment(
            applicationId, file, requirementId, authenticationFor(applicantPersonId));

    assertThat(response.requirementId()).isEqualTo(requirementId);
    assertThat(response.reviewStatus()).isEqualTo(DocumentReviewStatus.PENDING_REVIEW);
    assertThat(response.storagePath()).isNull();
    assertThat(response.originalFileName()).isEqualTo("dni.pdf");
    verify(attachmentRepository).saveAndFlush(any(EnrollmentAttachment.class));
    verify(journal).lockReservation(reservationId);
    verify(storage).write(any(), eq("application/pdf"), eq(file.getSize()), eq(file));
    verify(journal).confirm(eq(reservationId), any());
  }

  @Test
  @DisplayName("Should supersede an existing delivery of the same requirement and retain its file")
  void uploadAttachment_replaceExisting() throws Exception {
    when(applicationRepository.findForAttachmentUpdate(applicationId, institutionId))
        .thenReturn(Optional.of(application));

    EnrollmentAttachment existing =
        EnrollmentAttachment.builder()
            .id(UUID.randomUUID())
            .enrollmentApplication(application)
            .requirement(requirement)
            .originalFileName("old_dni.pdf")
            .storagePath("old_path.pdf")
            .contentType("application/pdf")
            .fileSize(10L)
            .build();

    when(attachmentRepository
            .findByEnrollmentApplicationIdAndRequirementIdAndVersionStatusAndDeletedAtIsNull(
                applicationId, requirementId, DocumentVersionStatus.CURRENT))
        .thenReturn(Optional.of(existing));

    MockMultipartFile file = EnrollmentDocumentTestData.pdf("dni.pdf");

    service.uploadAttachment(
        applicationId, file, requirementId, authenticationFor(applicantPersonId));

    assertThat(existing.getVersionStatus()).isEqualTo(DocumentVersionStatus.SUPERSEDED);
    assertThat(existing.isDeleted()).isFalse();
    verify(storage, never()).deletePhysicalFile("old_path.pdf");
  }

  @Test
  @DisplayName("Should reject upload when application is not editable")
  void uploadAttachment_notEditable() {
    application.submit();
    application.approve(java.time.Instant.parse("2026-09-26T12:00:00Z"), applicantPersonId);
    when(applicationRepository.findForAttachmentUpdate(applicationId, institutionId))
        .thenReturn(Optional.of(application));

    MockMultipartFile file =
        new MockMultipartFile("file", "dni.pdf", "application/pdf", "content".getBytes());

    assertThatThrownBy(
            () ->
                service.uploadAttachment(
                    applicationId, file, requirementId, authenticationFor(applicantPersonId)))
        .isInstanceOf(ApplicationNotEditableException.class);

    verifyNoInteractions(storage);
  }

  @Test
  @DisplayName("Should reject upload when caller is not owner and not administrative")
  void uploadAttachment_unauthorized() {
    when(applicationRepository.findForAttachmentUpdate(applicationId, institutionId))
        .thenReturn(Optional.of(application));
    UUID strangerId = UUID.randomUUID();
    when(authorityResolver.resolvePersonAuthorities(strangerId, institutionId))
        .thenReturn(new InstitutionalAuthoritySnapshot(Set.of(), List.of()));

    MockMultipartFile file =
        new MockMultipartFile("file", "dni.pdf", "application/pdf", "content".getBytes());

    assertThatThrownBy(
            () ->
                service.uploadAttachment(
                    applicationId, file, requirementId, authenticationFor(strangerId)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("Should reject upload when caller is not authenticated")
  void uploadAttachment_unauthenticated() {

    MockMultipartFile file =
        new MockMultipartFile("file", "dni.pdf", "application/pdf", "content".getBytes());

    assertThatThrownBy(() -> service.uploadAttachment(applicationId, file, requirementId, null))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("Should reject upload with a requirement outside the application")
  void uploadAttachment_invalidRequirement() {
    when(applicationRepository.findForAttachmentUpdate(applicationId, institutionId))
        .thenReturn(Optional.of(application));
    MockMultipartFile file =
        new MockMultipartFile("file", "dni.pdf", "application/pdf", "content".getBytes());

    assertThatThrownBy(
            () ->
                service.uploadAttachment(
                    applicationId, file, UUID.randomUUID(), authenticationFor(applicantPersonId)))
        .isInstanceOf(EnrollmentValidationException.class)
        .hasMessage(EnrollmentMessages.DOCUMENT_REQUIREMENT_NOT_FOUND);
  }

  @Test
  @DisplayName("Should retrieve attachment content for authorized applicant")
  void getAttachmentContent_success() {
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));

    EnrollmentAttachment attachment =
        EnrollmentAttachment.builder()
            .id(attachmentId)
            .enrollmentApplication(application)
            .requirement(requirement)
            .originalFileName("foto.png")
            .storagePath(applicationId + "/foto.png")
            .contentType("image/png")
            .fileSize(100L)
            .build();

    when(attachmentRepository.findByIdAndEnrollmentApplicationIdAndDeletedAtIsNull(
            attachmentId, applicationId))
        .thenReturn(Optional.of(attachment));

    Resource mockResource = new ByteArrayResource("image data".getBytes());
    when(storage.loadAsResource(attachment.getStoragePath())).thenReturn(mockResource);

    EnrollmentAttachmentService.AttachmentContentResult result =
        service.getAttachmentContent(
            applicationId, attachmentId, authenticationFor(applicantPersonId));

    assertThat(result.resource()).isEqualTo(mockResource);
    assertThat(result.attachment()).isEqualTo(attachment);
  }

  @Test
  @DisplayName("Should retrieve attachment content for administrative user")
  void getAttachmentContent_adminSuccess() {
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));

    UUID adminId = UUID.randomUUID();
    when(authorityResolver.resolvePersonAuthorities(adminId, institutionId))
        .thenReturn(
            new InstitutionalAuthoritySnapshot(
                Set.of(PermissionCode.ENROLLMENT_ATTACHMENT_READ), List.of("ADMINISTRATIVE")));

    EnrollmentAttachment attachment =
        EnrollmentAttachment.builder()
            .id(attachmentId)
            .enrollmentApplication(application)
            .requirement(requirement)
            .originalFileName("foto.png")
            .storagePath(applicationId + "/foto.png")
            .contentType("image/png")
            .fileSize(100L)
            .build();

    when(attachmentRepository.findByIdAndEnrollmentApplicationIdAndDeletedAtIsNull(
            attachmentId, applicationId))
        .thenReturn(Optional.of(attachment));

    Resource mockResource = new ByteArrayResource("image data".getBytes());
    when(storage.loadAsResource(attachment.getStoragePath())).thenReturn(mockResource);

    EnrollmentAttachmentService.AttachmentContentResult result =
        service.getAttachmentContent(applicationId, attachmentId, authenticationFor(adminId));

    assertThat(result.resource()).isEqualTo(mockResource);
  }

  @Test
  @DisplayName("Should throw AttachmentNotFoundException when attachment does not exist")
  void getAttachmentContent_notFound() {
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
    when(attachmentRepository.findByIdAndEnrollmentApplicationIdAndDeletedAtIsNull(
            attachmentId, applicationId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.getAttachmentContent(
                    applicationId, attachmentId, authenticationFor(applicantPersonId)))
        .isInstanceOf(AttachmentNotFoundException.class);
  }

  @Test
  @DisplayName("Should withdraw an attachment and retain its file")
  void deleteAttachment_success() {
    when(applicationRepository.findForAttachmentUpdate(applicationId, institutionId))
        .thenReturn(Optional.of(application));

    EnrollmentAttachment attachment =
        EnrollmentAttachment.builder()
            .id(attachmentId)
            .enrollmentApplication(application)
            .requirement(requirement)
            .originalFileName("cert.pdf")
            .storagePath(applicationId + "/cert.pdf")
            .contentType("application/pdf")
            .fileSize(500L)
            .build();

    when(attachmentRepository.findByIdAndEnrollmentApplicationIdAndDeletedAtIsNull(
            attachmentId, applicationId))
        .thenReturn(Optional.of(attachment));

    service.deleteAttachment(applicationId, attachmentId, authenticationFor(applicantPersonId));

    assertThat(attachment.getVersionStatus()).isEqualTo(DocumentVersionStatus.WITHDRAWN);
    assertThat(attachment.isDeleted()).isFalse();
    verify(attachmentRepository).save(attachment);
    verify(storage, never()).deletePhysicalFile(attachment.getStoragePath());
  }

  @Test
  @DisplayName("Should list active attachments for application")
  void listAttachments_success() {
    when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));

    EnrollmentAttachment attachment =
        EnrollmentAttachment.builder()
            .id(attachmentId)
            .enrollmentApplication(application)
            .requirement(requirement)
            .originalFileName("dni.pdf")
            .storagePath(applicationId + "/dni.pdf")
            .contentType("application/pdf")
            .fileSize(200L)
            .build();

    when(attachmentRepository.findByEnrollmentApplicationIdAndDeletedAtIsNull(applicationId))
        .thenReturn(List.of(attachment));

    List<EnrollmentAttachmentResponse> result =
        service.listAttachments(applicationId, authenticationFor(applicantPersonId));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().id()).isEqualTo(attachmentId);
  }
}
