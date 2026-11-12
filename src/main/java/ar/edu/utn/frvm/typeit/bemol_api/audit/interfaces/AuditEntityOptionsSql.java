package ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces;

final class AuditEntityOptionsSql {
  private AuditEntityOptionsSql() {}

  private static final String ENTITY_NAME_FALLBACK =
      "COALESCE(identities.details ->> 'entityName', 'Referencia histórica')";
  private static final String ENTITY_IDENTITY_COLUMNS =
      AuditEventOptionsSql.IDENTITY_FIRST_ACTIVITY_LABEL
          + " AS first_activity_label, "
          + AuditEventOptionsSql.PERSON_LABEL_PREFIX
          + AuditEventOptionsSql.IDENTITY_FIRST_ACTIVITY_LABEL
          + ") AS person_name, "
          + "NULLIF(TRIM(CONCAT_WS(' ', tutor.first_name, tutor.last_name)), '')"
          + " AS tutor_name, "
          + AuditEventOptionsSql.ACCOUNT_LABEL_PREFIX
          + AuditEventOptionsSql.IDENTITY_FIRST_ACTIVITY_LABEL
          + ") AS account_name, "
          + AuditEventOptionsSql.ROLE_LABEL_PREFIX
          + AuditEventOptionsSql.IDENTITY_FIRST_ACTIVITY_LABEL
          + ") AS role_name, "
          + ENTITY_NAME_FALLBACK
          + " AS entity_name, "
          + "institution.name AS institution_name";

  private static final String ENTITIES_QUERY =
      """
      SELECT entities.entity_type AS "entityType", entities.entity_id AS "entityId",
          COALESCE(CAST(:resourceLabels AS jsonb) ->> entities.entity_type, entities.entity_type)
              || ' · ' || CASE entities.entity_type
              WHEN 'PERSON' THEN entities.person_name
              WHEN 'USER' THEN entities.person_name
              WHEN 'PLATFORM_ACCOUNT' THEN entities.account_name
              WHEN 'ROLE' THEN entities.role_name
              WHEN 'PERSON_ACCESS_INVITATION' THEN entities.person_name || ' · ' || entities.first_activity_label
              WHEN 'ENROLLMENT_APPLICATION' THEN entities.person_name || ' · ' || entities.first_activity_label
              WHEN 'PERSON_GUARDIAN' THEN
                  entities.person_name
                      || CASE WHEN entities.tutor_name IS NULL THEN '' ELSE ' · ' || entities.tutor_name END
                      || ' · ' || entities.first_activity_label
              ELSE entities.entity_name
          END AS label,
          entities.institution_name AS "institutionName"
      FROM (
          SELECT identities.*,
      """
          + ENTITY_IDENTITY_COLUMNS
          + "\n"
          + """
      FROM (
          SELECT DISTINCT ON (event.entity_type, event.entity_id)
              event.entity_type, event.entity_id, event.subject_person_id,
              event.institution_id, event.details,
              MIN(event.occurred_at) OVER (PARTITION BY event.entity_type, event.entity_id) AS first_activity
          FROM audit_events event
          WHERE (CAST(:entityType AS varchar) IS NULL OR event.entity_type = :entityType)
              AND (CAST(:entityId AS uuid) IS NULL OR event.entity_id = :entityId)
      """
          + AuditEventOptionsSql.OPTION_SCOPE_FILTER
          + """
      ORDER BY event.entity_type, event.entity_id, event.occurred_at DESC, event.audit_event_id DESC
          ) identities
          LEFT JOIN users user_account ON identities.entity_type = 'USER'
              AND user_account.user_id = identities.entity_id
          LEFT JOIN enrollment_applications application ON identities.entity_type = 'ENROLLMENT_APPLICATION'
              AND application.enrollment_application_id = identities.entity_id
          LEFT JOIN person_access_invitations invitation ON identities.entity_type = 'PERSON_ACCESS_INVITATION'
              AND invitation.invitation_id = identities.entity_id
          LEFT JOIN person_guardians guardian ON identities.entity_type = 'PERSON_GUARDIAN'
              AND guardian.person_guardian_id = identities.entity_id
          LEFT JOIN people person ON person.person_id = CASE identities.entity_type
              WHEN 'PERSON' THEN identities.entity_id
              WHEN 'USER' THEN COALESCE(user_account.person_id, identities.subject_person_id)
              WHEN 'ENROLLMENT_APPLICATION' THEN COALESCE(application.applicant_person_id, identities.subject_person_id)
              WHEN 'PERSON_ACCESS_INVITATION' THEN COALESCE(invitation.person_id, identities.subject_person_id)
              WHEN 'PERSON_GUARDIAN' THEN COALESCE(guardian.dependent_person_id, identities.subject_person_id)
          END
          LEFT JOIN people tutor ON tutor.person_id = guardian.tutor_person_id
          LEFT JOIN platform_accounts account ON identities.entity_type = 'PLATFORM_ACCOUNT'
              AND account.platform_account_id = identities.entity_id
          LEFT JOIN roles role ON identities.entity_type = 'ROLE' AND role.role_id = identities.entity_id
          LEFT JOIN institutions institution ON institution.institution_id = identities.institution_id
      ) entities
      """;
  static final String ENTITIES_PAGE_QUERY =
      AuditEventOptionsSql.OPTION_PAGE_SELECT
          + ENTITIES_QUERY
          + AuditEventOptionsSql.OPTION_SOURCE_SUFFIX
          + AuditEventOptionsSql.OPTION_SEARCH
          + " ORDER BY bemol_normalize_text(options.label), options.\"entityType\", options.\"entityId\"";
  static final String ENTITIES_COUNT_QUERY =
      AuditEventOptionsSql.OPTION_COUNT_SELECT
          + ENTITIES_QUERY
          + AuditEventOptionsSql.OPTION_SOURCE_SUFFIX
          + AuditEventOptionsSql.OPTION_SEARCH;
}
