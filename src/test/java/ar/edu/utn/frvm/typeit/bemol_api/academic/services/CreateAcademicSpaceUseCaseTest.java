package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicSpaceRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateAcademicSpaceRequest;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateAcademicSpaceUseCaseTest {

  @Mock private AcademicSpaceRepository academicSpaceRepository;

  @Mock private InstitutionRepository institutionRepository;

  @Test
  @DisplayName("Should create an academic space with the requested format")
  void createsSpaceWithRequestedFormat() {
    final UUID institutionId = UUID.randomUUID();
    final var institution = Institution.builder().id(institutionId).build();
    given(institutionRepository.findById(institutionId)).willReturn(Optional.of(institution));
    given(
            academicSpaceRepository.existsByNormalizedNameAndTypeAndFormat(
                institutionId, "Programación", "SUBJECT", "GRUPAL"))
        .willReturn(false);
    given(academicSpaceRepository.save(any(AcademicSpace.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    final var response =
        new CreateAcademicSpaceUseCase(
                org.mockito.Mockito.mock(AuditEventRecorder.class),
                academicSpaceRepository,
                institutionRepository)
            .execute(
                institutionId,
                new CreateAcademicSpaceRequest(
                    "Programación", null, AcademicSpaceType.SUBJECT, AcademicSpaceFormat.GRUPAL));

    assertThat(response.format()).isEqualTo(AcademicSpaceFormat.GRUPAL);
    assertThat(response.type()).isEqualTo(AcademicSpaceType.SUBJECT);
    verify(academicSpaceRepository).flush();
  }

  @Test
  @DisplayName("Should reject a duplicate name for the same type and format")
  void rejectsDuplicateNameTypeAndFormat() {
    final UUID institutionId = UUID.randomUUID();
    final var institution = Institution.builder().id(institutionId).build();
    given(institutionRepository.findById(institutionId)).willReturn(Optional.of(institution));
    given(
            academicSpaceRepository.existsByNormalizedNameAndTypeAndFormat(
                institutionId, "Programación", "SUBJECT", "INDIVIDUAL"))
        .willReturn(true);

    assertThatThrownBy(
            () ->
                new CreateAcademicSpaceUseCase(
                        org.mockito.Mockito.mock(AuditEventRecorder.class),
                        academicSpaceRepository,
                        institutionRepository)
                    .execute(
                        institutionId,
                        new CreateAcademicSpaceRequest(
                            "Programación",
                            null,
                            AcademicSpaceType.SUBJECT,
                            AcademicSpaceFormat.INDIVIDUAL)))
        .isInstanceOf(AcademicConflictException.class);
  }
}
