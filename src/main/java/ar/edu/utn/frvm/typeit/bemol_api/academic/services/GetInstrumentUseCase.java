package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.InstrumentNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.InstrumentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.InstrumentResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetInstrumentUseCase {
  private final InstrumentRepository instrumentRepository;

  @Transactional(readOnly = true)
  public InstrumentResponse execute(final UUID institutionId, final UUID id) {
    return instrumentRepository
        .findByIdAndInstitution_Id(id, institutionId)
        .map(InstrumentResponse::from)
        .orElseThrow(InstrumentNotFoundException::new);
  }
}
