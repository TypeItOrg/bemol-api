package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Shift;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.ShiftRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CreateShiftRequest;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateShiftUseCaseTest {
  @org.mockito.Mock private AuditEventRecorder auditAuditEventRecorder;

  @Mock private ShiftRepository shiftRepository;

  @Mock
  private ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository
      institutionRepository;

  @InjectMocks private CreateShiftUseCase useCase;

  @Test
  @DisplayName("Should create an active shift with a normalized name")
  void createsActiveShiftWithNormalizedName() {
    final UUID institutionId = UUID.randomUUID();
    final var institution = Institution.builder().id(institutionId).build();
    given(institutionRepository.findById(institutionId)).willReturn(Optional.of(institution));
    given(shiftRepository.existsByNormalizedName(institutionId, "Turno mañana")).willReturn(false);
    given(shiftRepository.save(any(Shift.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    final var response =
        useCase.execute(institutionId, new CreateShiftRequest("  Turno   mañana ", null));

    assertThat(response.name()).isEqualTo("Turno mañana");
    assertThat(response.active()).isTrue();
    assertThat(response.institutionId()).isEqualTo(institutionId);
    then(shiftRepository).should().flush();
  }

  @Test
  @DisplayName("Should reject creating a shift with a name already used in the institution")
  void rejectsDuplicateNameWithinInstitution() {
    final UUID institutionId = UUID.randomUUID();
    final var institution = Institution.builder().id(institutionId).build();
    given(institutionRepository.findById(institutionId)).willReturn(Optional.of(institution));
    given(shiftRepository.existsByNormalizedName(institutionId, "Turno mañana")).willReturn(true);

    assertThatThrownBy(
            () -> useCase.execute(institutionId, new CreateShiftRequest("Turno mañana", null)))
        .isInstanceOf(AcademicConflictException.class);

    then(shiftRepository).shouldHaveNoMoreInteractions();
  }

  @Test
  @DisplayName("Should reject creating a shift for an institution that does not exist")
  void rejectsUnknownInstitution() {
    final UUID institutionId = UUID.randomUUID();
    given(institutionRepository.findById(institutionId)).willReturn(Optional.empty());

    assertThatThrownBy(
            () -> useCase.execute(institutionId, new CreateShiftRequest("Turno mañana", null)))
        .isInstanceOf(InstitutionNotFoundException.class);
  }
}
