package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.InvalidFileException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentStorage;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentStorage.StoredFile;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardianAttachment;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.GuardianLinkAlreadyResolvedException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.GuardianLinkAttachmentNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.GuardianLinkNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonGuardianAttachmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianLinkAttachmentResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class GuardianLinkAttachmentServiceTest {

  @Mock private PersonGuardianRepository personGuardianRepository;
  @Mock private PersonGuardianAttachmentRepository attachmentRepository;
  @Mock private EnrollmentStorage storage;

  private GuardianLinkAttachmentService service;
  private Institution institution;
  private Person tutor;
  private PersonGuardian link;
  private final UUID linkId = UUID.randomUUID();
  private final MockMultipartFile file =
      new MockMultipartFile("file", "partida.pdf", "application/pdf", "%PDF-1.4".getBytes());

  @BeforeEach
  void setUp() {
    TransactionSynchronizationManager.initSynchronization();
    service =
        new GuardianLinkAttachmentService(personGuardianRepository, attachmentRepository, storage);
    institution = Institution.builder().id(UUID.randomUUID()).build();
    tutor = Person.builder().id(UUID.randomUUID()).institution(institution).build();
    link =
        PersonGuardian.request(
            institution,
            tutor,
            Person.builder().id(UUID.randomUUID()).institution(institution).build(),
            GuardianRelationship.MOTHER,
            true);
  }

  @AfterEach
  void tearDown() {
    TransactionSynchronizationManager.clearSynchronization();
  }

  @Test
  @DisplayName("Should store the file and attach it to the tutor's pending link")
  void upload_attachesFileToPendingLink() {
    when(personGuardianRepository.findForUpdate(linkId, institution.getId()))
        .thenReturn(Optional.of(link));
    when(attachmentRepository.countByPersonGuardian(link)).thenReturn(0L);
    when(storage.store(linkId, file))
        .thenReturn(new StoredFile("stored.pdf", linkId + "/stored.pdf", "application/pdf", 8));
    when(attachmentRepository.saveAndFlush(any(PersonGuardianAttachment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    final GuardianLinkAttachmentResponse response =
        service.upload(institution.getId(), tutor.getId(), linkId, file);

    assertThat(response.originalFileName()).isEqualTo("partida.pdf");
    assertThat(response.size()).isEqualTo(8);
    assertThat(response.contentType()).isEqualTo("application/pdf");
  }

  @Test
  @DisplayName("Should not reveal the link to a tutor who does not own it")
  void upload_failsForAnotherTutor() {
    when(personGuardianRepository.findForUpdate(linkId, institution.getId()))
        .thenReturn(Optional.of(link));

    assertThatThrownBy(() -> service.upload(institution.getId(), UUID.randomUUID(), linkId, file))
        .isInstanceOf(GuardianLinkNotFoundException.class);
    verifyNoInteractions(storage);
  }

  @Test
  @DisplayName("Should only accept documents while the link is pending")
  void upload_failsOnceResolved() {
    link.approve(Person.builder().id(UUID.randomUUID()).build());
    when(personGuardianRepository.findForUpdate(linkId, institution.getId()))
        .thenReturn(Optional.of(link));

    assertThatThrownBy(() -> service.upload(institution.getId(), tutor.getId(), linkId, file))
        .isInstanceOf(GuardianLinkAlreadyResolvedException.class);
    verifyNoInteractions(storage);
  }

  @Test
  @DisplayName("Should cap the number of documents per request")
  void upload_failsWhenLimitReached() {
    when(personGuardianRepository.findForUpdate(linkId, institution.getId()))
        .thenReturn(Optional.of(link));
    when(attachmentRepository.countByPersonGuardian(link))
        .thenReturn((long) GuardianLinkAttachmentService.MAX_ATTACHMENTS);

    assertThatThrownBy(() -> service.upload(institution.getId(), tutor.getId(), linkId, file))
        .isInstanceOf(InvalidFileException.class);
    verifyNoInteractions(storage);
  }

  @Test
  @DisplayName("Should delete the row and its stored file only while the link is pending")
  void delete_removesAttachmentOfPendingLink() {
    final PersonGuardianAttachment attachment = attachment();
    when(personGuardianRepository.findForUpdate(linkId, institution.getId()))
        .thenReturn(Optional.of(link));
    when(attachmentRepository.findByIdAndPersonGuardian(attachment.getId(), link))
        .thenReturn(Optional.of(attachment));

    service.delete(institution.getId(), tutor.getId(), linkId, attachment.getId());

    verify(attachmentRepository).delete(attachment);
  }

  @Test
  @DisplayName("Should let the institution list the documents without being the tutor")
  void list_allowsReviewerWithoutTutor() {
    when(personGuardianRepository.findByIdAndInstitution_Id(linkId, institution.getId()))
        .thenReturn(Optional.of(link));
    when(attachmentRepository.findByPersonGuardianOrderByCreatedAtAsc(link))
        .thenReturn(List.of(attachment()));

    assertThat(service.list(institution.getId(), null, linkId)).hasSize(1);
  }

  @Test
  @DisplayName("Should not list the documents to another tutor")
  void list_failsForAnotherTutor() {
    when(personGuardianRepository.findByIdAndInstitution_Id(linkId, institution.getId()))
        .thenReturn(Optional.of(link));

    assertThatThrownBy(() -> service.list(institution.getId(), UUID.randomUUID(), linkId))
        .isInstanceOf(GuardianLinkNotFoundException.class);
    verify(attachmentRepository, never()).findByPersonGuardianOrderByCreatedAtAsc(any());
  }

  @Test
  @DisplayName("Should serve the stored content of a document")
  void content_returnsStoredResource() {
    final PersonGuardianAttachment attachment = attachment();
    final ByteArrayResource resource = new ByteArrayResource("%PDF".getBytes());
    when(personGuardianRepository.findByIdAndInstitution_Id(linkId, institution.getId()))
        .thenReturn(Optional.of(link));
    when(attachmentRepository.findByIdAndPersonGuardian(attachment.getId(), link))
        .thenReturn(Optional.of(attachment));
    when(storage.loadAsResource(attachment.getStoragePath())).thenReturn(resource);

    final var content = service.content(institution.getId(), null, linkId, attachment.getId());

    assertThat(content.resource()).isSameAs(resource);
    assertThat(content.attachment()).isSameAs(attachment);
  }

  @Test
  @DisplayName("Should fail when the document does not belong to the link")
  void content_failsWhenAttachmentIsUnknown() {
    final UUID attachmentId = UUID.randomUUID();
    when(personGuardianRepository.findByIdAndInstitution_Id(linkId, institution.getId()))
        .thenReturn(Optional.of(link));
    when(attachmentRepository.findByIdAndPersonGuardian(attachmentId, link))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.content(institution.getId(), null, linkId, attachmentId))
        .isInstanceOf(GuardianLinkAttachmentNotFoundException.class);
  }

  private PersonGuardianAttachment attachment() {
    return PersonGuardianAttachment.builder()
        .id(UUID.randomUUID())
        .institution(institution)
        .personGuardian(link)
        .originalFileName("partida.pdf")
        .storagePath(linkId + "/stored.pdf")
        .contentType("application/pdf")
        .fileSize(8)
        .build();
  }
}
