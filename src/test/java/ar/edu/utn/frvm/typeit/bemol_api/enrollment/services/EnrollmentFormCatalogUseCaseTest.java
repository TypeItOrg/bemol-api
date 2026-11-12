package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.audit.EnrollmentAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentForm;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormBaseDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormVersion;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormNativeBlock;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentFormRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentFormVersionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormCreateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormRenameRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class EnrollmentFormCatalogUseCaseTest {
  private final UUID institutionId = UUID.randomUUID();
  private final UUID formId = UUID.randomUUID();
  private final Institution institution = Institution.builder().id(institutionId).build();
  private final EnrollmentFormRepository forms = mock(EnrollmentFormRepository.class);
  private final EnrollmentFormVersionRepository versions =
      mock(EnrollmentFormVersionRepository.class);
  private final InstitutionRepository institutions = mock(InstitutionRepository.class);
  private final EnrollmentInstitutionLock lock = mock(EnrollmentInstitutionLock.class);
  private final EnrollmentFormAudit audit = mock(EnrollmentFormAudit.class);
  private final EnrollmentFormCatalogUseCase service =
      new EnrollmentFormCatalogUseCase(forms, versions, institutions, lock, audit);

  @Test
  void baseAndPreviewAreStableAndDoNotPersistAnything() {
    when(institutions.existsById(institutionId)).thenReturn(true);

    final var base = service.base(institutionId);

    assertThat(service.base(institutionId)).isEqualTo(base);
    assertThat(service.preview(institutionId, base)).isSameAs(base);
    assertThat(base.sections())
        .extracting(section -> section.nativeBlock())
        .contains(EnrollmentFormNativeBlock.SCHOOLING, EnrollmentFormNativeBlock.PREFERENCES);
    verifyNoInteractions(forms, versions, lock, audit);
  }

  @Test
  void creationLocksThenPersistsTheModelAndItsInitialBaseDraft() {
    when(institutions.findById(institutionId)).thenReturn(Optional.of(institution));

    final var response =
        service.create(institutionId, new EnrollmentFormCreateRequest("  Talleres  "));

    final var draft = ArgumentCaptor.forClass(EnrollmentFormVersion.class);
    final var ordered = inOrder(lock, forms, versions, audit);
    ordered.verify(lock).lock(institutionId);
    ordered.verify(forms).saveAndFlush(any(EnrollmentForm.class));
    ordered.verify(versions).saveAndFlush(draft.capture());
    ordered
        .verify(audit)
        .form(draft.getValue().getForm(), EnrollmentAuditAction.ENROLLMENT_FORM_CREATED);
    ordered
        .verify(audit)
        .version(draft.getValue(), EnrollmentAuditAction.ENROLLMENT_FORM_VERSION_CREATED);
    assertThat(draft.getValue().getVersionNumber()).isEqualTo(1);
    assertThat(draft.getValue().getDefinition()).isEqualTo(EnrollmentFormBaseDefinition.create());
    assertThat(response.name()).isEqualTo("Talleres");
  }

  @Test
  void identicalRenameDoesNotWriteAndStaleRenameDoesNotMutate() {
    final var form = EnrollmentForm.create(institution, "Talleres");
    when(forms.findByIdAndInstitution_Id(formId, institutionId)).thenReturn(Optional.of(form));

    service.rename(institutionId, formId, new EnrollmentFormRenameRequest(" Talleres ", 0L));

    verify(forms, never()).saveAndFlush(any());
    verifyNoInteractions(audit);
    assertThatThrownBy(
            () ->
                service.rename(institutionId, formId, new EnrollmentFormRenameRequest("Otro", 1L)))
        .isInstanceOf(EnrollmentFormException.class);
    assertThat(form.getName()).isEqualTo("Talleres");
  }

  @Test
  void listingAnUnknownInstitutionDoesNotReturnAnEmptySuccessfulCatalog() {
    assertThatThrownBy(() -> service.list(institutionId, Pageable.ofSize(10)))
        .isInstanceOf(InstitutionNotFoundException.class);
    verifyNoInteractions(forms);
  }
}
