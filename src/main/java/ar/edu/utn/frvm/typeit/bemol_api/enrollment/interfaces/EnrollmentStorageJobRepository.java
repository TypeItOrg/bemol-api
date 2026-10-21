package ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.common.storage.StorageService.Destination;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentDocumentAudit.Actor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EnrollmentStorageJobRepository {
  private final JdbcTemplate jdbc;

  public record Job(
      UUID id,
      Destination destination,
      String storagePath,
      UUID institutionId,
      UUID applicationId,
      @Nullable UUID attachmentId,
      Actor actor,
      String state,
      int attempts) {}

  public UUID insert(
      final Destination destination,
      final String path,
      final UUID institutionId,
      final UUID applicationId,
      final @Nullable UUID attachmentId,
      final Actor actor,
      final Instant now,
      final Instant due) {
    final UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO enrollment_storage_jobs
          (id, created_at, updated_at, state, provider, location, prefix, storage_path,
           institution_id, application_id, attachment_id, actor_id, account_type, request_id, next_attempt_at)
        VALUES (?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        Timestamp.from(now),
        Timestamp.from(now),
        destination.provider(),
        destination.location(),
        destination.prefix(),
        path,
        institutionId,
        applicationId,
        attachmentId,
        actor.id(),
        actor.accountType(),
        actor.requestId(),
        Timestamp.from(due));
    return id;
  }

  public Optional<Job> lock(final UUID id) {
    return jdbc
        .query("SELECT * FROM enrollment_storage_jobs WHERE id = ? FOR UPDATE", this::map, id)
        .stream()
        .findFirst();
  }

  public Optional<Job> lockAttachment(final @Nullable UUID attachmentId) {
    return jdbc
        .query(
            "SELECT * FROM enrollment_storage_jobs WHERE attachment_id = ? FOR UPDATE",
            this::map,
            attachmentId)
        .stream()
        .findFirst();
  }

  public Optional<Job> next(final Instant now) {
    return jdbc
        .query(
            """
        SELECT * FROM enrollment_storage_jobs
        WHERE state = 'PENDING' AND next_attempt_at <= ?
        ORDER BY next_attempt_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
        """,
            this::map,
            Timestamp.from(now))
        .stream()
        .findFirst();
  }

  public boolean hasDifferentDestination(final Destination destination) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
        SELECT EXISTS (SELECT 1 FROM enrollment_storage_jobs WHERE state <> 'DONE'
          AND (provider <> ? OR location <> ? OR prefix <> ?))
        """,
            Boolean.class,
            destination.provider(),
            destination.location(),
            destination.prefix()));
  }

  public Optional<UUID> activeReference(final String path) {
    return jdbc
        .query(
            """
        SELECT enrollment_attachment_id FROM enrollment_attachments
        WHERE storage_path = ? AND deleted_at IS NULL LIMIT 1
        """,
            (rs, row) -> rs.getObject(1, UUID.class),
            path)
        .stream()
        .findFirst();
  }

  public void activate(final UUID id, final @Nullable UUID attachmentId, final Instant now) {
    jdbc.update(
        """
        UPDATE enrollment_storage_jobs SET state = 'ACTIVE', attachment_id = ?, updated_at = ?,
          next_attempt_at = NULL, last_error = NULL WHERE id = ?
        """,
        attachmentId,
        Timestamp.from(now),
        id);
  }

  public void queue(final UUID id, final Actor actor, final Instant now) {
    jdbc.update(
        """
        UPDATE enrollment_storage_jobs SET state = 'PENDING', updated_at = ?, next_attempt_at = ?,
          actor_id = ?, account_type = ?, request_id = ?, attempts = 0, last_error = NULL WHERE id = ?
        """,
        Timestamp.from(now),
        Timestamp.from(now),
        actor.id(),
        actor.accountType(),
        actor.requestId(),
        id);
  }

  public void complete(final UUID id, final Instant now) {
    jdbc.update(
        """
        UPDATE enrollment_storage_jobs SET state = 'DONE', updated_at = ?, next_attempt_at = NULL,
          last_error = NULL WHERE id = ?
        """,
        Timestamp.from(now),
        id);
  }

  public void retry(final Job job, final Instant now, final String error) {
    final long delaySeconds = Math.min(3600, 60L << Math.min(job.attempts(), 6));
    jdbc.update(
        """
        UPDATE enrollment_storage_jobs SET attempts = attempts + 1, updated_at = ?, next_attempt_at = ?,
          last_error = ? WHERE id = ?
        """,
        Timestamp.from(now),
        Timestamp.from(now.plusSeconds(delaySeconds)),
        error,
        job.id());
  }

  private Job map(final ResultSet rs, final int row) throws SQLException {
    return new Job(
        rs.getObject("id", UUID.class),
        new Destination(rs.getString("provider"), rs.getString("location"), rs.getString("prefix")),
        rs.getString("storage_path"),
        rs.getObject("institution_id", UUID.class),
        rs.getObject("application_id", UUID.class),
        rs.getObject("attachment_id", UUID.class),
        new Actor(
            rs.getObject("actor_id", UUID.class),
            rs.getString("account_type"),
            rs.getString("request_id")),
        rs.getString("state"),
        rs.getInt("attempts"));
  }
}
