package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.InstrumentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.InstrumentResponse;
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
public class ListInstrumentsUseCase {
  private final InstrumentRepository instrumentRepository;

  @Transactional(readOnly = true)
  public PaginatedResponse<InstrumentResponse> execute(
      final @Nullable UUID institutionId,
      final @Nullable String search,
      final @Nullable Boolean active,
      final boolean deleted,
      final Pageable pageable) {
    return PaginatedResponse.from(
        instrumentRepository
            .findByFilters(
                institutionId, AcademicNameNormalizer.search(search), active, deleted, pageable)
            .map(InstrumentResponse::from));
  }

  public PaginatedResponse<InstrumentResponse> execute(
      final @Nullable UUID institutionId,
      final @Nullable String search,
      final @Nullable Boolean active,
      final Pageable pageable) {
    return execute(institutionId, search, active, false, pageable);
  }
}
