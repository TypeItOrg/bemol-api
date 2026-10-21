package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Shift;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.ShiftRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ListShiftsUseCaseTest {

  @Mock private ShiftRepository shiftRepository;
  @InjectMocks private ListShiftsUseCase useCase;

  @Test
  @DisplayName("Should list shifts paginated with a normalized search term")
  void listsShiftsWithNormalizedSearch() {
    final UUID institutionId = UUID.randomUUID();
    final var institution = Institution.builder().id(institutionId).build();
    final var shift = Shift.create(institution, "Turno mañana", null);
    final Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"));
    given(shiftRepository.findByFilters(institutionId, "mañana", Boolean.TRUE, false, pageable))
        .willReturn(new PageImpl<>(List.of(shift), pageable, 1));

    final var response = useCase.execute(institutionId, "  mañana ", true, false, pageable);

    assertThat(response.totalItems()).isEqualTo(1);
    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).name()).isEqualTo("Turno mañana");
  }

  @Test
  @DisplayName("Should default to listing non-deleted shifts")
  void defaultsToNonDeletedShifts() {
    final UUID institutionId = UUID.randomUUID();
    final Pageable pageable = PageRequest.of(0, 20);
    given(shiftRepository.findByFilters(institutionId, null, null, false, pageable))
        .willReturn(new PageImpl<>(List.of(), pageable, 0));

    final var response = useCase.execute(institutionId, null, null, pageable);

    assertThat(response.totalItems()).isZero();
    assertThat(response.items()).isEmpty();
  }
}
