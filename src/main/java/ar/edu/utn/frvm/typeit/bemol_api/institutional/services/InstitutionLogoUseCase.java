package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.common.storage.StorageFileNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.common.storage.StorageService;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionLogoNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.InstitutionDetailResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstitutionLogoUseCase {
  public record Logo(Resource resource, String contentType, long size, String version) {}

  private final InstitutionRepository institutions;
  private final StorageService storage;
  private final AuditEventRecorder auditEventRecorder;

  @Transactional
  public InstitutionDetailResponse replace(final UUID id, final MultipartFile file) {
    final var image = InstitutionLogoPolicy.prepare(file);
    final var institution =
        institutions.findByIdForUpdate(id).orElseThrow(InstitutionNotFoundException::new);
    final var before = InstitutionAuditDetails.logo(institution);
    final String version = UUID.randomUUID().toString();
    final String key =
        "institutions/"
            + id
            + "/logos/"
            + version
            + (image.contentType().equals("image/png") ? ".png" : ".jpg");
    cleanupAfterTransaction(key, institution.getLogoKey());
    storage.write(
        key,
        image.contentType(),
        image.bytes().length,
        () -> new ByteArrayInputStream(image.bytes()));
    institution.replaceLogo(key, image.contentType(), version, image.bytes().length);
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
    final var before = InstitutionAuditDetails.logo(institution);
    cleanupAfterTransaction(null, institution.getLogoKey());
    institution.removeLogo();
    institutions.saveAndFlush(institution);
    recordLogoChange(institution, before);
  }

  // PostgreSQL FOR SHARE needs a writable transaction; the read changes no entity state.
  @Transactional
  public Logo get(final UUID id) {
    final var institution =
        institutions.findActiveByIdForShare(id).orElseThrow(InstitutionNotFoundException::new);
    final String key = institution.getLogoKey();
    if (key == null) {
      throw new InstitutionLogoNotFoundException();
    }
    // Snapshot bytes before releasing the shared lock: replacement cleanup cannot unlink
    // a resource that the HTTP response has yet to open.
    try (final var input = storage.loadAsInputStream(key)) {
      final byte[] bytes = input.readNBytes((int) InstitutionLogoPolicy.MAX_BYTES + 1);
      if (bytes.length != requireNonNull(institution.getLogoSize())) {
        throw new InstitutionLogoNotFoundException();
      }
      return new Logo(
          new ByteArrayResource(bytes),
          requireNonNull(institution.getLogoContentType()),
          bytes.length,
          requireNonNull(institution.getLogoVersion()));
    } catch (StorageFileNotFoundException exception) {
      throw new InstitutionLogoNotFoundException();
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private void recordLogoChange(final Institution institution, final Map<String, Object> before) {
    auditEventRecorder.recordCurrentActor(
        institution,
        null,
        AuditAction.INSTITUTION_LOGO_CHANGED,
        AuditEntityType.INSTITUTION,
        institution.getId(),
        AuditOrigin.administration(),
        Map.of(
            "entityName", institution.getName(),
            "before", before,
            "after", InstitutionAuditDetails.logo(institution)));
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
