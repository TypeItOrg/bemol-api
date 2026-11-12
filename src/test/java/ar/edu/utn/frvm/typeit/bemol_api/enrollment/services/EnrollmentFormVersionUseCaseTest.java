package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.audit.EnrollmentAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentForm;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormBaseDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormVersion;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormVersionStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentFormAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentFormRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentFormVersionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormDefinitionUpdateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormRevisionRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormVersionCreateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EnrollmentFormVersionUseCaseTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-06T21:00:00Z"), ZoneOffset.UTC);
  private final UUID institutionId = UUID.randomUUID();
  private final UUID formId = UUID.randomUUID();
  private final UUID versionId = UUID.randomUUID();
  private final EnrollmentFormRepository forms = mock(EnrollmentFormRepository.class);
  private final EnrollmentFormVersionRepository versions =
      mock(EnrollmentFormVersionRepository.class);
  private final EnrollmentFormAssignmentRepository assignments =
      mock(EnrollmentFormAssignmentRepository.class);
  private final EnrollmentInstitutionLock lock = mock(EnrollmentInstitutionLock.class);
  private final EnrollmentFormAudit audit = mock(EnrollmentFormAudit.class);
  private final EnrollmentFormVersionUseCase service =
      new EnrollmentFormVersionUseCase(forms, versions, assignments, lock, audit, CLOCK);

  @Test
  void cloningUsesTheNextNumberAndDoesNotChangeAnyAssignment() {
    final var form = form();
    final var source = EnrollmentFormVersion.draft(form, 1, EnrollmentFormBaseDefinition.create());
    source.publish(CLOCK.instant(), 0);
    when(forms.findByIdAndInstitution_Id(formId, institutionId)).thenReturn(Optional.of(form));
    when(versions.findByIdAndInstitution_Id(versionId, institutionId))
        .thenReturn(Optional.of(source));
    when(versions.highestVersionNumber(formId)).thenReturn(3);

    service.createDraft(institutionId, formId, new EnrollmentFormVersionCreateRequest(versionId));

    final var draft = ArgumentCaptor.forClass(EnrollmentFormVersion.class);
    verify(versions).saveAndFlush(draft.capture());
    assertThat(draft.getValue().getVersionNumber()).isEqualTo(4);
    assertThat(draft.getValue().getDefinition()).isSameAs(source.getDefinition());
    assertThat(source.getStatus()).isEqualTo(EnrollmentFormVersionStatus.PUBLISHED);
    verify(audit).version(draft.getValue(), EnrollmentAuditAction.ENROLLMENT_FORM_VERSION_CREATED);
    verifyNoInteractions(assignments);
  }

  @Test
  void existingDraftPreventsAnotherCloneBeforeWriting() {
    final var form = form();
    final var source = EnrollmentFormVersion.draft(form, 1, EnrollmentFormBaseDefinition.create());
    source.publish(CLOCK.instant(), 0);
    when(forms.findByIdAndInstitution_Id(formId, institutionId)).thenReturn(Optional.of(form));
    when(versions.findByIdAndInstitution_Id(versionId, institutionId))
        .thenReturn(Optional.of(source));
    when(versions.existsByForm_IdAndStatus(formId, EnrollmentFormVersionStatus.DRAFT))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                service.createDraft(
                    institutionId, formId, new EnrollmentFormVersionCreateRequest(versionId)))
        .isInstanceOf(EnrollmentFormException.class)
        .hasMessage(EnrollmentFormMessages.DRAFT_EXISTS);
    verify(versions, never()).saveAndFlush(any());
    verifyNoInteractions(audit);
  }

  @Test
  void archiveChecksAssignmentsBeforeMutatingTheVersion() {
    final var form = form();
    final var version = mock(EnrollmentFormVersion.class);
    when(version.getForm()).thenReturn(form);
    when(versions.findByIdAndInstitution_Id(versionId, institutionId))
        .thenReturn(Optional.of(version));
    when(assignments.existsByFormVersion_Id(versionId)).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.archive(
                    institutionId, formId, versionId, new EnrollmentFormRevisionRequest(0L)))
        .isInstanceOf(EnrollmentFormException.class)
        .hasMessage(EnrollmentFormMessages.VERSION_IN_USE);
    verify(version, never()).archive(any(), anyLong());
    verify(versions, never()).saveAndFlush(any());
    verifyNoInteractions(audit);
  }

  @Test
  void identicalDefinitionDoesNotWriteAndAStaleEditDoesNotChangeIt() {
    final var definition = EnrollmentFormBaseDefinition.create();
    final var version = EnrollmentFormVersion.draft(form(), 1, definition);
    when(versions.findByIdAndInstitution_Id(versionId, institutionId))
        .thenReturn(Optional.of(version));

    service.replace(
        institutionId,
        formId,
        versionId,
        new EnrollmentFormDefinitionUpdateRequest(definition, 0L));

    assertThatThrownBy(
            () ->
                service.replace(
                    institutionId,
                    formId,
                    versionId,
                    new EnrollmentFormDefinitionUpdateRequest(definition, 1L)))
        .isInstanceOf(EnrollmentFormException.class);
    assertThat(version.getDefinition()).isSameAs(definition);
    verify(versions, never()).saveAndFlush(any());
    verifyNoInteractions(audit);
  }

  private EnrollmentForm form() {
    final var form = mock(EnrollmentForm.class);
    when(form.getId()).thenReturn(formId);
    when(form.getInstitution()).thenReturn(Institution.builder().id(institutionId).build());
    return form;
  }
}
