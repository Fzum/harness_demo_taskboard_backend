package com.example.taskboard.task;

import static com.example.taskboard.task.TaskApi.body;
import static com.example.taskboard.task.TaskApi.text;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * The sync status: the status of the task's latest outbox event, as the datasync sets it. Every
 * check reads both GET /api/tasks/{id} and GET /api/tasks.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskSyncStatusTest {

  private final TaskApi api;
  private final OutboxTable outbox;
  private final JdbcTemplate jdbc;
  private final Statistics statistics;

  TaskSyncStatusTest(
      MockMvcTester mvc, JdbcTemplate jdbc, EntityManagerFactory entityManagerFactory) {
    this.api = new TaskApi(mvc);
    this.outbox = new OutboxTable(jdbc);
    this.jdbc = jdbc;
    this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
  }

  @Test
  void aChangedTaskIsPendingUntilTheDatasyncHasWrittenItToTheHost() {
    long id = api.createTask();
    assertThat(syncStatusOf(id)).isEqualTo("PENDING");

    outbox.markProcessed(outbox.latestOf(id));

    assertThat(syncStatusOf(id)).isEqualTo("SYNCED");
  }

  @Test
  void aTaskWhoseLatestEventFailedIsAHostInconsistency() {
    long id = api.createTask();

    outbox.markFailed(outbox.latestOf(id));

    assertThat(syncStatusOf(id)).isEqualTo("FAILED");
  }

  @Test
  void onlyTheLatestEventCounts() {
    long id = api.createTask();
    outbox.markFailed(outbox.latestOf(id));

    api.put("/api/tasks/" + id + "/assignee", "{\"assignee\": \"bkoch\"}");
    assertThat(syncStatusOf(id)).isEqualTo("PENDING");

    outbox.markProcessed(outbox.latestOf(id));
    assertThat(syncStatusOf(id)).isEqualTo("SYNCED");
  }

  @Test
  void aTaskLoadedFromTheHostAndUnchangedSinceIsSynced() {
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    // What the datasync's ETL writes: a host task without any outbox event.
    long id =
        new SimpleJdbcInsert(jdbc)
            .withTableName("TASK")
            .usingGeneratedKeyColumns("ID")
            .executeAndReturnKey(
                Map.of(
                    "TITLE", "Renew the standing order",
                    "STATUS", "OPEN",
                    "ASSIGNEE", "amueller",
                    "SOURCE", "HOST",
                    "HOST_ID", "H000001001",
                    "CREATED_AT", now,
                    "UPDATED_AT", now))
            .longValue();

    assertThat(outbox.eventsOf(id)).isEmpty();
    assertThat(syncStatusOf(id)).isEqualTo("SYNCED");
    assertThat(api.get("/api/tasks/" + id))
        .bodyJson()
        .isLenientlyEqualTo("{\"source\": \"HOST\", \"hostId\": \"H000001001\"}");
  }

  @Test
  void listingTheTasksTakesOneQuery() {
    outbox.markProcessed(outbox.latestOf(api.createTask()));
    outbox.markFailed(outbox.latestOf(api.createTask()));
    api.createTask();

    statistics.clear();
    statistics.setStatisticsEnabled(true);
    try {
      assertThat(api.get("/api/tasks")).hasStatusOk();
      assertThat(statistics.getPrepareStatementCount()).isOne();
    } finally {
      statistics.setStatisticsEnabled(false);
    }
  }

  private String syncStatusOf(long id) {
    String one = text(api.get("/api/tasks/" + id), "$.syncStatus");
    List<String> inList =
        JsonPath.read(body(api.get("/api/tasks")), "$[?(@.id == %d)].syncStatus".formatted(id));
    assertThat(inList).containsExactly(one);
    return one;
  }
}
