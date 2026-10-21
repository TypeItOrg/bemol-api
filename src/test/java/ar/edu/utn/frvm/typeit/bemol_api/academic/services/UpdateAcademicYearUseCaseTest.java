package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicYearRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassTeacherRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateAcademicYearRequest;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.common.time.BusinessDateProvider;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.CourseClosureService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentInstitutionLock;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateAcademicYearUseCaseTest {
  @Mock private AuditEventRecorder auditRecorder;
  @Mock private CourseClassTeacherRepository courseClassTeacherRepository;

  @Mock private AcademicYearRepository academicYearRepository;
  @Mock private CourseRepository courseRepository;
  @Mock private CourseClosureService courseClosureService;
  @Mock private EnrollmentInstitutionLock institutionLock;
  private UpdateAcademicYearUseCase service;

  @BeforeEach
  void setUp() {
    final var statusChanges =
        new UpdateAcademicYearStatusUseCase(
            academicYearRepository,
            courseRepository,
            courseClosureService,
            institutionLock,
            auditRecorder,
            courseClassTeacherRepository);
    service =
        new UpdateAcademicYearUseCase(
            org.mockito.Mockito.mock(AuditEventRecorder.class),
            academicYearRepository,
            new BusinessDateProvider(Clock.systemUTC()),
            institutionLock,
            statusChanges);
  }

  @Test
  @DisplayName("Should apply the selected status while updating an academic year")
  void updatesStatusFromEditRequest() {
    final UUID institutionId = UUID.randomUUID();
    final UUID academicYearId = UUID.randomUUID();
    final var institution = Institution.builder().id(institutionId).build();
    final var academicYear =
        AcademicYear.create(
            institution,
            2026,
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 12, 15),
            LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")));
    given(academicYearRepository.findByIdAndInstitution_IdForUpdate(academicYearId, institutionId))
        .willReturn(Optional.of(academicYear));
    given(
            academicYearRepository.existsByInstitution_IdAndStatusAndDeletedAtIsNull(
                institutionId, AcademicYearStatus.ACTIVE))
        .willReturn(false);

    final var response =
        service.execute(
            institutionId,
            academicYearId,
            new UpdateAcademicYearRequest(
                2026,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 12, 15),
                AcademicYearStatus.ACTIVE));

    assertThat(response.status()).isEqualTo(AcademicYearStatus.ACTIVE);
    verify(academicYearRepository).flush();
  }

  @Test
  @DisplayName("Should reject activating an academic year when another one is already active")
  void rejectsSecondActiveAcademicYear() {
    final UUID institutionId = UUID.randomUUID();
    final UUID academicYearId = UUID.randomUUID();
    final var institution = Institution.builder().id(institutionId).build();
    final var academicYear =
        AcademicYear.create(
            institution,
            2026,
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 12, 15),
            LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")));
    given(academicYearRepository.findByIdAndInstitution_IdForUpdate(academicYearId, institutionId))
        .willReturn(Optional.of(academicYear));
    given(
            academicYearRepository.existsByInstitution_IdAndStatusAndDeletedAtIsNull(
                institutionId, AcademicYearStatus.ACTIVE))
        .willReturn(true);

    assertThatThrownBy(
            () ->
                service.execute(
                    institutionId,
                    academicYearId,
                    new UpdateAcademicYearRequest(
                        2026,
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 12, 15),
                        AcademicYearStatus.ACTIVE)))
        .isInstanceOf(AcademicConflictException.class);
  }
}
