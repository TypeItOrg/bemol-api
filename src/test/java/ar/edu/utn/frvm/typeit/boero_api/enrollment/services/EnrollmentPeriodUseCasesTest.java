package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicYearFixtures;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicYearNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.AcademicYearRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.PermissionAccess;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.ScopedAuthorizationService;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentPeriod;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentPeriodStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentPeriodNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.InvalidEnrollmentPeriodDatesException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentPeriodRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CreateEnrollmentPeriodRequest;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollmentPeriodStatusRequest;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.UpdateEnrollmentPeriodRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnrollmentPeriodUseCasesTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);

  @Mock private EnrollmentPeriodRepository periodRepository;
  @Mock private InstitutionRepository institutionRepository;
  @Mock private AcademicYearRepository academicYearRepository;

  @Mock private ScopedAuthorizationService authorization;
  @Mock private EnrollmentPeriodScopeService scopeService;
  @Mock private EnrollmentInstitutionLock institutionLock;
  private EnrollmentPeriodAccessService periodAccess;

  private CreateEnrollmentPeriodUseCase createUseCase;
  private GetEnrollmentPeriodUseCase getUseCase;
  private UpdateEnrollmentPeriodUseCase updateUseCase;
  private UpdateEnrollmentPeriodStatusUseCase updateStatusUseCase;
  private DeleteEnrollmentPeriodUseCase deleteUseCase;

  private UUID institutionId;
  private UUID academicYearId;
  private UUID periodId;
  private Institution institution;
  private AcademicYear academicYear;
  private EnrollmentPeriod period;

  @BeforeEach
  void setUp() {
    periodAccess = new EnrollmentPeriodAccessService(authorization);
    createUseCase =
        new CreateEnrollmentPeriodUseCase(
            periodAccess,
            institutionLock,
            scopeService,
            institutionRepository,
            academicYearRepository);
    getUseCase = new GetEnrollmentPeriodUseCase(periodAccess, periodRepository);
    updateUseCase =
        new UpdateEnrollmentPeriodUseCase(
            periodAccess, periodRepository, institutionLock, scopeService);
    updateStatusUseCase =
        new UpdateEnrollmentPeriodStatusUseCase(
            periodAccess, periodRepository, institutionLock, scopeService);
    deleteUseCase =
        new DeleteEnrollmentPeriodUseCase(
            periodAccess, periodRepository, institutionLock, scopeService, CLOCK);

    institutionId = UUID.fromString("00000000-0000-4000-8000-000000000001");
    academicYearId = UUID.fromString("00000000-0000-4000-8000-000000000002");
    periodId = UUID.fromString("00000000-0000-4000-8000-000000000003");

    institution = Institution.builder().id(institutionId).name("UTN FRVM").build();
    academicYear = AcademicYearFixtures.planned(academicYearId, institution, 2026);

    period =
        EnrollmentPeriod.builder()
            .id(periodId)
            .institution(institution)
            .academicYear(academicYear)
            .name("Inscripción 2026 - Primer Llamado")
            .startDate(LocalDateTime.of(2026, 11, 1, 8, 0).toInstant(ZoneOffset.UTC))
            .endDate(LocalDateTime.of(2026, 12, 1, 20, 0).toInstant(ZoneOffset.UTC))
            .status(EnrollmentPeriodStatus.PLANNED)
            .build();
  }

  @Test
  @DisplayName("Create: Debería crear un período exitosamente")
  void create_success() {
    when(authorization.managementAccess(any())).thenReturn(PermissionAccess.institution());
    var request =
        new CreateEnrollmentPeriodRequest(
            academicYearId,
            "  Inscripción 2026  ",
            LocalDateTime.of(2026, 11, 1, 8, 0).toInstant(ZoneOffset.UTC),
            LocalDateTime.of(2026, 12, 1, 20, 0).toInstant(ZoneOffset.UTC));

    when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(institution));
    when(academicYearRepository.findById(academicYearId)).thenReturn(Optional.of(academicYear));
    when(scopeService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var response = createUseCase.execute(institutionId, request);

    assertThat(response).isNotNull();
    assertThat(response.name()).isEqualTo("Inscripción 2026");
    assertThat(response.status()).isEqualTo(EnrollmentPeriodStatus.PLANNED);
    assertThat(response.institutionId()).isEqualTo(institutionId);
    assertThat(response.academicYearId()).isEqualTo(academicYearId);
    assertThat(response.startDate()).isEqualTo(request.startDate());
    assertThat(response.endDate()).isEqualTo(request.endDate());
    final var saved = ArgumentCaptor.forClass(EnrollmentPeriod.class);
    verify(scopeService).save(saved.capture());
    assertThat(saved.getValue().getInstitution()).isSameAs(institution);
    assertThat(saved.getValue().getAcademicYear()).isSameAs(academicYear);
    verify(scopeService).configure(saved.getValue(), request.offerings());
    verify(institutionLock).lock(institutionId);
  }

  @Test
  @DisplayName("Create: Debería fallar si las fechas son inválidas")
  void create_invalidDates() {
    var request =
        new CreateEnrollmentPeriodRequest(
            academicYearId,
            "Inscripción 2026",
            LocalDateTime.of(2026, 12, 1, 8, 0).toInstant(ZoneOffset.UTC),
            LocalDateTime.of(2026, 11, 1, 20, 0).toInstant(ZoneOffset.UTC));

    assertThatThrownBy(() -> createUseCase.execute(institutionId, request))
        .isInstanceOf(InvalidEnrollmentPeriodDatesException.class);
    verifyNoInteractions(institutionRepository, academicYearRepository, scopeService);
  }

  @Test
  @DisplayName("Create: Debería fallar si la institución no existe")
  void create_institutionNotFound() {
    var request =
        new CreateEnrollmentPeriodRequest(
            academicYearId,
            "Inscripción 2026",
            LocalDateTime.of(2026, 11, 1, 8, 0).toInstant(ZoneOffset.UTC),
            LocalDateTime.of(2026, 12, 1, 20, 0).toInstant(ZoneOffset.UTC));

    when(institutionRepository.findById(institutionId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> createUseCase.execute(institutionId, request))
        .isInstanceOf(InstitutionNotFoundException.class);
  }

  @Test
  @DisplayName("Create: Debería fallar si el ciclo lectivo no existe")
  void create_academicYearNotFound() {
    var request =
        new CreateEnrollmentPeriodRequest(
            academicYearId,
            "Inscripción 2026",
            LocalDateTime.of(2026, 11, 1, 8, 0).toInstant(ZoneOffset.UTC),
            LocalDateTime.of(2026, 12, 1, 20, 0).toInstant(ZoneOffset.UTC));

    when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(institution));
    when(academicYearRepository.findById(academicYearId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> createUseCase.execute(institutionId, request))
        .isInstanceOf(AcademicYearNotFoundException.class);
  }

  @Test
  @DisplayName("Get: Debería devolver el detalle del período")
  void get_success() {
    when(authorization.managementAccess(any())).thenReturn(PermissionAccess.institution());
    when(periodRepository.findByIdAndInstitutionIdAndDeletedAtIsNull(periodId, institutionId))
        .thenReturn(Optional.of(period));

    var response = getUseCase.execute(institutionId, periodId);

    assertThat(response.id()).isEqualTo(periodId);
    assertThat(response.name()).isEqualTo(period.getName());
  }

  @Test
  @DisplayName("Get: Debería fallar si el período no existe")
  void get_notFound() {
    when(periodRepository.findByIdAndInstitutionIdAndDeletedAtIsNull(periodId, institutionId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> getUseCase.execute(institutionId, periodId))
        .isInstanceOf(EnrollmentPeriodNotFoundException.class);
  }

  @Test
  @DisplayName("Update: Debería actualizar datos correctamente")
  void update_success() {
    when(authorization.managementAccess(any())).thenReturn(PermissionAccess.institution());
    var request =
        new UpdateEnrollmentPeriodRequest(
            "Inscripción 2026 Editada",
            LocalDateTime.of(2026, 11, 5, 8, 0).toInstant(ZoneOffset.UTC),
            LocalDateTime.of(2026, 12, 10, 20, 0).toInstant(ZoneOffset.UTC));

    when(periodRepository.findByIdAndInstitutionIdAndDeletedAtIsNull(periodId, institutionId))
        .thenReturn(Optional.of(period));
    when(scopeService.save(any())).thenReturn(period);

    var response = updateUseCase.execute(institutionId, periodId, request);

    assertThat(response).isNotNull();
    assertThat(response.id()).isEqualTo(periodId);
    assertThat(response.name()).isEqualTo("Inscripción 2026 Editada");
    assertThat(response.startDate()).isEqualTo(request.startDate());
    assertThat(response.endDate()).isEqualTo(request.endDate());
    assertThat(response.status()).isEqualTo(EnrollmentPeriodStatus.PLANNED);
    assertThat(period.getName()).isEqualTo(response.name());
    verify(scopeService).save(period);
  }

  @Test
  @DisplayName("UpdateStatus: Debería cambiar el estado correctamente")
  void updateStatus_success() {
    when(authorization.managementAccess(any())).thenReturn(PermissionAccess.institution());
    period.markScopeConfigured();
    var request = new EnrollmentPeriodStatusRequest(EnrollmentPeriodStatus.OPEN);

    when(periodRepository.findByIdAndInstitutionIdAndDeletedAtIsNull(periodId, institutionId))
        .thenReturn(Optional.of(period));

    updateStatusUseCase.execute(institutionId, periodId, request);

    assertThat(period.getStatus()).isEqualTo(EnrollmentPeriodStatus.OPEN);
    verify(scopeService).save(period);
  }

  @Test
  @DisplayName("Delete: Debería marcar como eliminado (soft delete)")
  void delete_success() {
    when(authorization.managementAccess(any())).thenReturn(PermissionAccess.institution());
    when(periodRepository.findByIdAndInstitutionIdAndDeletedAtIsNull(periodId, institutionId))
        .thenReturn(Optional.of(period));

    deleteUseCase.execute(institutionId, periodId);

    assertThat(period.getDeletedAt()).isEqualTo(CLOCK.instant());
    verify(scopeService).save(period);
  }
}
