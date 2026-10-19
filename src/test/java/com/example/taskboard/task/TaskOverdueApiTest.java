package com.example.taskboard.task;

import static com.example.taskboard.task.TaskApi.id;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The computed {@code overdue} field of task responses (smoke-1, AC3). */
@SpringBootTest
@AutoConfigureMockMvc
class TaskOverdueApiTest {

  private final TaskApi api;
  private final JdbcTemplate jdbc;

  TaskOverdueApiTest(MockMvcTester mvc, JdbcTemplate jdbc) {
    this.api = new TaskApi(mvc);
    this.jdbc = jdbc;
  }

  private MvcTestResult createWithDue(String dueJson) {
    return api.create("{\"title\": \"Overdue check\", \"dueDate\": " + dueJson + "}");
  }

  @Test
  void aTaskDueBeforeTodayIsOverdue() {
    MvcTestResult created = createWithDue("\"2020-01-01\"");

    assertThat(created).bodyJson().extractingPath("$.overdue").isEqualTo(true);
    assertThat(api.get("/api/tasks/" + id(created)))
        .bodyJson()
        .extractingPath("$.overdue")
        .isEqualTo(true);
  }

  @Test
  void aTaskDueYesterdayIsOverdue() {
    MvcTestResult created = createWithDue("\"" + LocalDate.now().minusDays(1) + "\"");

    assertThat(created).bodyJson().extractingPath("$.overdue").isEqualTo(true);
  }

  @ParameterizedTest
  @ValueSource(strings = {"today", "tomorrow", "farFuture"})
  void aTaskDueTodayOrLaterIsNotOverdue(String when) {
    String date =
        switch (when) {
          case "today" -> LocalDate.now().toString();
          case "tomorrow" -> LocalDate.now().plusDays(1).toString();
          default -> "9999-12-31";
        };

    MvcTestResult created = createWithDue("\"" + date + "\"");

    assertThat(created).bodyJson().extractingPath("$.overdue").isEqualTo(false);
  }

  @Test
  void aTaskWithoutDueDateIsNotOverdueAndTheFieldIsNeverNull() {
    MvcTestResult created = createWithDue("null");

    assertThat(created).bodyJson().extractingPath("$.dueDate").isNull();
    assertThat(created).bodyJson().extractingPath("$.overdue").isEqualTo(false);
  }

  @Test
  void aDoneTaskThatIsLateIsStillOverdue() {
    long id = id(createWithDue("\"2020-01-01\""));

    MvcTestResult done =
        api.put(
            "/api/tasks/" + id,
            "{\"title\": \"Overdue check\", \"status\": \"DONE\", \"dueDate\": \"2020-01-01\"}");

    assertThat(done).hasStatus(HttpStatus.OK);
    assertThat(done).bodyJson().extractingPath("$.status").isEqualTo("DONE");
    assertThat(done).bodyJson().extractingPath("$.overdue").isEqualTo(true);
  }

  @Test
  void editingTheDueDateRecomputesOverdue() {
    long id = id(createWithDue("\"2020-01-01\""));

    MvcTestResult edited =
        api.put(
            "/api/tasks/" + id,
            "{\"title\": \"Overdue check\", \"dueDate\": \"" + LocalDate.now().plusDays(5) + "\"}");

    assertThat(edited).hasStatus(HttpStatus.OK);
    assertThat(edited).bodyJson().extractingPath("$.overdue").isEqualTo(false);
  }

  @Test
  void theListCarriesOverdueForEveryTaskIncludingHostSeededOnes() {
    jdbc.update(
        "INSERT INTO TASK (TITLE, STATUS, SOURCE, HOST_ID, DUE_DATE, CREATED_AT, UPDATED_AT)"
            + " VALUES ('Host late', 'OPEN', 'HOST', 'H900000001', DATE '2020-01-01',"
            + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    jdbc.update(
        "INSERT INTO TASK (TITLE, STATUS, SOURCE, HOST_ID, DUE_DATE, CREATED_AT, UPDATED_AT)"
            + " VALUES ('Host later', 'OPEN', 'HOST', 'H900000002', DATE '9999-12-31',"
            + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    jdbc.update(
        "INSERT INTO TASK (TITLE, STATUS, SOURCE, HOST_ID, CREATED_AT, UPDATED_AT)"
            + " VALUES ('Host open ended', 'OPEN', 'HOST', 'H900000003',"
            + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

    MvcTestResult list = api.get("/api/tasks");

    assertThat(list).hasStatus(HttpStatus.OK);
    String body = TaskApi.body(list);
    List<Map<String, Object>> tasks = JsonPath.read(body, "$");
    assertThat(tasks).isNotEmpty();
    assertThat(tasks).allSatisfy(t -> assertThat(t.get("overdue")).isInstanceOf(Boolean.class));
    assertThat(tasks)
        .filteredOn(t -> "H900000001".equals(t.get("hostId")))
        .singleElement()
        .satisfies(t -> assertThat(t.get("overdue")).isEqualTo(true));
    assertThat(tasks)
        .filteredOn(t -> "H900000002".equals(t.get("hostId")))
        .singleElement()
        .satisfies(t -> assertThat(t.get("overdue")).isEqualTo(false));
    assertThat(tasks)
        .filteredOn(t -> "H900000003".equals(t.get("hostId")))
        .singleElement()
        .satisfies(t -> assertThat(t.get("overdue")).isEqualTo(false));
  }
}
