package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Shift;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.ShiftNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.ShiftRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetShiftUseCaseTest {

  @Mock private ShiftRepository shiftRepository;
  @InjectMocks private GetShiftUseCase useCase;

  @Test
  @DisplayName("Should return a shift belonging to the institution")
  void returnsShiftById() {
    final UUID institutionId = UUID.randomUUID();
    final UUID shiftId = UUID.randomUUID();
    final var institution = Institution.builder().id(institutionId).build();
    final var shift = Shift.create(institution, "Turno mañana", "De 8 a 12");
    given(shiftRepository.findByIdAndInstitution_Id(shiftId, institutionId))
        .willReturn(Optional.of(shift));

    final var response = useCase.execute(institutionId, shiftId);

    assertThat(response.id()).isEqualTo(shift.getId());
    assertThat(response.name()).isEqualTo("Turno mañana");
    assertThat(response.description()).isEqualTo("De 8 a 12");
  }

  @Test
  @DisplayName("Should reject getting a shift that does not exist in the institution")
  void rejectsUnknownShift() {
    final UUID institutionId = UUID.randomUUID();
    final UUID shiftId = UUID.randomUUID();
    given(shiftRepository.findByIdAndInstitution_Id(shiftId, institutionId))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(institutionId, shiftId))
        .isInstanceOf(ShiftNotFoundException.class);
  }
}
