package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicSpace;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.AcademicSpaceRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.UpdateAcademicSpaceRequest;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateAcademicSpaceUseCaseTest {

  @Mock private AcademicSpaceRepository academicSpaceRepository;

  private AcademicSpace existingSpace(final Institution institution) {
    return AcademicSpace.create(
        institution,
        "Programación",
        null,
        AcademicSpaceType.SUBJECT,
        AcademicSpaceFormat.INDIVIDUAL);
  }

  @Test
  @DisplayName("Should apply the requested format while updating an academic space")
  void updatesFormatFromEditRequest() {
    final UUID institutionId = UUID.randomUUID();
    final UUID spaceId = UUID.randomUUID();
    final var institution = Institution.builder().id(institutionId).build();
    given(academicSpaceRepository.findByIdAndInstitution_IdForUpdate(spaceId, institutionId))
        .willReturn(Optional.of(existingSpace(institution)));
    given(
            academicSpaceRepository.existsByNormalizedNameAndTypeAndFormatAndIdNot(
                institutionId, "Programación", "SUBJECT", "GRUPAL", spaceId))
        .willReturn(false);

    final var response =
        new UpdateAcademicSpaceUseCase(
                org.mockito.Mockito.mock(AuditEventRecorder.class), academicSpaceRepository)
            .execute(
                institutionId,
                spaceId,
                new UpdateAcademicSpaceRequest(
                    "Programación", null, AcademicSpaceType.SUBJECT, AcademicSpaceFormat.GRUPAL));

    assertThat(response.format()).isEqualTo(AcademicSpaceFormat.GRUPAL);
    verify(academicSpaceRepository).flush();
  }

  @Test
  @DisplayName("Should reject updating when another space matches name, type and format")
  void rejectsDuplicateNameTypeAndFormat() {
    final UUID institutionId = UUID.randomUUID();
    final UUID spaceId = UUID.randomUUID();
    final var institution = Institution.builder().id(institutionId).build();
    given(academicSpaceRepository.findByIdAndInstitution_IdForUpdate(spaceId, institutionId))
        .willReturn(Optional.of(existingSpace(institution)));
    given(
            academicSpaceRepository.existsByNormalizedNameAndTypeAndFormatAndIdNot(
                institutionId, "Programación", "SUBJECT", "INDIVIDUAL", spaceId))
        .willReturn(true);

    assertThatThrownBy(
            () ->
                new UpdateAcademicSpaceUseCase(
                        org.mockito.Mockito.mock(AuditEventRecorder.class), academicSpaceRepository)
                    .execute(
                        institutionId,
                        spaceId,
                        new UpdateAcademicSpaceRequest(
                            "Programación",
                            null,
                            AcademicSpaceType.SUBJECT,
                            AcademicSpaceFormat.INDIVIDUAL)))
        .isInstanceOf(AcademicConflictException.class);
  }
}
