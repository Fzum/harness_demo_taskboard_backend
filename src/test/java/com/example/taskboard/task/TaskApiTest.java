package com.example.taskboard.task;

import static com.example.taskboard.task.TaskApi.UNKNOWN_ID;
import static com.example.taskboard.task.TaskApi.body;
import static com.example.taskboard.task.TaskApi.id;
import static com.example.taskboard.task.TaskApi.instant;
import static com.example.taskboard.task.TaskApi.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
class TaskApiTest {

  private static final String DIRECT_DEBIT =
      """
      {"title": "Clarify returned direct debit",
       "description": "The bank returned the October instalment",
       "assignee": "amueller",
       "dueDate": "2026-10-31"}""";

  private static final String USER_ID_MESSAGE =
      "must be a lower-case user id: a letter, then up to 7 letters or digits";

  private final MockMvcTester mvc;
  private final TaskApi api;
  private final JdbcTemplate jdbc;

  TaskApiTest(MockMvcTester mvc, JdbcTemplate jdbc) {
    this.mvc = mvc;
    this.api = new TaskApi(mvc);
    this.jdbc = jdbc;
  }

  @Test
  void createAnswersWithTheNewOpenTaskAndItsLocation() {
    MvcTestResult result = api.post("/api/tasks", DIRECT_DEBIT);

    assertThat(result).hasStatus(HttpStatus.CREATED);
    long id = id(result);
    String createdAt = text(result, "$.createdAt");
    assertThat(result).hasHeader(HttpHeaders.LOCATION, "/api/tasks/" + id);
    assertThat(result)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"id": %d,
             "title": "Clarify returned direct debit",
             "description": "The bank returned the October instalment",
             "status": "OPEN",
             "assignee": "amueller",
             "dueDate": "2026-10-31",
             "source": "TASKBOARD",
             "hostId": null,
             "syncStatus": "PENDING",
             "createdAt": "%s",
             "updatedAt": "%s"}"""
                .formatted(id, createdAt, createdAt));
    assertThat(Instant.parse(createdAt)).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
  }

  @Test
  void createNeedsNothingButATitle() {
    MvcTestResult result = api.post("/api/tasks", "{\"title\": \"Call back about the claim\"}");

    assertThat(result)
        .hasStatus(HttpStatus.CREATED)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"title": "Call back about the claim", "description": null, "status": "OPEN",
             "assignee": null, "dueDate": null, "source": "TASKBOARD", "hostId": null}""");
  }

  @Test
  void getAnswersWithTheTaskAsCreateDid() {
    MvcTestResult created = api.create(DIRECT_DEBIT);

    MvcTestResult result = api.get("/api/tasks/" + id(created));

    assertThat(result).hasStatusOk().bodyJson().isStrictlyEqualTo(body(created));
  }

  @Test
  void listAnswersWithEveryTaskSortedById() {
    long first = api.createTask();
    long second = api.createTask();

    MvcTestResult result = api.get("/api/tasks");

    assertThat(result).hasStatusOk();
    List<Number> ids = JsonPath.read(body(result), "$[*].id");
    assertThat(ids).extracting(Number::longValue).contains(first, second).isSorted();
  }

  @Test
  void updateReplacesEveryEditableFieldAndNullClearsOne() {
    MvcTestResult created = api.create(DIRECT_DEBIT);
    long id = id(created);

    MvcTestResult result =
        api.put(
            "/api/tasks/" + id,
            """
            {"title": "Clarify returned direct debits",
             "description": null,
             "status": "IN_PROGRESS",
             "assignee": "bkoch",
             "dueDate": null}""");

    assertThat(result)
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"id": %d, "title": "Clarify returned direct debits", "description": null,
             "status": "IN_PROGRESS", "assignee": "bkoch", "dueDate": null, "source": "TASKBOARD",
             "hostId": null, "syncStatus": "PENDING", "createdAt": "%s"}"""
                .formatted(id, text(created, "$.createdAt")));
    assertThat(instant(result, "$.updatedAt")).isAfterOrEqualTo(instant(created, "$.updatedAt"));
    assertThat(api.get("/api/tasks/" + id)).bodyJson().isStrictlyEqualTo(body(result));
  }

  @Test
  void updateClearsAFieldWhoseKeyIsMissing() {
    long id = id(api.create(DIRECT_DEBIT));

    MvcTestResult result =
        api.put(
            "/api/tasks/" + id,
            "{\"title\": \"Clarify returned direct debit\", \"status\": \"DONE\"}");

    assertThat(result)
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"title": "Clarify returned direct debit", "description": null, "status": "DONE",
             "assignee": null, "dueDate": null}""");
  }

  @Test
  void assigneeAssignsAndUnassignsTheTask() {
    long id = id(api.create(DIRECT_DEBIT));

    MvcTestResult assigned = api.put("/api/tasks/" + id + "/assignee", "{\"assignee\": \"bkoch\"}");

    assertThat(assigned)
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"id": %d, "title": "Clarify returned direct debit",
             "description": "The bank returned the October instalment",
             "status": "OPEN", "assignee": "bkoch", "dueDate": "2026-10-31"}"""
                .formatted(id));

    MvcTestResult unassigned = api.put("/api/tasks/" + id + "/assignee", "{\"assignee\": null}");

    assertThat(unassigned).hasStatusOk().bodyJson().extractingPath("$.assignee").isNull();
  }

  @Test
  void deleteRemovesTheTask() {
    long id = api.createTask();

    assertThat(api.delete("/api/tasks/" + id)).hasStatus(HttpStatus.NO_CONTENT);

    assertThat(api.get("/api/tasks/" + id)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(api.delete("/api/tasks/" + id)).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void anUnknownTaskIsANotFoundProblem() {
    String uri = "/api/tasks/" + UNKNOWN_ID;
    String update = "{\"title\": \"Call back about the claim\", \"status\": \"OPEN\"}";

    assertNotFound(api.get(uri), uri);
    assertNotFound(api.put(uri, update), uri);
    assertNotFound(api.put(uri + "/assignee", "{\"assignee\": null}"), uri + "/assignee");
    assertNotFound(api.delete(uri), uri);
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"title\": null}", "{\"title\": \"\"}", "{\"title\": \"   \"}"})
  void createNeedsATitle(String body) {
    assertThat(api.post("/api/tasks", body))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"title": "Bad Request",
             "status": 400,
             "detail": "The request has invalid fields.",
             "instance": "/api/tasks",
             "errors": {"title": "must not be blank"}}""");
  }

  @Test
  void titleAndDescriptionKeepToTheLimitsOfTheHostRecord() {
    assertThat(api.post("/api/tasks", titleAndDescription(40, 200))).hasStatus(HttpStatus.CREATED);

    assertThat(api.post("/api/tasks", titleAndDescription(41, 201)))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors")
        .isEqualTo(
            Map.of(
                "title", "must be at most 40 characters",
                "description", "must be at most 200 characters"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"a", "amueller", "b2", "x1234567"})
  void anAssigneeIsALowerCaseHostUserId(String assignee) {
    assertThat(api.post("/api/tasks", titleAndAssignee(assignee))).hasStatus(HttpStatus.CREATED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"AMUELLER", "Amueller", "amueller1", "1amuelle", "a.muell", "a mue", ""})
  void anyOtherAssigneeIsRejected(String assignee) {
    long id = api.createTask();
    String assigneeJson = "{\"assignee\": \"%s\"}".formatted(assignee);

    assertThat(api.post("/api/tasks", titleAndAssignee(assignee)))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors")
        .isEqualTo(Map.of("assignee", USER_ID_MESSAGE));
    assertThat(api.put("/api/tasks/" + id + "/assignee", assigneeJson))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors")
        .isEqualTo(Map.of("assignee", USER_ID_MESSAGE));
  }

  @Test
  void updateNeedsATitleAndAStatus() {
    long id = api.createTask();

    assertThat(api.put("/api/tasks/" + id, "{\"description\": \"Second reminder\"}"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors")
        .isEqualTo(Map.of("title", "must not be blank", "status", "must not be null"));
  }

  @Test
  void theMessagesAreEnglishWhateverLanguageTheClientPrefers() {
    long id = api.createTask();

    assertThat(
            mvc.put()
                .uri("/api/tasks/" + id)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "de-DE,de;q=0.9")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"Second reminder\"}"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors")
        .isEqualTo(Map.of("title", "must not be blank", "status", "must not be null"));
  }

  @Test
  void aValueOfTheWrongTypeIsAFieldError() {
    long id = api.createTask();

    assertThat(
            api.post(
                "/api/tasks", "{\"title\": \"Check the letter\", \"dueDate\": \"15.11.2026\"}"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors")
        .isEqualTo(Map.of("dueDate", "must be a date like 2026-10-31"));
    assertThat(
            api.put(
                "/api/tasks/" + id, "{\"title\": \"Check the letter\", \"status\": \"CLOSED\"}"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors")
        .isEqualTo(Map.of("status", "must be one of OPEN, IN_PROGRESS, DONE"));
  }

  @Test
  void aBodyThatIsNotJsonIsABadRequestProblem() {
    assertThat(api.post("/api/tasks", "Call back about the claim"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"title": "Bad Request",
             "status": 400,
             "detail": "The request body is missing or not valid JSON.",
             "instance": "/api/tasks"}""");
  }

  @Test
  void anIdThatIsNotANumberIsABadRequestProblem() {
    assertThat(api.get("/api/tasks/abc"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void timestampsAreStoredInUtc() {
    MvcTestResult created = api.create(DIRECT_DEBIT);

    LocalDateTime stored =
        jdbc.queryForObject(
            "SELECT CREATED_AT FROM TASK WHERE ID = ?", LocalDateTime.class, id(created));

    assertThat(stored)
        .isEqualTo(LocalDateTime.ofInstant(instant(created, "$.createdAt"), ZoneOffset.UTC));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0001-01-01", "2026-10-31", "9999-12-31"})
  void aDueDateIsStoredAsGivenFromTheHostsFirstDayToItsLast(String dueDate) {
    long id = id(api.create(titleAndDueDate("\"" + dueDate + "\"")));

    assertThat(jdbc.queryForObject("SELECT DUE_DATE FROM TASK WHERE ID = ?", LocalDate.class, id))
        .isEqualTo(dueDate);
    assertThat(api.get("/api/tasks/" + id))
        .bodyJson()
        .extractingPath("$.dueDate")
        .isEqualTo(dueDate);
  }

  private static void assertNotFound(MvcTestResult result, String uri) {
    assertThat(result)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"title": "Not Found",
             "status": 404,
             "detail": "Task %d does not exist.",
             "instance": "%s"}"""
                .formatted(UNKNOWN_ID, uri));
  }

  private static String titleAndDescription(int titleLength, int descriptionLength) {
    return "{\"title\": \"%s\", \"description\": \"%s\"}"
        .formatted("t".repeat(titleLength), "d".repeat(descriptionLength));
  }

  private static String titleAndAssignee(String assignee) {
    return "{\"title\": \"Check the letter\", \"assignee\": \"%s\"}".formatted(assignee);
  }

  private static String titleAndDueDate(String dueDateJson) {
    return "{\"title\": \"Check the letter\", \"dueDate\": %s}".formatted(dueDateJson);
  }
}
