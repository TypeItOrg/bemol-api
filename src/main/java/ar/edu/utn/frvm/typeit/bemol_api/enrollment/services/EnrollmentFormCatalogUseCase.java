package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import ar.edu.utn.frvm.typeit.bemol_api.common.search.SearchNormalization;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.audit.EnrollmentAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentForm;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormBaseDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormVersion;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentFormRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentFormVersionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormCreateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormRenameRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentFormCatalogUseCase {
  private final EnrollmentFormRepository forms;
  private final EnrollmentFormVersionRepository versions;
  private final InstitutionRepository institutions;
  private final EnrollmentInstitutionLock lock;
  private final EnrollmentFormAudit audit;

  @Transactional(readOnly = true)
  public Page<EnrollmentFormResponse> list(final UUID institutionId, final Pageable pageable) {
    return list(institutionId, null, pageable);
  }

  @Transactional(readOnly = true)
  public Page<EnrollmentFormResponse> list(
      final UUID institutionId, final @Nullable String search, final Pageable pageable) {
    requireInstitution(institutionId);
    final var normalizedSearch = SearchNormalization.normalizeSearch(search);
    final var pageRequest =
        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("name", "id"));

    final var result =
        normalizedSearch == null
            ? forms.findByInstitution_Id(institutionId, pageRequest)
            : forms.searchByInstitutionId(
                institutionId,
                SearchNormalization.likeContainsPattern(normalizedSearch),
                pageRequest);

    return result.map(EnrollmentFormResponse::from);
  }

  @Transactional(readOnly = true)
  public EnrollmentFormResponse get(final UUID institutionId, final UUID formId) {
    return EnrollmentFormResponse.from(form(institutionId, formId));
  }

  @Transactional
  public EnrollmentFormResponse create(
      final UUID institutionId, final EnrollmentFormCreateRequest request) {
    lock.lock(institutionId);
    final var institution =
        institutions.findById(institutionId).orElseThrow(InstitutionNotFoundException::new);
    final var form = EnrollmentForm.create(institution, request.name());
    forms.saveAndFlush(form);
    final var version = EnrollmentFormVersion.draft(form, 1, EnrollmentFormBaseDefinition.create());
    versions.saveAndFlush(version);
    audit.form(form, EnrollmentAuditAction.ENROLLMENT_FORM_CREATED);
    audit.version(version, EnrollmentAuditAction.ENROLLMENT_FORM_VERSION_CREATED);

    return EnrollmentFormResponse.from(form);
  }

  @Transactional
  public EnrollmentFormResponse rename(
      final UUID institutionId, final UUID formId, final EnrollmentFormRenameRequest request) {
    lock.lock(institutionId);
    final var form = form(institutionId, formId);
    form.requireRevision(request.expectedRevision());
    final var previousName = form.getName();
    form.rename(request.name());
    if (!previousName.equals(form.getName())) {
      forms.saveAndFlush(form);
      audit.form(form, EnrollmentAuditAction.ENROLLMENT_FORM_UPDATED);
    }

    return EnrollmentFormResponse.from(form);
  }

  @Transactional(readOnly = true)
  public EnrollmentFormDefinition base(final UUID institutionId) {
    requireInstitution(institutionId);
    return EnrollmentFormBaseDefinition.create();
  }

  @Transactional(readOnly = true)
  public EnrollmentFormDefinition preview(
      final UUID institutionId, final EnrollmentFormDefinition definition) {
    requireInstitution(institutionId);
    definition.validateForPublication();
    return definition;
  }

  private EnrollmentForm form(final UUID institutionId, final UUID id) {
    return forms
        .findByIdAndInstitution_Id(id, institutionId)
        .orElseThrow(
            () ->
                new EnrollmentFormException(
                    ErrorCategory.NOT_FOUND, EnrollmentFormMessages.FORM_NOT_FOUND));
  }

  private void requireInstitution(final UUID institutionId) {
    if (!institutions.existsById(institutionId)) {
      throw new InstitutionNotFoundException();
    }
  }
}
