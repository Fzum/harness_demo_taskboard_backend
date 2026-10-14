package com.example.taskboard.task;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * OUTBOX_EVENT through plain JDBC, as the datasync sees it, and the datasync's updates of the
 * events it relays.
 */
final class OutboxTable {

  /** A row of OUTBOX_EVENT; its timestamps are UTC. */
  record Event(
      long id,
      String aggregateType,
      long aggregateId,
      String eventType,
      String payload,
      String status,
      int attempts,
      String lastError,
      LocalDateTime createdAt,
      LocalDateTime processedAt) {}

  private static final RowMapper<Event> EVENT =
      (rs, row) ->
          new Event(
              rs.getLong("ID"),
              rs.getString("AGGREGATE_TYPE"),
              rs.getLong("AGGREGATE_ID"),
              rs.getString("EVENT_TYPE"),
              rs.getString("PAYLOAD"),
              rs.getString("STATUS"),
              rs.getInt("ATTEMPTS"),
              rs.getString("LAST_ERROR"),
              rs.getObject("CREATED_AT", LocalDateTime.class),
              rs.getObject("PROCESSED_AT", LocalDateTime.class));

  private final JdbcTemplate jdbc;

  OutboxTable(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** The task's events, oldest first. */
  List<Event> eventsOf(long taskId) {
    return jdbc.query(
        "SELECT * FROM OUTBOX_EVENT WHERE AGGREGATE_ID = ? ORDER BY ID", EVENT, taskId);
  }

  Event latestOf(long taskId) {
    return eventsOf(taskId).getLast();
  }

  long count() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM OUTBOX_EVENT", Long.class);
  }

  /** What the datasync does once it has written the event to the host. */
  void markProcessed(Event event) {
    jdbc.update(
        "UPDATE OUTBOX_EVENT SET STATUS = 'PROCESSED', ATTEMPTS = ATTEMPTS + 1, PROCESSED_AT = ?"
            + " WHERE ID = ?",
        LocalDateTime.now(ZoneOffset.UTC),
        event.id());
  }

  /** What the datasync does when it gives up on the event. */
  void markFailed(Event event) {
    jdbc.update(
        "UPDATE OUTBOX_EVENT SET STATUS = 'FAILED', ATTEMPTS = 5, LAST_ERROR = ? WHERE ID = ?",
        "CICS program TSKUPDT unavailable",
        event.id());
  }
}
