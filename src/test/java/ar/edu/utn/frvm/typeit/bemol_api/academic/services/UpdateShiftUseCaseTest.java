package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verify;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Shift;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.ShiftNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.ShiftRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateShiftRequest;
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
class UpdateShiftUseCaseTest {
  @org.mockito.Mock private AuditEventRecorder auditAuditEventRecorder;

  @Mock private ShiftRepository shiftRepository;
  @InjectMocks private UpdateShiftUseCase useCase;

  @Test
  @DisplayName("Should update name and description of an existing shift")
  void updatesNameAndDescription() {
    final UUID institutionId = UUID.randomUUID();
    final UUID shiftId = UUID.randomUUID();
    final var institution = Institution.builder().id(institutionId).build();
    final var shift = Shift.create(institution, "Turno mañana", null);
    given(shiftRepository.findByIdAndInstitution_Id(shiftId, institutionId))
        .willReturn(Optional.of(shift));
    given(shiftRepository.existsByNormalizedNameAndIdNot(institutionId, "Turno tarde", shiftId))
        .willReturn(false);

    final var response =
        useCase.execute(
            institutionId, shiftId, new UpdateShiftRequest(" Turno tarde ", "De 13 a 17"));

    assertThat(response.name()).isEqualTo("Turno tarde");
    assertThat(response.description()).isEqualTo("De 13 a 17");
    verify(shiftRepository).flush();
  }

  @Test
  @DisplayName("Should reject renaming a shift to a name already used by another shift")
  void rejectsDuplicateNameExcludingSelf() {
    final UUID institutionId = UUID.randomUUID();
    final UUID shiftId = UUID.randomUUID();
    final var institution = Institution.builder().id(institutionId).build();
    final var shift = Shift.create(institution, "Turno mañana", null);
    given(shiftRepository.findByIdAndInstitution_Id(shiftId, institutionId))
        .willReturn(Optional.of(shift));
    given(shiftRepository.existsByNormalizedNameAndIdNot(institutionId, "Turno tarde", shiftId))
        .willReturn(true);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    institutionId, shiftId, new UpdateShiftRequest("Turno tarde", null)))
        .isInstanceOf(AcademicConflictException.class);

    then(shiftRepository).shouldHaveNoMoreInteractions();
  }

  @Test
  @DisplayName("Should reject updating a shift that does not exist in the institution")
  void rejectsUnknownShift() {
    final UUID institutionId = UUID.randomUUID();
    final UUID shiftId = UUID.randomUUID();
    given(shiftRepository.findByIdAndInstitution_Id(shiftId, institutionId))
        .willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                useCase.execute(
                    institutionId, shiftId, new UpdateShiftRequest("Turno tarde", null)))
        .isInstanceOf(ShiftNotFoundException.class);
  }
}
