package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InvalidPublicSubdomainException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PublicSubdomainAlreadyExistsException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.InstitutionDetailResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.validation.PublicSubdomainPolicy;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateInstitutionPublicAccessUseCase {
  private final InstitutionRepository institutions;
  private final AuditEventRecorder auditEventRecorder;

  @Transactional
  public InstitutionDetailResponse execute(final UUID id, final @Nullable String name) {
    final var institution =
        institutions.findByIdForUpdate(id).orElseThrow(InstitutionNotFoundException::new);
    PublicSubdomainPolicy.validate(name);
    if (name != null && institutions.existsByPublicSubdomainAndIdNot(name, id)) {
      throw new PublicSubdomainAlreadyExistsException();
    }
    final var before = InstitutionAuditDetails.publicAccess(institution);
    institution.changePublicSubdomain(name);
    try {
      institutions.saveAndFlush(institution);
    } catch (final DataIntegrityViolationException exception) {
      for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
        if (cause instanceof ConstraintViolationException violation) {
          final String constraint = violation.getConstraintName();
          if ("institutions_public_subdomain_unique".equals(constraint)) {
            throw new PublicSubdomainAlreadyExistsException();
          }
          if ("institutions_public_subdomain_dns_check".equals(constraint)
              || "institutions_public_subdomain_reserved_check".equals(constraint)) {
            throw new InvalidPublicSubdomainException();
          }
        }
      }
      throw exception;
    }
    final var after = InstitutionAuditDetails.publicAccess(institution);
    if (!before.equals(after)) {
      auditEventRecorder.recordCurrentActor(
          institution,
          null,
          AuditAction.INSTITUTION_PUBLIC_ACCESS_CHANGED,
          AuditEntityType.INSTITUTION,
          id,
          AuditOrigin.administration(),
          Map.of("entityName", institution.getName(), "before", before, "after", after));
    }
    return InstitutionDetailResponse.from(institution);
  }
}
