package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.City;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Province;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.MonthlyInstitutionCount;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PlatformDashboardSummaryCounts;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetPlatformDashboardUseCaseTest {

  private InstitutionRepository institutionRepository;
  private GetPlatformDashboardUseCase useCase;

  @BeforeEach
  void setUp() {
    institutionRepository = mock(InstitutionRepository.class);
    useCase = new GetPlatformDashboardUseCase(institutionRepository, Clock.systemUTC());
  }

  @Test
  @DisplayName("Should return platform dashboard with a complete twelve month trend")
  void execute_returnsDashboardWithCompleteTrend() {
    final YearMonth currentMonth = YearMonth.of(2026, 7);
    final MonthlyInstitutionCount novemberCount = monthlyCount(2025, 11, 3);
    final Institution recentInstitution = recentInstitution();
    final PlatformDashboardSummaryCounts summaryCounts = summaryCounts(9, 7, 41, 28);
    when(institutionRepository.getPlatformDashboardSummaryCounts()).thenReturn(summaryCounts);
    when(institutionRepository.countCreatedByMonth(
            LocalDateTime.of(2025, 8, 1, 0, 0).toInstant(ZoneOffset.UTC),
            LocalDateTime.of(2026, 8, 1, 0, 0).toInstant(ZoneOffset.UTC)))
        .thenReturn(List.of(novemberCount));
    when(institutionRepository.findTop5ByOrderByCreatedAtDesc())
        .thenReturn(List.of(recentInstitution));

    final var response = useCase.execute(currentMonth);

    assertThat(response.summary().institutions()).isEqualTo(9);
    assertThat(response.summary().activeInstitutions()).isEqualTo(7);
    assertThat(response.summary().inactiveInstitutions()).isEqualTo(2);
    assertThat(response.summary().people()).isEqualTo(41);
    assertThat(response.summary().usersWithAccess()).isEqualTo(28);
    assertThat(response.institutionRegistrations()).hasSize(12);
    assertThat(response.institutionRegistrations().getFirst().year()).isEqualTo(2025);
    assertThat(response.institutionRegistrations().getFirst().month()).isEqualTo(8);
    assertThat(response.institutionRegistrations().getFirst().count()).isZero();
    assertThat(response.institutionRegistrations().get(3).count()).isEqualTo(3);
    assertThat(response.institutionRegistrations().getLast().year()).isEqualTo(2026);
    assertThat(response.institutionRegistrations().getLast().month()).isEqualTo(7);
    assertThat(response.recentInstitutions()).hasSize(1);
    assertThat(response.recentInstitutions().getFirst().name()).isEqualTo("Instituto Boero");
  }

  @Test
  @DisplayName("Should return zero values and empty recent institutions when no data exists")
  void execute_returnsEmptyDashboard() {
    final PlatformDashboardSummaryCounts summaryCounts = summaryCounts(0, 0, 0, 0);
    when(institutionRepository.getPlatformDashboardSummaryCounts()).thenReturn(summaryCounts);
    when(institutionRepository.countCreatedByMonth(
            LocalDateTime.of(2025, 8, 1, 0, 0).toInstant(ZoneOffset.UTC),
            LocalDateTime.of(2026, 8, 1, 0, 0).toInstant(ZoneOffset.UTC)))
        .thenReturn(List.of());
    when(institutionRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(List.of());

    final var response = useCase.execute(YearMonth.of(2026, 7));

    assertThat(response.summary().institutions()).isZero();
    assertThat(response.institutionRegistrations())
        .allSatisfy(registration -> assertThat(registration.count()).isZero());
    assertThat(response.recentInstitutions()).isEmpty();
  }

  @Test
  @DisplayName("Should bucket the dashboard trend by UTC month")
  void execute_usesUtcMonthForTrendWindow() {
    final Clock clock = Clock.fixed(Instant.parse("2026-08-01T02:00:00Z"), ZoneOffset.UTC);
    useCase = new GetPlatformDashboardUseCase(institutionRepository, clock);
    final PlatformDashboardSummaryCounts summaryCounts = summaryCounts(0, 0, 0, 0);
    when(institutionRepository.getPlatformDashboardSummaryCounts()).thenReturn(summaryCounts);
    when(institutionRepository.countCreatedByMonth(
            LocalDateTime.of(2025, 9, 1, 0, 0).toInstant(ZoneOffset.UTC),
            LocalDateTime.of(2026, 9, 1, 0, 0).toInstant(ZoneOffset.UTC)))
        .thenReturn(List.of());
    when(institutionRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(List.of());

    final var response = useCase.execute();

    assertThat(response.institutionRegistrations()).hasSize(12);
    assertThat(response.institutionRegistrations().getFirst().year()).isEqualTo(2025);
    assertThat(response.institutionRegistrations().getFirst().month()).isEqualTo(9);
    assertThat(response.institutionRegistrations().getLast().year()).isEqualTo(2026);
    assertThat(response.institutionRegistrations().getLast().month()).isEqualTo(8);
  }

  private static PlatformDashboardSummaryCounts summaryCounts(
      final long institutions,
      final long activeInstitutions,
      final long people,
      final long usersWithAccess) {
    final PlatformDashboardSummaryCounts result = mock(PlatformDashboardSummaryCounts.class);
    when(result.getInstitutions()).thenReturn(institutions);
    when(result.getActiveInstitutions()).thenReturn(activeInstitutions);
    when(result.getPeople()).thenReturn(people);
    when(result.getUsersWithAccess()).thenReturn(usersWithAccess);
    return result;
  }

  private static MonthlyInstitutionCount monthlyCount(
      final int year, final int month, final long count) {
    final MonthlyInstitutionCount result = mock(MonthlyInstitutionCount.class);
    when(result.getYear()).thenReturn(year);
    when(result.getMonth()).thenReturn(month);
    when(result.getInstitutionCount()).thenReturn(count);
    return result;
  }

  private static Institution recentInstitution() {
    final Province province = Province.builder().name("Córdoba").build();
    final City city = City.builder().name("Villa María").province(province).build();
    final Institution institution = mock(Institution.class);
    when(institution.getId()).thenReturn(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    when(institution.getName()).thenReturn("Instituto Boero");
    when(institution.getCity()).thenReturn(city);
    when(institution.isActive()).thenReturn(true);
    when(institution.getCreatedAt())
        .thenReturn(LocalDateTime.of(2026, 7, 10, 12, 0).toInstant(ZoneOffset.UTC));
    return institution;
  }
}
