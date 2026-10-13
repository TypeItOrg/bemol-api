package ar.edu.utn.frvm.typeit.boero_api.search;

import static ar.edu.utn.frvm.typeit.boero_api.search.SearchMessages.INVALID_ENTITY_TYPE;

import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

enum SearchDefinition {
  INSTITUTION(
      SearchEntityType.INSTITUTION,
      null,
      """
SELECT i.institution_id AS id, i.institution_id, i.name AS institution_name,
       i.active AS institution_active, i.name AS title, i.slug AS subtitle,
       CASE WHEN i.active THEN 'ACTIVE' ELSE 'INACTIVE' END AS status,
       NULL::text AS category,
       boero_search_rank(i.name || ' ' || i.slug, :query, :normalized) AS score, NULL::integer AS study_plan_version
  FROM institutions i
 WHERE boero_search_vector(i.name || ' ' || i.slug) @@ to_tsquery('simple', :query)
"""),
  USER(
      SearchEntityType.USER,
      PermissionCode.INSTITUTION_PERSON_READ_ANY,
      """
SELECT p.person_id AS id, p.institution_id, i.name AS institution_name,
       i.active AS institution_active, p.first_name || ' ' || p.last_name AS title,
       p.document_number AS subtitle,
       CASE WHEN u.user_id IS NULL THEN 'NO_ACCESS' WHEN u.enabled THEN 'ENABLED' ELSE 'DISABLED' END AS status,
       NULL::text AS category,
       boero_search_rank(p.first_name || ' ' || p.last_name || ' ' || p.last_name || ' ' || p.first_name || ' ' || p.document_number || ' ' || coalesce(p.email, ''), :query, :normalized) AS score, NULL::integer AS study_plan_version
  FROM people p
  JOIN institutions i ON i.institution_id = p.institution_id
  LEFT JOIN users u ON u.institution_id = p.institution_id AND u.person_id = p.person_id
 WHERE NOT p.deleted
   AND boero_search_vector(p.first_name || ' ' || p.last_name || ' ' || p.last_name || ' ' || p.first_name || ' ' || p.document_number || ' ' || coalesce(p.email, '')) @@ to_tsquery('simple', :query)
"""),
  ROLE(
      SearchEntityType.ROLE,
      PermissionCode.INSTITUTION_ROLE_READ,
      """
SELECT r.role_id AS id, r.institution_id, i.name AS institution_name,
       i.active AS institution_active, r.name AS title, r.code AS subtitle,
       NULL::text AS status, CASE WHEN r.is_system THEN 'SYSTEM' ELSE 'CUSTOM' END AS category,
       boero_search_rank(r.name || ' ' || r.code, :query, :normalized) AS score, NULL::integer AS study_plan_version
  FROM roles r
  JOIN institutions i ON i.institution_id = r.institution_id
 WHERE r.scope = 'INSTITUTION'
   AND boero_search_vector(r.name || ' ' || r.code) @@ to_tsquery('simple', :query)
"""),
  PLATFORM_ACCOUNT(
      SearchEntityType.PLATFORM_ACCOUNT,
      null,
      """
SELECT a.platform_account_id AS id, NULL::uuid AS institution_id,
       NULL::text AS institution_name, NULL::boolean AS institution_active,
       a.first_name || ' ' || a.last_name AS title, a.email AS subtitle,
       CASE WHEN a.enabled THEN 'ENABLED' ELSE 'DISABLED' END AS status,
       NULL::text AS category,
       boero_search_rank(a.first_name || ' ' || a.last_name || ' ' || a.last_name || ' ' || a.first_name || ' ' || a.email, :query, :normalized) AS score, NULL::integer AS study_plan_version
  FROM platform_accounts a
 WHERE boero_search_vector(a.first_name || ' ' || a.last_name || ' ' || a.last_name || ' ' || a.first_name || ' ' || a.email) @@ to_tsquery('simple', :query)
"""),
  ACADEMIC_YEAR(
      SearchEntityType.ACADEMIC_YEAR,
      PermissionCode.ACADEMIC_YEAR_READ,
      """
SELECT y.academic_year_id AS id, y.institution_id, i.name AS institution_name,
       i.active AS institution_active, y.year::text AS title, NULL::text AS subtitle,
       y.status, NULL::text AS category,
       boero_search_rank(y.year::text, :query, :normalized) AS score, NULL::integer AS study_plan_version
  FROM academic_years y JOIN institutions i ON i.institution_id = y.institution_id
 WHERE y.deleted_at IS NULL
   AND boero_search_vector(y.year::text) @@ to_tsquery('simple', :query)
"""),
  TRAINING_PATH(
      SearchEntityType.TRAINING_PATH,
      PermissionCode.TRAINING_PATH_READ,
      namedEntity("training_paths", "training_path_id")),
  STUDY_PLAN(
      SearchEntityType.STUDY_PLAN,
      PermissionCode.STUDY_PLAN_READ,
      """
SELECT p.study_plan_id AS id, p.institution_id, i.name AS institution_name,
       i.active AS institution_active, p.name AS title, t.name AS subtitle,
       p.status, NULL::text AS category,
       boero_search_rank(p.name, :query, :normalized) AS score, p.version_number AS study_plan_version
  FROM study_plans p JOIN institutions i ON i.institution_id = p.institution_id
  JOIN training_paths t ON t.training_path_id = p.training_path_id
 WHERE p.deleted_at IS NULL
   AND t.deleted_at IS NULL
   AND boero_search_vector(p.name) @@ to_tsquery('simple', :query)
"""),
  ACADEMIC_SPACE(
      SearchEntityType.ACADEMIC_SPACE,
      PermissionCode.ACADEMIC_SPACE_READ,
      """
SELECT e.academic_space_id AS id, e.institution_id, i.name AS institution_name,
       i.active AS institution_active, e.name AS title, e.description AS subtitle,
       CASE WHEN e.active THEN 'ACTIVE' ELSE 'INACTIVE' END AS status, e.type AS category,
       boero_search_rank(e.name, :query, :normalized) AS score, NULL::integer AS study_plan_version
  FROM academic_spaces e JOIN institutions i ON i.institution_id = e.institution_id
 WHERE e.deleted_at IS NULL
   AND boero_search_vector(e.name) @@ to_tsquery('simple', :query)
"""),
  INSTRUMENT(
      SearchEntityType.INSTRUMENT,
      PermissionCode.INSTRUMENT_READ,
      namedEntity("instruments", "instrument_id")),
  SHIFT(SearchEntityType.SHIFT, PermissionCode.SHIFT_READ, namedEntity("shifts", "shift_id")),
  COURSE(
      SearchEntityType.COURSE,
      PermissionCode.COURSE_READ,
      """
      SELECT c.course_id AS id, c.institution_id, i.name AS institution_name,
             i.active AS institution_active, e.name AS title, p.name AS subtitle,
             c.status,
             NULL::text AS category,
             boero_search_rank(e.name || ' ' || p.name, :query, :normalized) AS score, p.version_number AS study_plan_version
        FROM courses c
        JOIN institutions i ON i.institution_id = c.institution_id
        JOIN academic_spaces e ON e.academic_space_id = c.academic_space_id
        JOIN study_plan_spaces s ON s.study_plan_space_id = c.study_plan_space_id
        JOIN study_plans p ON p.study_plan_id = s.study_plan_id
       WHERE c.deleted_at IS NULL
         AND e.deleted_at IS NULL
         AND p.deleted_at IS NULL
         AND boero_search_vector(e.name || ' ' || p.name) @@ to_tsquery('simple', :query)
       """),
  // Scoped to the caller's own dependents through :personId, so it is institutional-only.
  GUARDIAN_DEPENDENT(
      SearchEntityType.GUARDIAN_DEPENDENT,
      PermissionCode.GUARDIAN_DEPENDENT_MANAGE,
      guardianDependents());

  private final SearchEntityType type;
  private final @Nullable PermissionCode permission;
  private final String selectSql;

  SearchDefinition(
      final SearchEntityType type,
      final @Nullable PermissionCode permission,
      final String selectSql) {
    this.type = type;
    this.permission = permission;
    this.selectSql = selectSql;
  }

  SearchEntityType type() {
    return type;
  }

  boolean isAvailableTo(final Set<PermissionCode> permissions) {
    return permission != null && permissions.contains(permission);
  }

  boolean requiresPerson() {
    return type == SearchEntityType.GUARDIAN_DEPENDENT;
  }

  @Nullable PermissionCode permission() {
    return permission;
  }

  String selectSql() {
    return selectSql;
  }

  static List<SearchDefinition> all() {
    return Arrays.stream(values()).filter(definition -> !definition.requiresPerson()).toList();
  }

  static List<SearchDefinition> institutionalFor(final Set<PermissionCode> permissions) {
    return Arrays.stream(values())
        .filter(definition -> definition.isAvailableTo(permissions))
        .toList();
  }

  static SearchDefinition fromType(final SearchEntityType entityType) {
    return all().stream()
        .filter(definition -> definition.type == entityType)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException(INVALID_ENTITY_TYPE));
  }

  private static String guardianDependents() {
    final String searchText =
        "p.first_name || ' ' || p.last_name || ' ' || p.last_name || ' ' || p.first_name"
            + " || ' ' || p.document_number || ' ' || coalesce(p.email, '')";
    return """
        SELECT p.person_id AS id, p.institution_id, i.name AS institution_name,
               i.active AS institution_active, p.first_name || ' ' || p.last_name AS title,
               p.document_number AS subtitle, NULL::text AS status, NULL::text AS category,
               boero_search_rank(%1$s, :query, :normalized) AS score, NULL::integer AS study_plan_version
          FROM person_guardians g
          JOIN people p ON p.institution_id = g.institution_id AND p.person_id = g.dependent_person_id
          JOIN institutions i ON i.institution_id = p.institution_id
         WHERE g.tutor_person_id = :personId
           AND g.status = 'ACTIVE'
           AND NOT p.deleted
           AND boero_search_vector(%1$s) @@ to_tsquery('simple', :query)
        """
        .formatted(searchText);
  }

  private static String namedEntity(final String table, final String idColumn) {
    return """
SELECT e.%s AS id, e.institution_id, i.name AS institution_name,
       i.active AS institution_active, e.name AS title, e.description AS subtitle,
       CASE WHEN e.active THEN 'ACTIVE' ELSE 'INACTIVE' END AS status,
       NULL::text AS category,
       boero_search_rank(e.name, :query, :normalized) AS score, NULL::integer AS study_plan_version
  FROM %s e
  JOIN institutions i ON i.institution_id = e.institution_id
 WHERE e.deleted_at IS NULL
   AND boero_search_vector(e.name) @@ to_tsquery('simple', :query)
"""
        .formatted(idColumn, table);
  }
}
