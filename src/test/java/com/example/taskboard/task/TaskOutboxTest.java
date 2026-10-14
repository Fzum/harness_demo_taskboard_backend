package com.example.taskboard.task;

import static com.example.taskboard.task.TaskApi.UNKNOWN_ID;
import static com.example.taskboard.task.TaskApi.body;
import static com.example.taskboard.task.TaskApi.id;
import static com.example.taskboard.task.TaskApi.instant;
import static com.example.taskboard.task.TaskApi.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

import com.example.taskboard.outbox.Outbox;
import com.example.taskboard.task.OutboxTable.Event;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.h2.api.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonContent;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** The outbox guarantee: one event per change, in the change's transaction. */
@SpringBootTest
@AutoConfigureMockMvc
class TaskOutboxTest {

  private static final String DIRECT_DEBIT =
      """
      {"title": "Clarify returned direct debit",
       "description": "The bank returned the October instalment",
       "assignee": "amueller",
       "dueDate": "2026-10-31"}""";

  private final TaskApi api;
  private final OutboxTable outbox;
  private final JdbcTemplate jdbc;
  private final TaskService service;
  private final Outbox transactionalOutbox;
  private final TransactionTemplate transaction;
  private final String databaseUrl;

  TaskOutboxTest(
      MockMvcTester mvc,
      JdbcTemplate jdbc,
      TaskService service,
      Outbox transactionalOutbox,
      TransactionTemplate transaction,
      @Value("${spring.datasource.url}") String databaseUrl) {
    this.api = new TaskApi(mvc);
    this.outbox = new OutboxTable(jdbc);
    this.jdbc = jdbc;
    this.service = service;
    this.transactionalOutbox = transactionalOutbox;
    this.transaction = transaction;
    this.databaseUrl = databaseUrl;
  }

  @Test
  void createWritesOneTaskCreatedEventWithTheNewTask() {
    MvcTestResult created = api.create(DIRECT_DEBIT);
    long id = id(created);
    Instant createdAt = instant(created, "$.createdAt");

    assertThat(outbox.eventsOf(id))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.aggregateType()).isEqualTo("TASK");
              assertThat(event.eventType()).isEqualTo("TASK_CREATED");
              assertThat(event.status()).isEqualTo("PENDING");
              assertThat(event.attempts()).isZero();
              assertThat(event.lastError()).isNull();
              assertThat(event.processedAt()).isNull();
              assertThat(event.createdAt())
                  .isCloseTo(
                      LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC),
                      within(10, ChronoUnit.SECONDS));
              assertThat(new JsonContent(event.payload()))
                  .isStrictlyEqualTo(
                      """
                      {"taskId": %d,
                       "hostId": null,
                       "title": "Clarify returned direct debit",
                       "description": "The bank returned the October instalment",
                       "status": "OPEN",
                       "assignee": "amueller",
                       "dueDate": "2026-10-31",
                       "source": "TASKBOARD",
                       "occurredAt": "%s"}"""
                          .formatted(id, createdAt));
            });
  }

  @Test
  void updateWritesOneTaskUpdatedEventWithTheWholeNewState() {
    long id = id(api.create(DIRECT_DEBIT));

    MvcTestResult updated =
        api.put(
            "/api/tasks/" + id,
            """
            {"title": "Clarify returned direct debit", "description": null, "status": "DONE",
             "assignee": "amueller", "dueDate": null}""");

    assertThat(outbox.eventsOf(id))
        .extracting(Event::eventType)
        .containsExactly("TASK_CREATED", "TASK_UPDATED");
    assertThat(new JsonContent(outbox.latestOf(id).payload()))
        .isStrictlyEqualTo(
            """
            {"taskId": %d, "hostId": null, "title": "Clarify returned direct debit",
             "description": null, "status": "DONE", "assignee": "amueller", "dueDate": null,
             "source": "TASKBOARD", "occurredAt": "%s"}"""
                .formatted(id, text(updated, "$.updatedAt")));
  }

  @Test
  void assigningAndUnassigningAreUpdates() {
    long id = api.createTask();

    api.put("/api/tasks/" + id + "/assignee", "{\"assignee\": \"bkoch\"}");
    api.put("/api/tasks/" + id + "/assignee", "{\"assignee\": null}");

    List<Event> events = outbox.eventsOf(id);
    assertThat(events)
        .extracting(Event::eventType)
        .containsExactly("TASK_CREATED", "TASK_UPDATED", "TASK_UPDATED");
    assertThat(new JsonContent(events.get(1).payload()))
        .extractingPath("$.assignee")
        .isEqualTo("bkoch");
    assertThat(new JsonContent(events.get(2).payload())).extractingPath("$.assignee").isNull();
  }

  @Test
  void deleteWritesOneTaskDeletedEventWithTheLastStateOfTheTask() {
    MvcTestResult created = api.create(DIRECT_DEBIT);
    long id = id(created);

    assertThat(api.delete("/api/tasks/" + id)).hasStatus(HttpStatus.NO_CONTENT);

    assertThat(outbox.eventsOf(id))
        .extracting(Event::eventType)
        .containsExactly("TASK_CREATED", "TASK_DELETED");
    String payload = outbox.latestOf(id).payload();
    assertThat(new JsonContent(payload))
        .isLenientlyEqualTo(
            """
            {"taskId": %d, "hostId": null, "title": "Clarify returned direct debit",
             "description": "The bank returned the October instalment", "status": "OPEN",
             "assignee": "amueller", "dueDate": "2026-10-31", "source": "TASKBOARD"}"""
                .formatted(id));
    assertThat(Instant.parse(JsonPath.read(payload, "$.occurredAt")))
        .isAfterOrEqualTo(instant(created, "$.updatedAt"));
  }

  @Test
  void aRejectedRequestWritesNoEvent() {
    long id = api.createTask();
    long before = outbox.count();
    String unknown = "/api/tasks/" + UNKNOWN_ID;

    assertThat(api.post("/api/tasks", "{}")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(api.put("/api/tasks/" + id, "{\"title\": \"\"}")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(api.put(unknown, "{\"title\": \"Call back\", \"status\": \"OPEN\"}"))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(api.put(unknown + "/assignee", "{\"assignee\": null}"))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(api.delete(unknown)).hasStatus(HttpStatus.NOT_FOUND);

    assertThat(outbox.count()).isEqualTo(before);
  }

  @Test
  void aRolledBackChangeLeavesNeitherTheTaskNorItsEvent() {
    long id =
        transaction.execute(
            status -> {
              TaskResponse task =
                  service.create(new CreateTaskRequest("Rolled back", null, null, null));
              status.setRollbackOnly();
              return task.id();
            });

    assertThat(api.get("/api/tasks/" + id)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(outbox.eventsOf(id)).isEmpty();
  }

  @Test
  void aChangeThatFailsToCommitTakesItsEventWithIt() {
    MvcTestResult created = api.create(DIRECT_DEBIT);
    long id = id(created);
    // Past the API's validation: the event is inserted, then the UPDATE of TASK fails at commit,
    // because TITLE is a VARCHAR(40).
    UpdateTaskRequest tooLong =
        new UpdateTaskRequest("t".repeat(41), null, TaskStatus.DONE, null, null);

    assertThatThrownBy(() -> service.update(id, tooLong))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(outbox.eventsOf(id)).extracting(Event::eventType).containsExactly("TASK_CREATED");
    assertThat(api.get("/api/tasks/" + id)).bodyJson().isStrictlyEqualTo(body(created));
  }

  @Test
  void theOutboxOnlyWritesInTheTransactionOfAChange() {
    assertThatThrownBy(() -> transactionalOutbox.append("TASK", 1, "TASK_UPDATED", Map.of()))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  @Test
  void aChangeLocksTheTaskUntilItsTransactionEnds() {
    long id = api.createTask();

    transaction.executeWithoutResult(
        status -> {
          service.assign(id, "amueller");

          assertThatThrownBy(() -> assignFromAnotherConnection(id))
              .isInstanceOfSatisfying(
                  SQLException.class,
                  e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LOCK_TIMEOUT_1));
        });

    assertThat(api.get("/api/tasks/" + id))
        .bodyJson()
        .extractingPath("$.assignee")
        .isEqualTo("amueller");
  }

  @Test
  void aChangeThatWaitsForTheLockIsStampedOnceItHasIt() throws Exception {
    long id = api.createTask();
    AtomicReference<Instant> released = new AtomicReference<>();

    CompletableFuture<TaskResponse> waiting =
        transaction.execute(
            status -> {
              service.assign(id, "amueller");
              CompletableFuture<TaskResponse> next =
                  CompletableFuture.supplyAsync(() -> service.assign(id, "bkoch"));
              await().until(this::aSessionWaitsForALock);
              released.set(Instant.now().truncatedTo(ChronoUnit.MILLIS));
              return next;
            });

    // Stamped before it waited, the second change would look older than the first one.
    assertThat(waiting.get(10, TimeUnit.SECONDS).updatedAt()).isAfterOrEqualTo(released.get());
  }

  @Test
  void changesLeaveWhatTheDatasyncWroteAlone() {
    long id = api.createTask();
    String hostKey = "T%09d".formatted(id);
    // The datasync has written the task to the host: it stored the host key and processed the
    // event.
    jdbc.update("UPDATE TASK SET HOST_ID = ? WHERE ID = ?", hostKey, id);
    outbox.markProcessed(outbox.latestOf(id));
    Event processed = outbox.latestOf(id);

    MvcTestResult updated =
        api.put(
            "/api/tasks/" + id,
            """
            {"title": "Call back about the claim", "description": null, "status": "IN_PROGRESS",
             "assignee": "bkoch", "dueDate": null}""");

    assertThat(updated).hasStatusOk().bodyJson().extractingPath("$.hostId").isEqualTo(hostKey);
    assertThat(jdbc.queryForObject("SELECT HOST_ID FROM TASK WHERE ID = ?", String.class, id))
        .isEqualTo(hostKey);
    List<Event> events = outbox.eventsOf(id);
    assertThat(events).hasSize(2);
    assertThat(events.getFirst()).isEqualTo(processed);
    assertThat(new JsonContent(events.getLast().payload()))
        .extractingPath("$.hostId")
        .isEqualTo(hostKey);
  }

  @Test
  void aChangeNeverWritesTheHostId() {
    long id = api.createTask();
    String hostKey = "T%09d".formatted(id);

    transaction.executeWithoutResult(
        status -> {
          service.assign(id, "bkoch");
          // The host key arrives after the change has read the task. Only the change's own
          // transaction can store it now: the row lock keeps the datasync waiting.
          jdbc.update("UPDATE TASK SET HOST_ID = ? WHERE ID = ?", hostKey, id);
        });

    assertThat(jdbc.queryForObject("SELECT HOST_ID FROM TASK WHERE ID = ?", String.class, id))
        .isEqualTo(hostKey);
  }

  /** A concurrent change of the task: another connection, which gives up after 100 ms. */
  private void assignFromAnotherConnection(long id) throws SQLException {
    try (Connection connection = DriverManager.getConnection(databaseUrl, "sa", "");
        Statement statement = connection.createStatement()) {
      statement.execute("SET LOCK_TIMEOUT 100");
      statement.executeUpdate("UPDATE TASK SET ASSIGNEE = 'bkoch' WHERE ID = " + id);
    }
  }

  private boolean aSessionWaitsForALock() {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID IS NOT NULL",
            Integer.class)
        > 0;
  }
}
