package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.CityNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.SlugAlreadyExistsException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.CityRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.InstitutionDetailResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.requests.UpdateInstitutionRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateInstitutionUseCase {

  private final CityRepository cityRepository;
  private final InstitutionRepository institutionRepository;
  private final SessionRevocationService sessionRevocationService;
  private final AuditEventRecorder auditEventRecorder;

  @Transactional
  public InstitutionDetailResponse execute(UUID id, UpdateInstitutionRequest request) {
    var institution =
        institutionRepository
            .findWithLocationById(id)
            .orElseThrow(InstitutionNotFoundException::new);

    if (institutionRepository.existsBySlugAndIdNot(request.slug(), id)) {
      throw new SlugAlreadyExistsException();
    }

    var city = cityRepository.findById(request.cityId()).orElseThrow(CityNotFoundException::new);

    boolean deactivating = institution.isActive() && !request.active();

    final var before = InstitutionAuditDetails.snapshot(institution);

    institution.rename(request.name());
    institution.changeSlug(request.slug());
    institution.updateLocation(
        city, request.street(), request.number(), request.neighborhood(), request.additionalInfo());
    institution.updateContact(request.phoneNumber(), request.email());
    institution.updateStatus(request.active());

    try {
      institutionRepository.save(institution);
      institutionRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      throw new SlugAlreadyExistsException();
    }

    if (deactivating) {
      sessionRevocationService.revokeInstitutionalSessionsForInstitution(id);
    }

    final var after = InstitutionAuditDetails.snapshot(institution);
    if (!before.equals(after)) {
      auditEventRecorder.recordCurrentActor(
          institution,
          null,
          AuditAction.INSTITUTION_UPDATED,
          AuditEntityType.INSTITUTION,
          id,
          AuditOrigin.administration(),
          InstitutionAuditDetails.changed(before, after));
    }

    return InstitutionDetailResponse.from(institution);
  }
}
