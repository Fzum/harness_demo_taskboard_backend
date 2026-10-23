package com.example.taskboard.task;

import static com.example.taskboard.task.TaskApi.body;
import static com.example.taskboard.task.TaskApi.id;
import static com.example.taskboard.task.TaskApi.text;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskboard.task.OutboxTable.Event;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
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

/** PRIO-1 AC6, AC7 and the outbox payload: the task priority through the REST API. */
@SpringBootTest
@AutoConfigureMockMvc
class TaskPriorityTest {

  private final TaskApi api;
  private final OutboxTable outbox;
  private final JdbcTemplate jdbc;

  TaskPriorityTest(MockMvcTester mvc, JdbcTemplate jdbc) {
    this.api = new TaskApi(mvc);
    this.outbox = new OutboxTable(jdbc);
    this.jdbc = jdbc;
  }

  @Test
  void createWithoutPriorityIsMedium() {
    MvcTestResult created = api.create("{\"title\": \"Call back\"}");

    assertThat(text(created, "$.priority")).isEqualTo("MEDIUM");
    assertThat(api.get("/api/tasks/" + id(created)))
        .bodyJson()
        .extractingPath("$.priority")
        .isEqualTo("MEDIUM");
  }

  @Test
  void createWithNullPriorityIsMedium() {
    assertThat(api.create("{\"title\": \"Call back\", \"priority\": null}"))
        .bodyJson()
        .extractingPath("$.priority")
        .isEqualTo("MEDIUM");
  }

  @ParameterizedTest
  @ValueSource(strings = {"HIGH", "MEDIUM", "LOW"})
  void createStoresTheGivenPriorityAndEveryReadReportsIt(String priority) {
    MvcTestResult created =
        api.create("{\"title\": \"Call back\", \"priority\": \"%s\"}".formatted(priority));
    long id = id(created);

    assertThat(text(created, "$.priority")).isEqualTo(priority);
    assertThat(text(api.get("/api/tasks/" + id), "$.priority")).isEqualTo(priority);
    List<String> listed =
        JsonPath.read(body(api.get("/api/tasks")), "$[?(@.id == %d)].priority".formatted(id));
    assertThat(listed).containsExactly(priority);
  }

  @Test
  void everyTaskInTheListHasAPriority() {
    api.createTask();

    List<Object> priorities = JsonPath.read(body(api.get("/api/tasks")), "$[*].priority");

    assertThat(priorities).isNotEmpty().doesNotContainNull();
    assertThat(priorities).allSatisfy(p -> assertThat(p).isIn("HIGH", "MEDIUM", "LOW"));
  }

  @Test
  void updateChangesThePriority() {
    long id = id(api.create("{\"title\": \"Call back\", \"priority\": \"HIGH\"}"));

    MvcTestResult updated =
        api.put(
            "/api/tasks/" + id,
            "{\"title\": \"Call back\", \"status\": \"OPEN\", \"priority\": \"LOW\"}");

    assertThat(updated).hasStatusOk().bodyJson().extractingPath("$.priority").isEqualTo("LOW");
    assertThat(text(api.get("/api/tasks/" + id), "$.priority")).isEqualTo("LOW");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"title\": \"Call back\", \"status\": \"DONE\"}",
        "{\"title\": \"Call back\", \"status\": \"DONE\", \"priority\": null}"
      })
  void updateWithoutOrWithNullPriorityKeepsThePriority(String update) {
    long id = id(api.create("{\"title\": \"Call back\", \"priority\": \"HIGH\"}"));

    MvcTestResult updated = api.put("/api/tasks/" + id, update);

    assertThat(updated).hasStatusOk().bodyJson().extractingPath("$.priority").isEqualTo("HIGH");
    assertThat(text(api.get("/api/tasks/" + id), "$.priority")).isEqualTo("HIGH");
    assertThat(JsonPath.<String>read(outbox.latestOf(id).payload(), "$.priority"))
        .isEqualTo("HIGH");
  }

  @Test
  void assigningLeavesThePriorityAlone() {
    long id = id(api.create("{\"title\": \"Call back\", \"priority\": \"LOW\"}"));

    MvcTestResult assigned = api.put("/api/tasks/" + id + "/assignee", "{\"assignee\": \"bkoch\"}");

    assertThat(assigned).hasStatusOk().bodyJson().extractingPath("$.priority").isEqualTo("LOW");
    assertThat(JsonPath.<String>read(outbox.latestOf(id).payload(), "$.priority")).isEqualTo("LOW");
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"URGENT\"", "\"high\"", "\"\"", "1", "true", "[\"HIGH\"]", "{}"})
  void anyOtherPriorityIsRejectedOnCreateAndUpdateNamingTheField(String value) {
    long id = api.createTask();
    long before = outbox.count();

    MvcTestResult create =
        api.post("/api/tasks", "{\"title\": \"Call back\", \"priority\": %s}".formatted(value));
    MvcTestResult update =
        api.put(
            "/api/tasks/" + id,
            "{\"title\": \"Call back\", \"status\": \"OPEN\", \"priority\": %s}".formatted(value));

    for (MvcTestResult result : List.of(create, update)) {
      assertThat(result)
          .hasStatus(HttpStatus.BAD_REQUEST)
          .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
          .bodyJson()
          .extractingPath("$.errors")
          .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
          .containsOnlyKeys("priority");
    }
    assertThat(outbox.count()).isEqualTo(before);
    assertThat(text(api.get("/api/tasks/" + id), "$.priority")).isEqualTo("MEDIUM");
  }

  @Test
  void aRejectedPriorityIsReportedLikeAnInvalidStatus() {
    long id = api.createTask();

    MvcTestResult badStatus =
        api.put("/api/tasks/" + id, "{\"title\": \"Call back\", \"status\": \"CLOSED\"}");
    MvcTestResult badPriority =
        api.put(
            "/api/tasks/" + id,
            "{\"title\": \"Call back\", \"status\": \"OPEN\", \"priority\": \"URGENT\"}");

    assertThat(badPriority).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(badPriority)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo(JsonPath.read(body(badStatus), "$.detail"));
    assertThat(badPriority).bodyJson().extractingPath("$.errors.priority").asString().isNotBlank();
  }

  @Test
  void everyEventCarriesThePriorityOfTheTaskAsEnumName() {
    long id = id(api.create("{\"title\": \"Call back\", \"priority\": \"HIGH\"}"));
    api.put(
        "/api/tasks/" + id,
        "{\"title\": \"Call back\", \"status\": \"OPEN\", \"priority\": \"LOW\"}");
    api.delete("/api/tasks/" + id);

    List<Event> events = outbox.eventsOf(id);

    assertThat(events)
        .extracting(Event::eventType)
        .containsExactly("TASK_CREATED", "TASK_UPDATED", "TASK_DELETED");
    assertThat(events)
        .extracting(e -> JsonPath.<String>read(e.payload(), "$.priority"))
        .containsExactly("HIGH", "LOW", "LOW");
  }

  @Test
  void aTaskCreatedWithoutPriorityHasAMediumEvent() {
    long id = id(api.create("{\"title\": \"Call back\"}"));

    assertThat(new JsonContent(outbox.latestOf(id).payload()))
        .extractingPath("$.priority")
        .isEqualTo("MEDIUM");
  }

  @Test
  void theColumnDefaultsToMediumForRowsInsertedWithoutIt() {
    jdbc.update(
        "INSERT INTO TASK (TITLE, STATUS, SOURCE, HOST_ID, CREATED_AT, UPDATED_AT)"
            + " VALUES ('From host', 'OPEN', 'HOST', 'H000009999', CURRENT_TIMESTAMP,"
            + " CURRENT_TIMESTAMP)");

    assertThat(
            jdbc.queryForObject(
                "SELECT PRIORITY FROM TASK WHERE HOST_ID = 'H000009999'", String.class))
        .isEqualTo("MEDIUM");
    List<Object> reported =
        JsonPath.read(body(api.get("/api/tasks")), "$[?(@.hostId == 'H000009999')].priority");
    assertThat(reported).containsExactly("MEDIUM");
  }
}
