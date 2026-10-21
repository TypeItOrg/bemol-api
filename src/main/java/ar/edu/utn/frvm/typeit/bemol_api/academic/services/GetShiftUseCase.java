package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.ShiftNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.ShiftRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.ShiftResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetShiftUseCase {
  private final ShiftRepository shiftRepository;

  @Transactional(readOnly = true)
  public ShiftResponse execute(final UUID institutionId, final UUID id) {
    return shiftRepository
        .findByIdAndInstitution_Id(id, institutionId)
        .map(ShiftResponse::from)
        .orElseThrow(ShiftNotFoundException::new);
  }
}
