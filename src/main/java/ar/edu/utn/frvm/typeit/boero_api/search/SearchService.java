package ar.edu.utn.frvm.typeit.boero_api.search;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SearchService {

  private final SearchRepository repository;

  @Transactional(readOnly = true)
  public SearchSummaryResponse selfServiceSummary(
      final UUID institutionId, final UUID personId, final String search, final int limit) {
    final var grouped =
        repository.selfServiceSummary(institutionId, personId, SearchQuery.from(search), limit + 1);
    return summary(
        List.of(SearchEntityType.ENROLLMENT_APPLICATION, SearchEntityType.COURSE_ENROLLMENT),
        grouped,
        limit);
  }

  @Transactional(readOnly = true)
  public SearchSummaryResponse platformSummary(final String search, final int limit) {
    final List<SearchDefinition> definitions = SearchDefinition.all();
    final var grouped =
        repository.platformSummary(definitions, SearchQuery.from(search), limit + 1);
    return summary(
        definitions.stream().map(definition -> definition.type()).toList(), grouped, limit);
  }

  @Transactional(readOnly = true)
  public SearchSummaryResponse institutionalSummary(
      final UUID institutionId,
      final @Nullable UUID personId,
      final String search,
      final int limit,
      final Set<PermissionCode> permissions) {
    final List<SearchDefinition> definitions = SearchDefinition.institutionalFor(permissions);
    final var grouped =
        repository.institutionalSummary(
            definitions, SearchQuery.from(search), institutionId, personId, limit + 1);
    return summary(
        definitions.stream().map(definition -> definition.type()).toList(), grouped, limit);
  }

  @Transactional(readOnly = true)
  public PaginatedResponse<SearchResultResponse> platformPage(
      final SearchEntityType entityType, final String search, final int page, final int size) {
    final SearchDefinition definition = SearchDefinition.fromType(entityType);
    final SearchQuery query = SearchQuery.from(search);
    final List<SearchResultResponse> items = repository.page(definition, query, page, size);
    final long totalItems = repository.count(definition, query);
    final int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
    return new PaginatedResponse<>(items, page, size, totalItems, totalPages);
  }

  private SearchSummaryResponse summary(
      final List<SearchEntityType> types,
      final Map<SearchEntityType, List<SearchResultResponse>> grouped,
      final int limit) {
    final List<SearchGroupResponse> groups =
        types.stream()
            .filter(grouped::containsKey)
            .map(
                type -> {
                  final List<SearchResultResponse> results = requireNonNull(grouped.get(type));
                  return new SearchGroupResponse(
                      type, results.stream().limit(limit).toList(), results.size() > limit);
                })
            .toList();
    return new SearchSummaryResponse(groups);
  }
}
