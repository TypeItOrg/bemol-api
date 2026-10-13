package ar.edu.utn.frvm.typeit.boero_api.search;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.authorization.services.ScopedAuthorizationService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class SearchRepository {

  private static final RowMapper<SearchRow> ROW_MAPPER = SearchRepository::mapRow;

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ScopedAuthorizationService authorization;

  Map<SearchEntityType, List<SearchResultResponse>> platformSummary(
      final List<SearchDefinition> definitions,
      final SearchQuery searchQuery,
      final int fetchLimit) {
    return summary(definitions, searchQuery, null, null, fetchLimit);
  }

  Map<SearchEntityType, List<SearchResultResponse>> institutionalSummary(
      final List<SearchDefinition> definitions,
      final SearchQuery searchQuery,
      final UUID institutionId,
      final @Nullable UUID personId,
      final int fetchLimit) {
    return summary(definitions, searchQuery, institutionId, personId, fetchLimit);
  }

  private Map<SearchEntityType, List<SearchResultResponse>> summary(
      final List<SearchDefinition> definitions,
      final SearchQuery searchQuery,
      final @Nullable UUID institutionId,
      final @Nullable UUID personId,
      final int fetchLimit) {
    if (definitions.isEmpty()) {
      return Map.of();
    }

    final var queryParameters =
        parameters(searchQuery, institutionId, personId).addValue("fetchLimit", fetchLimit);
    final String unionSql =
        definitions.stream()
            .map(definition -> summaryBranch(definition, institutionId != null, queryParameters))
            .collect(Collectors.joining(" UNION ALL "));
    final String sql =
        "SELECT * FROM ("
            + unionSql
            + ") search_results ORDER BY entity_type, score DESC, title, id";
    final List<SearchRow> rows = jdbcTemplate.query(sql, queryParameters, ROW_MAPPER);
    final Map<SearchEntityType, List<SearchResultResponse>> grouped =
        new EnumMap<>(SearchEntityType.class);
    for (SearchRow row : rows) {
      grouped.computeIfAbsent(row.entityType(), ignored -> new ArrayList<>()).add(row.result());
    }
    return grouped;
  }

  List<SearchResultResponse> page(
      final SearchDefinition definition,
      final SearchQuery searchQuery,
      final int page,
      final int size) {
    final String sql =
        "SELECT entity.* FROM ("
            + definition.selectSql()
            + ") entity ORDER BY entity.score DESC, entity.title, entity.id LIMIT :size OFFSET"
            + " :offset";
    return jdbcTemplate.query(
        sql,
        parameters(searchQuery, null, null)
            .addValue("size", size)
            .addValue("offset", Math.multiplyExact(page, size)),
        (resultSet, rowNumber) -> mapResult(resultSet));
  }

  long count(final SearchDefinition definition, final SearchQuery searchQuery) {
    final String sql = "SELECT count(*) FROM (" + definition.selectSql() + ") entity";
    final Long count =
        jdbcTemplate.queryForObject(sql, parameters(searchQuery, null, null), Long.class);
    return count == null ? 0 : count;
  }

  private String summaryBranch(
      final SearchDefinition definition,
      final boolean institutional,
      MapSqlParameterSource parameters) {
    return "(SELECT '"
        + definition.type().code()
        + "' AS entity_type, entity.* FROM ("
        + definition.selectSql()
        + ") entity"
        + (institutional
            ? " WHERE entity.institution_id = :institutionId"
                + scopeCondition(definition, parameters)
            : "")
        + " ORDER BY entity.score DESC, entity.title, entity.id LIMIT :fetchLimit)";
  }

  private String scopeCondition(SearchDefinition definition, MapSqlParameterSource parameters) {
    var access = authorization.access(requireNonNull(definition.permission()));
    if (access.institutional()) {
      return "";
    }
    if (access.trainingPathIds().isEmpty()) {
      return " AND false";
    }
    String parameter = "scope_" + definition.name();
    parameters.addValue(parameter, access.trainingPathIds());
    return switch (definition.type()) {
      case TRAINING_PATH -> " AND entity.id IN (:" + parameter + ")";
      case STUDY_PLAN ->
          " AND entity.id IN (SELECT study_plan_id FROM study_plans WHERE training_path_id IN (:"
              + parameter
              + "))";
      case COURSE ->
          " AND entity.id IN (SELECT c.course_id FROM courses c JOIN study_plan_spaces s ON"
              + " s.study_plan_space_id = c.study_plan_space_id JOIN study_plans p ON"
              + " p.study_plan_id = s.study_plan_id WHERE p.training_path_id IN (:"
              + parameter
              + "))";
      default -> " AND false";
    };
  }

  private static MapSqlParameterSource parameters(
      final SearchQuery query, final @Nullable UUID institutionId, final @Nullable UUID personId) {
    final MapSqlParameterSource parameters =
        new MapSqlParameterSource()
            .addValue("query", query.tsQuery())
            .addValue("normalized", query.normalized());
    if (institutionId != null) {
      parameters.addValue("institutionId", institutionId);
    }
    // Always bound: a null personId matches no dependents instead of failing the query.
    parameters.addValue("personId", personId, Types.OTHER);
    return parameters;
  }

  private static SearchRow mapRow(final ResultSet resultSet, final int rowNumber)
      throws SQLException {
    return new SearchRow(
        SearchEntityType.fromCode(resultSet.getString("entity_type")), mapResult(resultSet));
  }

  private static SearchResultResponse mapResult(final ResultSet resultSet) throws SQLException {
    return new SearchResultResponse(
        resultSet.getObject("id", UUID.class),
        resultSet.getObject("institution_id", UUID.class),
        resultSet.getString("institution_name"),
        resultSet.getObject("institution_active", Boolean.class),
        resultSet.getString("title"),
        resultSet.getString("subtitle"),
        resultSet.getString("status"),
        resultSet.getString("category"),
        resultSet.getObject("study_plan_version", Integer.class));
  }

  private record SearchRow(SearchEntityType entityType, SearchResultResponse result) {}
}
