package ar.edu.utn.frvm.typeit.boero_api.audit.interfaces;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventOptionsQueries {
  String ACTORS_QUERY =
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
  String ACTORS_SEARCH =
      """
      WHERE (CAST(:search AS varchar) IS NULL OR STRPOS(
          boero_normalize_text(actors.label), boero_normalize_text(CAST(:search AS varchar))) > 0)
      """;

  @Query(
      value =
          "SELECT actors.* FROM ("
              + ACTORS_QUERY
              + ") actors "
              + ACTORS_SEARCH
              + " ORDER BY LOWER(actors.label), actors.\"actorKind\", actors.\"actorId\"",
      countQuery = "SELECT COUNT(*) FROM (" + ACTORS_QUERY + ") actors " + ACTORS_SEARCH,
      nativeQuery = true)
  Page<AuditActorOptionProjection> searchActors(
      @Param("actorKind") @Nullable String actorKind,
      @Param("institutionId") @Nullable UUID institutionId,
      @Param("search") @Nullable String search,
      Pageable pageable);

  String OPTION_SCOPE_FILTER =
      """
          AND event.entity_type <> 'EMAIL'
          AND (CAST(:scope AS varchar) IS NULL
              OR (:scope = 'PLATFORM' AND event.institution_id IS NULL)
              OR (:scope = 'INSTITUTION' AND event.institution_id IS NOT NULL))
          AND (CAST(:institutionId AS uuid) IS NULL OR event.institution_id = :institutionId)
      """;
  String OPTION_SEARCH =
      """
      WHERE (CAST(:search AS varchar) IS NULL OR STRPOS(
          boero_normalize_text(options.label || ' ' || COALESCE(options."institutionName", '')),
          boero_normalize_text(CAST(:search AS varchar))) > 0)
      """;
  String SUBJECTS_QUERY =
      """
      SELECT subjects.subject_person_id AS "subjectPersonId",
          COALESCE(NULLIF(TRIM(CONCAT_WS(' ', person.first_name, person.last_name)), ''),
              'Persona histórica · ' || TO_CHAR(subjects.first_activity
                  AT TIME ZONE 'America/Argentina/Cordoba', 'DD/MM/YYYY HH24:MI:SS')) AS label,
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

  @Query(
      value =
          "SELECT options.* FROM ("
              + SUBJECTS_QUERY
              + ") options "
              + OPTION_SEARCH
              + " ORDER BY boero_normalize_text(options.label), options.\"subjectPersonId\"",
      countQuery = "SELECT COUNT(*) FROM (" + SUBJECTS_QUERY + ") options " + OPTION_SEARCH,
      nativeQuery = true)
  Page<AuditSubjectOptionProjection> searchSubjects(
      @Param("scope") @Nullable String scope,
      @Param("institutionId") @Nullable UUID institutionId,
      @Param("subjectPersonId") @Nullable UUID subjectPersonId,
      @Param("search") @Nullable String search,
      Pageable pageable);

  String PEOPLE_QUERY =
      """
      SELECT participants.person_id AS "personId",
          COALESCE(NULLIF(TRIM(CONCAT_WS(' ', person.first_name, person.last_name)), ''),
              'Persona histórica · ' || TO_CHAR(participants.first_activity
                  AT TIME ZONE 'America/Argentina/Cordoba', 'DD/MM/YYYY HH24:MI:SS')) AS label,
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

  @Query(
      value =
          "SELECT options.* FROM ("
              + PEOPLE_QUERY
              + ") options "
              + OPTION_SEARCH
              + " ORDER BY boero_normalize_text(options.label), options.\"personId\"",
      countQuery = "SELECT COUNT(*) FROM (" + PEOPLE_QUERY + ") options " + OPTION_SEARCH,
      nativeQuery = true)
  Page<AuditPersonOptionProjection> searchPeople(
      @Param("scope") @Nullable String scope,
      @Param("institutionId") @Nullable UUID institutionId,
      @Param("personId") @Nullable UUID personId,
      @Param("search") @Nullable String search,
      Pageable pageable);

  String ENTITIES_QUERY =
      """
      SELECT entities.entity_type AS "entityType", entities.entity_id AS "entityId",
          CASE entities.entity_type
              WHEN 'PERSON' THEN 'Persona · ' || entities.person_name
              WHEN 'USER' THEN 'Cuenta institucional · ' || entities.person_name
              WHEN 'PLATFORM_ACCOUNT' THEN 'Cuenta de plataforma · ' || entities.account_name
              WHEN 'ROLE' THEN 'Rol · ' || entities.role_name
              WHEN 'INSTITUTION' THEN 'Institución · ' || COALESCE(entities.details ->> 'entityName', 'Referencia histórica')
              WHEN 'COURSE' THEN 'Curso · ' || COALESCE(entities.details ->> 'entityName', 'Referencia histórica')
              WHEN 'STUDY_PLAN' THEN 'Plan de estudio · ' || COALESCE(entities.details ->> 'entityName', 'Referencia histórica')
              WHEN 'STUDY_PLAN_SPACE' THEN 'Espacio de plan · ' || COALESCE(entities.details ->> 'entityName', 'Referencia histórica')
              WHEN 'ACADEMIC_LEVEL' THEN 'Nivel de plan · ' || COALESCE(entities.details ->> 'entityName', 'Referencia histórica')
              WHEN 'PREREQUISITE' THEN 'Correlatividad · ' || COALESCE(entities.details ->> 'entityName', 'Referencia histórica')
              WHEN 'DOCUMENT_DEFINITION' THEN 'Documento · ' || COALESCE(entities.details ->> 'entityName', 'Referencia histórica')
              WHEN 'PERSON_ACCESS_INVITATION' THEN
                  'Invitación de acceso · ' || entities.person_name || ' · ' || entities.first_activity_label
              WHEN 'ENROLLMENT_APPLICATION' THEN
                  'Inscripción · ' || entities.person_name || ' · ' || entities.first_activity_label
              WHEN 'PERSON_GUARDIAN' THEN
                  'Vinculación · ' || entities.person_name
                      || CASE WHEN entities.tutor_name IS NULL THEN '' ELSE ' · ' || entities.tutor_name END
                      || ' · ' || entities.first_activity_label
          END AS label,
          entities.institution_name AS "institutionName"
      FROM (
          SELECT identities.*,
              TO_CHAR(identities.first_activity AT TIME ZONE 'America/Argentina/Cordoba',
                  'DD/MM/YYYY HH24:MI:SS') AS first_activity_label,
              COALESCE(NULLIF(TRIM(CONCAT_WS(' ', person.first_name, person.last_name)), ''),
                  'Persona histórica · ' || TO_CHAR(identities.first_activity
                      AT TIME ZONE 'America/Argentina/Cordoba', 'DD/MM/YYYY HH24:MI:SS')) AS person_name,
              NULLIF(TRIM(CONCAT_WS(' ', tutor.first_name, tutor.last_name)), '') AS tutor_name,
              COALESCE(NULLIF(TRIM(CONCAT_WS(' ', account.first_name, account.last_name)), ''),
                  'Cuenta histórica · ' || TO_CHAR(identities.first_activity
                      AT TIME ZONE 'America/Argentina/Cordoba', 'DD/MM/YYYY HH24:MI:SS')) AS account_name,
              COALESCE(role.name, identities.details -> 'after' ->> 'name',
                  identities.details -> 'before' ->> 'name',
                  'Rol histórico · ' || TO_CHAR(identities.first_activity
                      AT TIME ZONE 'America/Argentina/Cordoba', 'DD/MM/YYYY HH24:MI:SS')) AS role_name,
              institution.name AS institution_name
          FROM (
              SELECT DISTINCT ON (event.entity_type, event.entity_id)
                  event.entity_type, event.entity_id, event.subject_person_id,
                  event.institution_id, event.details,
                  MIN(event.occurred_at) OVER (PARTITION BY event.entity_type, event.entity_id) AS first_activity
              FROM audit_events event
              WHERE (CAST(:entityType AS varchar) IS NULL OR event.entity_type = :entityType)
                  AND (CAST(:entityId AS uuid) IS NULL OR event.entity_id = :entityId)
      """
          + OPTION_SCOPE_FILTER
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

  @Query(
      value =
          "SELECT options.* FROM ("
              + ENTITIES_QUERY
              + ") options "
              + OPTION_SEARCH
              + " ORDER BY boero_normalize_text(options.label), options.\"entityType\", options.\"entityId\"",
      countQuery = "SELECT COUNT(*) FROM (" + ENTITIES_QUERY + ") options " + OPTION_SEARCH,
      nativeQuery = true)
  Page<AuditEntityOptionProjection> searchEntities(
      @Param("scope") @Nullable String scope,
      @Param("institutionId") @Nullable UUID institutionId,
      @Param("entityType") @Nullable String entityType,
      @Param("entityId") @Nullable UUID entityId,
      @Param("search") @Nullable String search,
      Pageable pageable);
}
