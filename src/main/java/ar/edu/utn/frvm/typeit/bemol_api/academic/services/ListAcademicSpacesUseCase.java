package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicSpaceRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicSpaceResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.validation.AcademicNameNormalizer;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListAcademicSpacesUseCase {
  private final AcademicSpaceRepository academicSpaceRepository;

  @Transactional(readOnly = true)
  public PaginatedResponse<AcademicSpaceResponse> execute(
      final @Nullable UUID institutionId,
      final @Nullable String search,
      final @Nullable Boolean active,
      final @Nullable AcademicSpaceType type,
      final @Nullable AcademicSpaceFormat format,
      final boolean deleted,
      final Pageable pageable) {
    return PaginatedResponse.from(
        academicSpaceRepository
            .findByFilters(
                institutionId,
                AcademicNameNormalizer.search(search),
                active,
                type,
                format,
                deleted,
                pageable)
            .map(AcademicSpaceResponse::from));
  }

  public PaginatedResponse<AcademicSpaceResponse> execute(
      final @Nullable UUID institutionId,
      final @Nullable String search,
      final @Nullable Boolean active,
      final @Nullable AcademicSpaceType type,
      final Pageable pageable) {
    return execute(institutionId, search, active, type, null, false, pageable);
  }
}
