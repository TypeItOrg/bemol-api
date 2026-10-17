package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionRoleProvisioner;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.CityNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.SlugAlreadyExistsException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.CityRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.InstitutionDetailResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.requests.CreateInstitutionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateInstitutionUseCase {

  private final CityRepository cityRepository;
  private final InstitutionRepository institutionRepository;
  private final InstitutionRoleProvisioner institutionRoleProvisioner;
  private final AuditEventRecorder auditEventRecorder;

  @Transactional
  public InstitutionDetailResponse execute(CreateInstitutionRequest request) {
    if (institutionRepository.existsBySlug(request.slug())) {
      throw new SlugAlreadyExistsException();
    }

    var city = cityRepository.findById(request.cityId()).orElseThrow(CityNotFoundException::new);

    Institution institution =
        Institution.builder()
            .name(request.name())
            .slug(request.slug())
            .city(city)
            .street(request.street())
            .number(request.number())
            .neighborhood(request.neighborhood())
            .additionalInfo(request.additionalInfo())
            .phoneNumber(request.phoneNumber())
            .email(request.email())
            .active(true)
            .build();

    Institution saved;
    try {
      saved = institutionRepository.save(institution);
      institutionRepository.flush();
      institutionRoleProvisioner.provision(saved);
    } catch (DataIntegrityViolationException exception) {
      throw new SlugAlreadyExistsException();
    }

    InstitutionDetailResponse response =
        institutionRepository
            .findWithLocationById(saved.getId())
            .map(InstitutionDetailResponse::from)
            .orElseThrow();

    auditEventRecorder.recordCurrentActor(
        saved,
        null,
        AuditAction.INSTITUTION_CREATED,
        AuditEntityType.INSTITUTION,
        saved.getId(),
        AuditOrigin.administration(),
        InstitutionAuditDetails.created(saved));

    log.info("[Institution] Created successfully, institutionId: {}", saved.getId());
    return response;
  }
}
