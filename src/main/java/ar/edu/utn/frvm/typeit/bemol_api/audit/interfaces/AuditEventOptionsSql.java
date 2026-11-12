package ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces;

final class AuditEventOptionsSql {
  private AuditEventOptionsSql() {}

  private static final String AUDIT_TIME_ZONE = "America/Argentina/Cordoba";
  private static final String FIRST_ACTIVITY_TIMESTAMP_FORMAT = "DD/MM/YYYY HH24:MI:SS";
  private static final String FIRST_ACTIVITY_TIMESTAMP_SUFFIX =
      " AT TIME ZONE '" + AUDIT_TIME_ZONE + "', '" + FIRST_ACTIVITY_TIMESTAMP_FORMAT + "')";
  static final String PERSON_LABEL_PREFIX =
      "COALESCE(NULLIF(TRIM(CONCAT_WS(' ', person.first_name, person.last_name)), ''), "
          + "'Persona histórica · ' || ";
  private static final String SUBJECT_FIRST_ACTIVITY_LABEL =
      "TO_CHAR(subjects.first_activity" + FIRST_ACTIVITY_TIMESTAMP_SUFFIX;
  private static final String PARTICIPANT_FIRST_ACTIVITY_LABEL =
      "TO_CHAR(participants.first_activity" + FIRST_ACTIVITY_TIMESTAMP_SUFFIX;
  static final String IDENTITY_FIRST_ACTIVITY_LABEL =
      "TO_CHAR(identities.first_activity" + FIRST_ACTIVITY_TIMESTAMP_SUFFIX;
  static final String ACCOUNT_LABEL_PREFIX =
      "COALESCE(NULLIF(TRIM(CONCAT_WS(' ', account.first_name, account.last_name)), ''), "
          + "'Cuenta histórica · ' || ";
  static final String ROLE_LABEL_PREFIX =
      "COALESCE(role.name, identities.details -> 'after' ->> 'name', "
          + "identities.details -> 'before' ->> 'name', 'Rol histórico · ' || ";

  static final String OPTION_SCOPE_FILTER =
      """
      AND event.entity_type <> 'EMAIL'
      AND (CAST(:scope AS varchar) IS NULL
          OR (:scope = 'PLATFORM' AND event.institution_id IS NULL)
          OR (:scope = 'INSTITUTION' AND event.institution_id IS NOT NULL))
      AND (CAST(:institutionId AS uuid) IS NULL OR event.institution_id = :institutionId)
      """;
  static final String OPTION_SEARCH =
      """
      WHERE (CAST(:search AS varchar) IS NULL OR STRPOS(
          bemol_normalize_text(options.label || ' ' || COALESCE(options."institutionName", '')),
          bemol_normalize_text(CAST(:search AS varchar))) > 0)
      """;
  static final String OPTION_PAGE_SELECT = "SELECT options.* FROM (";
  static final String OPTION_COUNT_SELECT = "SELECT COUNT(*) FROM (";
  static final String OPTION_SOURCE_SUFFIX = ") options ";

  private static final String ACTORS_QUERY =
      """
      SELECT DISTINCT event.actor_kind AS "actorKind",
          COALESCE(event.actor_person_id, event.actor_platform_account_id) AS "actorId",
          COALESCE(CASE WHEN event.actor_kind = 'INSTITUTION'
              THEN person.first_name || ' ' || person.last_name
              ELSE account.first_name || ' ' || account.last_name END,
              CAST(COALESCE(event.actor_person_id, event.actor_platform_account_id) AS text)) AS label
      FROM audit_events event
      LEFT JOIN people person ON person.person_id = event.actor_person_id
      LEFT JOIN platform_accounts account ON account.platform_account_id = event.actor_platform_account_id
      WHERE event.actor_kind <> 'ANONYMOUS'
          AND event.entity_type <> 'EMAIL'
          AND (CAST(:actorKind AS varchar) IS NULL OR event.actor_kind = :actorKind)
          AND (CAST(:institutionId AS uuid) IS NULL OR event.institution_id = :institutionId)
      """;
  private static final String ACTORS_SEARCH =
      """
      WHERE (CAST(:search AS varchar) IS NULL OR STRPOS(
          bemol_normalize_text(actors.label), bemol_normalize_text(CAST(:search AS varchar))) > 0)
      """;
  static final String ACTORS_PAGE_QUERY =
      "SELECT actors.* FROM ("
          + ACTORS_QUERY
          + ") actors "
          + ACTORS_SEARCH
          + " ORDER BY LOWER(actors.label), actors.\"actorKind\", actors.\"actorId\"";
  static final String ACTORS_COUNT_QUERY =
      OPTION_COUNT_SELECT + ACTORS_QUERY + ") actors " + ACTORS_SEARCH;

  private static final String SUBJECTS_QUERY =
      """
      SELECT subjects.subject_person_id AS "subjectPersonId",
      """
          + PERSON_LABEL_PREFIX
          + SUBJECT_FIRST_ACTIVITY_LABEL
          + """
      ) AS label,
      institution.name AS "institutionName"
      FROM (
          SELECT DISTINCT ON (event.subject_person_id)
              event.subject_person_id, event.institution_id,
              MIN(event.occurred_at) OVER (PARTITION BY event.subject_person_id) AS first_activity
          FROM audit_events event
          WHERE event.subject_person_id IS NOT NULL
              AND (CAST(:subjectPersonId AS uuid) IS NULL OR event.subject_person_id = :subjectPersonId)
      """
          + OPTION_SCOPE_FILTER
          + """
      ORDER BY event.subject_person_id, event.occurred_at DESC, event.audit_event_id DESC
      ) subjects
      LEFT JOIN people person ON person.person_id = subjects.subject_person_id
      LEFT JOIN institutions institution ON institution.institution_id = subjects.institution_id
      """;
  static final String SUBJECTS_PAGE_QUERY =
      OPTION_PAGE_SELECT
          + SUBJECTS_QUERY
          + OPTION_SOURCE_SUFFIX
          + OPTION_SEARCH
          + " ORDER BY bemol_normalize_text(options.label), options.\"subjectPersonId\"";
  static final String SUBJECTS_COUNT_QUERY =
      OPTION_COUNT_SELECT + SUBJECTS_QUERY + OPTION_SOURCE_SUFFIX + OPTION_SEARCH;

  private static final String PEOPLE_QUERY =
      """
      SELECT participants.person_id AS "personId",
      """
          + PERSON_LABEL_PREFIX
          + PARTICIPANT_FIRST_ACTIVITY_LABEL
          + """
      ) AS label,
      institution.name AS "institutionName"
      FROM (
          SELECT DISTINCT ON (participant.person_id)
              participant.person_id, event.institution_id,
              MIN(event.occurred_at) OVER (PARTITION BY participant.person_id) AS first_activity
          FROM audit_events event
          LEFT JOIN person_guardians guardian ON event.entity_type = 'PERSON_GUARDIAN'
              AND guardian.person_guardian_id = event.entity_id
          CROSS JOIN LATERAL (
              SELECT person_id FROM (VALUES (event.actor_person_id), (event.subject_person_id),
                  (guardian.tutor_person_id), (guardian.dependent_person_id)) known(person_id)
              UNION ALL
              SELECT CAST(related.value AS uuid)
              FROM jsonb_array_elements_text(COALESCE(event.details -> 'relatedPersonIds', '[]'::jsonb)) related(value)
          ) participant(person_id)
          WHERE participant.person_id IS NOT NULL
              AND (CAST(:personId AS uuid) IS NULL OR participant.person_id = :personId)
      """
          + OPTION_SCOPE_FILTER
          + """
      ORDER BY participant.person_id, event.occurred_at DESC, event.audit_event_id DESC
      ) participants
      LEFT JOIN people person ON person.person_id = participants.person_id
      LEFT JOIN institutions institution ON institution.institution_id = participants.institution_id
      """;
  static final String PEOPLE_PAGE_QUERY =
      OPTION_PAGE_SELECT
          + PEOPLE_QUERY
          + OPTION_SOURCE_SUFFIX
          + OPTION_SEARCH
          + " ORDER BY bemol_normalize_text(options.label), options.\"personId\"";
  static final String PEOPLE_COUNT_QUERY =
      OPTION_COUNT_SELECT + PEOPLE_QUERY + OPTION_SOURCE_SUFFIX + OPTION_SEARCH;
}
