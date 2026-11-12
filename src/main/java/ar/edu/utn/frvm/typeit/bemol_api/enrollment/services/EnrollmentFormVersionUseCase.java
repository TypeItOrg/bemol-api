package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import ar.edu.utn.frvm.typeit.bemol_api.common.search.SearchNormalization;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.audit.EnrollmentAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentForm;
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
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormVersionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormVersionSummaryResponse;
import java.time.Clock;
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
public class EnrollmentFormVersionUseCase {
  private final EnrollmentFormRepository forms;
  private final EnrollmentFormVersionRepository versions;
  private final EnrollmentFormAssignmentRepository assignments;
  private final EnrollmentInstitutionLock lock;
  private final EnrollmentFormAudit audit;
  private final Clock clock;

  @Transactional(readOnly = true)
  public Page<EnrollmentFormVersionSummaryResponse> list(
      final UUID institutionId, final UUID formId, final Pageable pageable) {
    return list(institutionId, formId, null, pageable);
  }

  @Transactional(readOnly = true)
  public Page<EnrollmentFormVersionSummaryResponse> list(
      final UUID institutionId,
      final UUID formId,
      final @Nullable String search,
      final Pageable pageable) {
    form(institutionId, formId);
    final var normalizedSearch = SearchNormalization.normalizeSearch(search);
    final var pageRequest =
        PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            Sort.by(Sort.Order.desc("versionNumber"), Sort.Order.asc("id")));

    if (normalizedSearch == null) {
      return versions.findSummaries(institutionId, formId, pageRequest);
    }

    return versions.searchSummaries(
        institutionId,
        formId,
        SearchNormalization.likeContainsPattern(normalizedSearch),
        pageRequest);
  }

  @Transactional(readOnly = true)
  public EnrollmentFormVersionResponse get(
      final UUID institutionId, final UUID formId, final UUID versionId) {
    return EnrollmentFormVersionResponse.from(version(institutionId, formId, versionId));
  }

  @Transactional
  public EnrollmentFormVersionResponse createDraft(
      final UUID institutionId,
      final UUID formId,
      final EnrollmentFormVersionCreateRequest request) {
    lock.lock(institutionId);
    final var form = form(institutionId, formId);
    final var source = version(institutionId, formId, request.sourceVersionId());
    if (source.getStatus() == EnrollmentFormVersionStatus.DRAFT) {
      throw new EnrollmentFormException(
          ErrorCategory.CONFLICT, EnrollmentFormMessages.SOURCE_VERSION_INVALID);
    }
    if (versions.existsByForm_IdAndStatus(formId, EnrollmentFormVersionStatus.DRAFT)) {
      throw new EnrollmentFormException(
          ErrorCategory.CONFLICT, EnrollmentFormMessages.DRAFT_EXISTS);
    }

    final var draft =
        EnrollmentFormVersion.draft(
            form, versions.highestVersionNumber(formId) + 1, source.getDefinition());
    versions.saveAndFlush(draft);
    audit.version(draft, EnrollmentAuditAction.ENROLLMENT_FORM_VERSION_CREATED);
    return EnrollmentFormVersionResponse.from(draft);
  }

  @Transactional
  public EnrollmentFormVersionResponse replace(
      final UUID institutionId,
      final UUID formId,
      final UUID versionId,
      final EnrollmentFormDefinitionUpdateRequest request) {
    lock.lock(institutionId);
    final var version = version(institutionId, formId, versionId);
    final var previous = version.getDefinition();
    version.replaceDefinition(request.definition(), request.expectedRevision());
    if (!previous.equals(version.getDefinition())) {
      versions.saveAndFlush(version);
      audit.version(version, EnrollmentAuditAction.ENROLLMENT_FORM_VERSION_UPDATED);
    }

    return EnrollmentFormVersionResponse.from(version);
  }

  @Transactional
  public EnrollmentFormVersionResponse publish(
      final UUID institutionId,
      final UUID formId,
      final UUID versionId,
      final EnrollmentFormRevisionRequest request) {
    lock.lock(institutionId);
    final var version = version(institutionId, formId, versionId);
    version.publish(clock.instant(), request.expectedRevision());
    versions.saveAndFlush(version);
    audit.version(version, EnrollmentAuditAction.ENROLLMENT_FORM_VERSION_PUBLISHED);
    return EnrollmentFormVersionResponse.from(version);
  }

  @Transactional
  public EnrollmentFormVersionResponse archive(
      final UUID institutionId,
      final UUID formId,
      final UUID versionId,
      final EnrollmentFormRevisionRequest request) {
    lock.lock(institutionId);
    final var version = version(institutionId, formId, versionId);
    if (assignments.existsByFormVersion_Id(versionId)) {
      throw new EnrollmentFormException(
          ErrorCategory.CONFLICT, EnrollmentFormMessages.VERSION_IN_USE);
    }

    version.archive(clock.instant(), request.expectedRevision());
    versions.saveAndFlush(version);
    audit.version(version, EnrollmentAuditAction.ENROLLMENT_FORM_VERSION_ARCHIVED);
    return EnrollmentFormVersionResponse.from(version);
  }

  private EnrollmentForm form(final UUID institutionId, final UUID id) {
    return forms
        .findByIdAndInstitution_Id(id, institutionId)
        .orElseThrow(
            () ->
                new EnrollmentFormException(
                    ErrorCategory.NOT_FOUND, EnrollmentFormMessages.FORM_NOT_FOUND));
  }

  private EnrollmentFormVersion version(
      final UUID institutionId, final UUID formId, final UUID versionId) {
    return versions
        .findByIdAndInstitution_Id(versionId, institutionId)
        .filter(value -> value.getForm().getId().equals(formId))
        .orElseThrow(
            () ->
                new EnrollmentFormException(
                    ErrorCategory.NOT_FOUND, EnrollmentFormMessages.VERSION_NOT_FOUND));
  }
}
