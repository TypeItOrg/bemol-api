package ar.edu.utn.frvm.typeit.boero_api.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
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
class SearchServiceTest {

  @Mock private SearchRepository repository;
  @InjectMocks private SearchService searchService;

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
  void institutionalSummaryIncludesGuardianDependentsForGuardianDependentManagers() {
    final UUID institutionId = UUID.randomUUID();
    final UUID personId = UUID.randomUUID();
    when(repository.institutionalSummary(any(), any(), eq(institutionId), eq(personId), anyInt()))
        .thenReturn(Map.of());

    searchService.institutionalSummary(
        institutionId, personId, "mateo", 5, Set.of(PermissionCode.GUARDIAN_DEPENDENT_MANAGE));

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
