package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verify;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Shift;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.ShiftNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.ShiftRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.ActiveStatusRequest;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
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
class UpdateShiftStatusUseCaseTest {
  @org.mockito.Mock private AuditEventRecorder auditAuditEventRecorder;

  @Mock private ShiftRepository shiftRepository;
  @InjectMocks private UpdateShiftStatusUseCase useCase;

  @Test
  @DisplayName("Should deactivate an existing shift")
  void deactivatesExistingShift() {
    final UUID institutionId = UUID.randomUUID();
    final UUID shiftId = UUID.randomUUID();
    final var institution = Institution.builder().id(institutionId).build();
    final var shift = Shift.create(institution, "Turno mañana", null);
    given(shiftRepository.findByIdAndInstitution_Id(shiftId, institutionId))
        .willReturn(Optional.of(shift));

    useCase.execute(institutionId, shiftId, new ActiveStatusRequest(false));

    assertThat(shift.isActive()).isFalse();
    verify(shiftRepository).flush();
  }

  @Test
  @DisplayName("Should reject changing the status of a shift that does not exist")
  void rejectsUnknownShift() {
    final UUID institutionId = UUID.randomUUID();
    final UUID shiftId = UUID.randomUUID();
    given(shiftRepository.findByIdAndInstitution_Id(shiftId, institutionId))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(institutionId, shiftId, new ActiveStatusRequest(true)))
        .isInstanceOf(ShiftNotFoundException.class);

    then(shiftRepository).shouldHaveNoMoreInteractions();
  }
}
