package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.common.storage.PresignedDownloadResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.storage.StorageFileNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.common.storage.StorageService;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.audit.InstitutionalAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionLogoNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InvalidInstitutionLogoException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.InstitutionDetailResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.PresignedInstitutionLogoUploadResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.requests.ConfirmInstitutionLogoUploadRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.requests.PresignInstitutionLogoUploadRequest;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstitutionLogoUseCase {
  private static final Duration UPLOAD_TTL = Duration.ofMinutes(10);
  private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(5);

  private final InstitutionRepository institutions;
  private final StorageService storage;
  private final AuditEventRecorder auditEventRecorder;

  @Transactional(readOnly = true)
  public PresignedInstitutionLogoUploadResponse presignUpload(
      final UUID id, final PresignInstitutionLogoUploadRequest request) {
    final var logo =
        InstitutionLogoPolicy.prepare(id, null, request.contentType(), request.fileSize());
    if (!institutions.existsById(id)) {
      throw new InstitutionNotFoundException();
    }
    final var presigned =
        storage.presignUpload(logo.key(), logo.contentType(), logo.size(), UPLOAD_TTL);

    return PresignedInstitutionLogoUploadResponse.from(logo.version(), presigned);
  }

  @Transactional
  public InstitutionDetailResponse replace(
      final UUID id, final ConfirmInstitutionLogoUploadRequest request) {
    final var logo =
        InstitutionLogoPolicy.prepare(
            id, request.version(), request.contentType(), request.fileSize());
    final var institution =
        institutions.findByIdForUpdate(id).orElseThrow(InstitutionNotFoundException::new);
    if (logo.key().equals(institution.getLogoKey())) {
      throw new InvalidInstitutionLogoException();
    }
    try {
      final var actual = storage.headObject(logo.key());
      InstitutionLogoPolicy.verifyUploadedObject(actual, logo.contentType(), logo.size());
    } catch (StorageFileNotFoundException exception) {
      throw new InvalidInstitutionLogoException();
    }

    final var before = institution.logoAuditSnapshot();
    cleanupAfterTransaction(logo.key(), institution.getLogoKey());
    institution.replaceLogo(logo.key(), logo.contentType(), logo.version(), logo.size());
    institutions.saveAndFlush(institution);
    recordLogoChange(institution, before);
    return InstitutionDetailResponse.from(institution);
  }

  @Transactional
  public void delete(final UUID id) {
    final var institution =
        institutions.findByIdForUpdate(id).orElseThrow(InstitutionNotFoundException::new);
    if (institution.getLogoKey() == null) {
      return;
    }
    final var before = institution.logoAuditSnapshot();
    cleanupAfterTransaction(null, institution.getLogoKey());
    institution.removeLogo();
    institutions.saveAndFlush(institution);
    recordLogoChange(institution, before);
  }

  // PostgreSQL FOR SHARE needs a writable transaction; the read changes no entity state.
  @Transactional
  public PresignedDownloadResponse get(final UUID id) {
    final var institution =
        institutions.findActiveByIdForShare(id).orElseThrow(InstitutionNotFoundException::new);
    final String key = institution.getLogoKey();
    if (key == null) {
      throw new InstitutionLogoNotFoundException();
    }
    return PresignedDownloadResponse.from(
        storage.presignDownload(
            key, requireNonNull(institution.getLogoContentType()), null, DOWNLOAD_TTL));
  }

  public @Nullable String presignLogoUrl(final Institution institution) {
    final String key = institution.getLogoKey();
    final String contentType = institution.getLogoContentType();
    if (key == null || contentType == null) {
      return null;
    }
    return storage.presignDownload(key, contentType, null, DOWNLOAD_TTL).url();
  }

  private void recordLogoChange(
      final Institution institution, final AuditSnapshot.Snapshot before) {
    auditEventRecorder.recordCurrentActor(
        institution,
        null,
        InstitutionalAuditAction.INSTITUTION_LOGO_CHANGED,
        Institution.AUDIT_TYPE,
        institution.getId(),
        AuditOrigin.administration(),
        AuditChangeDetails.change(institution.getName(), before, institution.logoAuditSnapshot()));
  }

  private void cleanupAfterTransaction(
      final @Nullable String newKey, final @Nullable String oldKey) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(final int status) {
            final String unused =
                status == STATUS_COMMITTED ? oldKey : status == STATUS_ROLLED_BACK ? newKey : null;
            if (unused != null) {
              try {
                storage.deletePhysicalFile(unused);
              } catch (RuntimeException exception) {
                log.warn("[InstitutionLogo] Storage cleanup failed, key: {}", unused);
              }
            }
          }
        });
  }
}
