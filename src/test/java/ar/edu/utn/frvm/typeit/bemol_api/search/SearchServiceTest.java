package ar.edu.utn.frvm.typeit.bemol_api.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCapabilitiesService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class SearchServiceTest {

  @Mock private SearchRepository repository;
  @Mock private InstitutionalCapabilitiesService capabilities;
  @InjectMocks private SearchService searchService;

  @Test
  void selfServiceSummaryPreservesGroupOrderAndLimitsResults() {
    final UUID institutionId = UUID.randomUUID();
    final UUID personId = UUID.randomUUID();
    final var application =
        new SearchResultResponse(
            UUID.randomUUID(), institutionId, null, null, "Solicitud", null, null, null, null);
    final var course =
        new SearchResultResponse(
            UUID.randomUUID(), institutionId, null, null, "Curso", null, null, null, null);
    when(repository.selfServiceSummary(eq(institutionId), eq(personId), any(), eq(2), any()))
        .thenReturn(
            Map.of(
                SearchEntityType.COURSE_ENROLLMENT, List.of(course),
                SearchEntityType.ENROLLMENT_APPLICATION, List.of(application, application)));

    final var result = searchService.selfServiceSummary(institutionId, personId, "mateo", 1);

    assertThat(result.groups())
        .containsExactly(
            new SearchGroupResponse(
                SearchEntityType.ENROLLMENT_APPLICATION, List.of(application), true),
            new SearchGroupResponse(SearchEntityType.COURSE_ENROLLMENT, List.of(course), false));
  }

  @Test
  void selfServiceSummaryOmitsGroupsWithoutResults() {
    final UUID institutionId = UUID.randomUUID();
    final UUID personId = UUID.randomUUID();
    final var course =
        new SearchResultResponse(
            UUID.randomUUID(), institutionId, null, null, "Curso", null, null, null, null);
    when(repository.selfServiceSummary(eq(institutionId), eq(personId), any(), eq(2), any()))
        .thenReturn(Map.of(SearchEntityType.COURSE_ENROLLMENT, List.of(course)));

    final var result = searchService.selfServiceSummary(institutionId, personId, "mateo", 1);

    assertThat(result.groups())
        .containsExactly(
            new SearchGroupResponse(SearchEntityType.COURSE_ENROLLMENT, List.of(course), false));
  }

  @Test
  void institutionalSummaryIncludesOnlyEntitiesGrantedByReadPermissions() {
    final UUID institutionId = UUID.randomUUID();
    final UUID personId = UUID.randomUUID();
    when(repository.institutionalSummary(any(), any(), eq(institutionId), eq(personId), anyInt()))
        .thenReturn(Map.of());

    searchService.institutionalSummary(
        institutionId,
        personId,
        "matias",
        5,
        Set.of(PermissionCode.INSTITUTION_PERSON_READ_ANY, PermissionCode.STUDY_PLAN_READ));

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<List<SearchDefinition>> definitions = ArgumentCaptor.forClass(List.class);
    verify(repository)
        .institutionalSummary(definitions.capture(), any(), eq(institutionId), eq(personId), eq(6));
    assertThat(definitions.getValue())
        .extracting(mappedSearchDefinition -> mappedSearchDefinition.type())
        .containsExactly(SearchEntityType.USER, SearchEntityType.STUDY_PLAN);
  }

  @Test
  void institutionalSummaryIncludesOwnRepresentationsWithPermission() {
    final UUID institutionId = UUID.randomUUID();
    final UUID personId = UUID.randomUUID();
    when(repository.institutionalSummary(any(), any(), eq(institutionId), eq(personId), anyInt()))
        .thenReturn(Map.of());

    searchService.institutionalSummary(
        institutionId, personId, "mateo", 5, Set.of(PermissionCode.GUARDIAN_LINK_REQUEST));

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<List<SearchDefinition>> definitions = ArgumentCaptor.forClass(List.class);
    verify(repository)
        .institutionalSummary(definitions.capture(), any(), eq(institutionId), eq(personId), eq(6));
    assertThat(definitions.getValue())
        .extracting(SearchDefinition::type)
        .containsExactly(SearchEntityType.GUARDIAN_DEPENDENT);
  }

  @Test
  void platformSearchNeverIncludesGuardianDependents() {
    assertThat(SearchDefinition.all())
        .extracting(SearchDefinition::type)
        .doesNotContain(SearchEntityType.GUARDIAN_DEPENDENT);
  }
}
