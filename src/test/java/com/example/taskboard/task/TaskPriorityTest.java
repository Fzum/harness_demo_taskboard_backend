package com.example.taskboard.task;

import static com.example.taskboard.task.TaskApi.UNKNOWN_ID;
import static com.example.taskboard.task.TaskApi.body;
import static com.example.taskboard.task.TaskApi.id;
import static com.example.taskboard.task.TaskApi.text;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskboard.task.OutboxTable.Event;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonContent;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** PRIO-1: the task priority through the REST API and the outbox. */
@SpringBootTest
@AutoConfigureMockMvc
class TaskPriorityTest {

  private final TaskApi api;
  private final OutboxTable outbox;

  TaskPriorityTest(MockMvcTester mvc, JdbcTemplate jdbc) {
    this.api = new TaskApi(mvc);
    this.outbox = new OutboxTable(jdbc);
  }

  private static String putBody(String priorityPart) {
    return "{\"title\": \"Clarify returned direct debit\", \"description\": null,"
        + " \"status\": \"OPEN\", \"assignee\": null, \"dueDate\": null"
        + priorityPart
        + "}";
  }

  @Test
  void createWithoutPriorityIsMedium() {
    MvcTestResult result = api.post("/api/tasks", "{\"title\": \"Call back about the claim\"}");

    assertThat(result).hasStatus(HttpStatus.CREATED);
    assertThat(text(result, "$.priority")).isEqualTo("MEDIUM");
  }

  @Test
  void createWithNullPriorityIsMedium() {
    MvcTestResult result =
        api.post("/api/tasks", "{\"title\": \"Call back about the claim\", \"priority\": null}");

    assertThat(result).hasStatus(HttpStatus.CREATED);
    assertThat(text(result, "$.priority")).isEqualTo("MEDIUM");
  }

  @ParameterizedTest
  @ValueSource(strings = {"HIGH", "MEDIUM", "LOW"})
  void createStoresTheGivenPriorityAndGetAndListReportIt(String priority) {
    MvcTestResult created =
        api.create(
            "{\"title\": \"Call back about the claim\", \"priority\": \"" + priority + "\"}");
    long id = id(created);

    assertThat(text(created, "$.priority")).isEqualTo(priority);
    assertThat(text(api.get("/api/tasks/" + id), "$.priority")).isEqualTo(priority);
    List<String> priorities =
        JsonPath.read(body(api.get("/api/tasks")), "$[?(@.id == " + id + ")].priority");
    assertThat(priorities).containsExactly(priority);
  }

  @Test
  void everyTaskOfTheListHasANonNullPriority() {
    api.createTask();

    List<String> priorities = JsonPath.read(body(api.get("/api/tasks")), "$[*].priority");

    assertThat(priorities).isNotEmpty().doesNotContainNull();
    assertThat(priorities).isSubsetOf("HIGH", "MEDIUM", "LOW");
  }

  @Test
  void updateChangesThePriority() {
    long id = api.createTask();

    MvcTestResult result = api.put("/api/tasks/" + id, putBody(", \"priority\": \"LOW\""));

    assertThat(result).hasStatusOk();
    assertThat(text(result, "$.priority")).isEqualTo("LOW");
    assertThat(text(api.get("/api/tasks/" + id), "$.priority")).isEqualTo("LOW");
  }

  @Test
  void updateWithoutPriorityKeepsIt() {
    long id = id(api.create("{\"title\": \"Call back about the claim\", \"priority\": \"HIGH\"}"));

    MvcTestResult omitted = api.put("/api/tasks/" + id, putBody(""));
    assertThat(omitted).hasStatusOk();
    assertThat(text(omitted, "$.priority")).isEqualTo("HIGH");

    MvcTestResult nulled = api.put("/api/tasks/" + id, putBody(", \"priority\": null"));
    assertThat(nulled).hasStatusOk();
    assertThat(text(nulled, "$.priority")).isEqualTo("HIGH");
    assertThat(text(api.get("/api/tasks/" + id), "$.priority")).isEqualTo("HIGH");
  }

  @Test
  void assigningDoesNotTouchThePriority() {
    long id = id(api.create("{\"title\": \"Call back about the claim\", \"priority\": \"LOW\"}"));

    MvcTestResult result =
        api.put("/api/tasks/" + id + "/assignee", "{\"assignee\": \"amueller\"}");

    assertThat(result).hasStatusOk();
    assertThat(text(result, "$.priority")).isEqualTo("LOW");
    assertThat(text(api.get("/api/tasks/" + id), "$.priority")).isEqualTo("LOW");
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"URGENT\"", "\"high\"", "\"\"", "1"})
  void createRejectsAnUnknownPriorityAndWritesNoEvent(String priority) {
    long events = outbox.count();

    assertThat(api.post("/api/tasks", "{\"title\": \"Call back\", \"priority\": " + priority + "}"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .extractingPath("$.errors")
        .isEqualTo(Map.of("priority", "must be one of HIGH, MEDIUM, LOW"));
    assertThat(outbox.count()).isEqualTo(events);
  }

  @Test
  void updateRejectsAnUnknownPriorityAndChangesNothing() {
    long id = id(api.create("{\"title\": \"Call back about the claim\", \"priority\": \"HIGH\"}"));
    int events = outbox.eventsOf(id).size();

    assertThat(api.put("/api/tasks/" + id, putBody(", \"priority\": \"URGENT\"")))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .extractingPath("$.errors")
        .isEqualTo(Map.of("priority", "must be one of HIGH, MEDIUM, LOW"));
    assertThat(outbox.eventsOf(id)).hasSize(events);
    assertThat(text(api.get("/api/tasks/" + id), "$.priority")).isEqualTo("HIGH");
  }

  @Test
  void updateOfAnUnknownTaskStillAnswersNotFoundWithAValidPriority() {
    assertThat(api.put("/api/tasks/" + UNKNOWN_ID, putBody(", \"priority\": \"LOW\"")))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void everyOutboxEventCarriesThePriority() {
    long id = id(api.create("{\"title\": \"Call back about the claim\", \"priority\": \"HIGH\"}"));
    assertThat(new JsonContent(outbox.latestOf(id).payload()))
        .extractingPath("$.priority")
        .isEqualTo("HIGH");

    api.put("/api/tasks/" + id, putBody(", \"priority\": \"LOW\""));
    assertThat(new JsonContent(outbox.latestOf(id).payload()))
        .extractingPath("$.priority")
        .isEqualTo("LOW");

    api.put("/api/tasks/" + id + "/assignee", "{\"assignee\": \"amueller\"}");
    assertThat(new JsonContent(outbox.latestOf(id).payload()))
        .extractingPath("$.priority")
        .isEqualTo("LOW");

    assertThat(api.delete("/api/tasks/" + id)).hasStatus(HttpStatus.NO_CONTENT);
    Event deleted = outbox.latestOf(id);
    assertThat(deleted.eventType()).isEqualTo("TASK_DELETED");
    assertThat(new JsonContent(deleted.payload())).extractingPath("$.priority").isEqualTo("LOW");
  }

  @Test
  void aTaskCreatedWithoutPriorityHasAMediumPriorityInItsEvent() {
    long id = api.createTask();

    assertThat(new JsonContent(outbox.eventsOf(id).getFirst().payload()))
        .extractingPath("$.priority")
        .isEqualTo("MEDIUM");
  }
}
