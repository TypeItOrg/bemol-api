package ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventOptionsQueries {
  @Query(
      value = AuditEventOptionsSql.ACTORS_PAGE_QUERY,
      countQuery = AuditEventOptionsSql.ACTORS_COUNT_QUERY,
      nativeQuery = true)
  Page<AuditActorOptionProjection> searchActors(
      @Param("actorKind") @Nullable String actorKind,
      @Param("institutionId") @Nullable UUID institutionId,
      @Param("search") @Nullable String search,
      Pageable pageable);

  @Query(
      value = AuditEventOptionsSql.SUBJECTS_PAGE_QUERY,
      countQuery = AuditEventOptionsSql.SUBJECTS_COUNT_QUERY,
      nativeQuery = true)
  Page<AuditSubjectOptionProjection> searchSubjects(
      @Param("scope") @Nullable String scope,
      @Param("institutionId") @Nullable UUID institutionId,
      @Param("subjectPersonId") @Nullable UUID subjectPersonId,
      @Param("search") @Nullable String search,
      Pageable pageable);

  @Query(
      value = AuditEventOptionsSql.PEOPLE_PAGE_QUERY,
      countQuery = AuditEventOptionsSql.PEOPLE_COUNT_QUERY,
      nativeQuery = true)
  Page<AuditPersonOptionProjection> searchPeople(
      @Param("scope") @Nullable String scope,
      @Param("institutionId") @Nullable UUID institutionId,
      @Param("personId") @Nullable UUID personId,
      @Param("search") @Nullable String search,
      Pageable pageable);

  @Query(
      value = AuditEntityOptionsSql.ENTITIES_PAGE_QUERY,
      countQuery = AuditEntityOptionsSql.ENTITIES_COUNT_QUERY,
      nativeQuery = true)
  Page<AuditEntityOptionProjection> searchEntities(
      @Param("scope") @Nullable String scope,
      @Param("institutionId") @Nullable UUID institutionId,
      @Param("entityType") @Nullable String entityType,
      @Param("entityId") @Nullable UUID entityId,
      @Param("search") @Nullable String search,
      @Param("resourceLabels") String resourceLabels,
      Pageable pageable);
}
