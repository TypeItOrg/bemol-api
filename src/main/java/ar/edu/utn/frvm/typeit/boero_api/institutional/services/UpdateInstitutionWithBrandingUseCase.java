package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InvalidInstitutionLogoException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.InstitutionDetailResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.requests.UpdateInstitutionWithBrandingRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.requests.UpdateInstitutionWithBrandingRequest.LogoIntent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UpdateInstitutionWithBrandingUseCase {
  private final InstitutionRepository institutions;
  private final UpdateInstitutionUseCase updateInstitution;
  private final UpdateInstitutionPublicAccessUseCase publicAccess;
  private final InstitutionLogoUseCase logos;

  @Transactional
  public InstitutionDetailResponse execute(
      final UUID id,
      final UpdateInstitutionWithBrandingRequest request,
      final @Nullable MultipartFile file) {
    if ((request.logoIntent() == LogoIntent.REPLACE) != (file != null)) {
      throw new InvalidInstitutionLogoException();
    }

    institutions.findByIdForUpdate(id).orElseThrow(InstitutionNotFoundException::new);

    // All nested use cases join this transaction, including logo rollback cleanup.
    publicAccess.execute(id, request.publicSubdomain());
    switch (request.logoIntent()) {
      case REPLACE -> logos.replace(id, requireNonNull(file));
      case REMOVE -> logos.delete(id);
      case KEEP -> {}
    }

    // Status changes can revoke sessions; only reach them after branding has succeeded.
    return updateInstitution.execute(id, request.institution());
  }
}
