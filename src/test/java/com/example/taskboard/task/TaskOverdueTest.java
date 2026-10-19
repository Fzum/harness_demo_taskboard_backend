package com.example.taskboard.task;

import static com.example.taskboard.task.TaskApi.body;
import static com.example.taskboard.task.TaskApi.id;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The computed {@code overdue} field of every task response. */
@SpringBootTest
@AutoConfigureMockMvc
class TaskOverdueTest {

  private final TaskApi api;

  TaskOverdueTest(MockMvcTester mvc) {
    this.api = new TaskApi(mvc);
  }

  private MvcTestResult createWithDue(String dueDate) {
    String due = dueDate == null ? "null" : "\"" + dueDate + "\"";
    return api.create("{\"title\": \"Overdue check\", \"dueDate\": " + due + "}");
  }

  private static Boolean overdue(MvcTestResult result) {
    return JsonPath.read(body(result), "$.overdue");
  }

  @Test
  void pastDueDateIsOverdue() {
    assertThat(overdue(createWithDue("2020-01-01"))).isTrue();
  }

  @Test
  void yesterdayIsOverdue() {
    assertThat(overdue(createWithDue(LocalDate.now().minusDays(1).toString()))).isTrue();
  }

  @Test
  void todayIsNotOverdue() {
    assertThat(overdue(createWithDue(LocalDate.now().toString()))).isFalse();
  }

  @Test
  void futureDueDateIsNotOverdue() {
    assertThat(overdue(createWithDue("2999-01-01"))).isFalse();
  }

  @Test
  void missingDueDateIsNotOverdueAndNeverNull() {
    assertThat(overdue(createWithDue(null))).isFalse();
  }

  @Test
  void getAndListCarryTheField() {
    long id = id(createWithDue("2020-01-01"));

    assertThat(overdue(api.get("/api/tasks/" + id))).isTrue();

    List<Object> all = JsonPath.read(body(api.get("/api/tasks")), "$[*].overdue");
    assertThat(all).isNotEmpty().allMatch(Boolean.class::isInstance);
    List<Boolean> mine =
        JsonPath.read(body(api.get("/api/tasks")), "$[?(@.id==" + id + ")].overdue");
    assertThat(mine).containsExactly(true);
  }

  @Test
  void updateRecomputesAndStatusIsIgnored() {
    long id = id(createWithDue("2020-01-01"));

    MvcTestResult done =
        api.put(
            "/api/tasks/" + id,
            "{\"title\": \"Overdue check\", \"status\": \"DONE\", \"dueDate\": \"2020-01-01\"}");
    assertThat(overdue(done)).isTrue();

    MvcTestResult future =
        api.put(
            "/api/tasks/" + id,
            "{\"title\": \"Overdue check\", \"status\": \"OPEN\", \"dueDate\": \"2999-01-01\"}");
    assertThat(overdue(future)).isFalse();
  }

  @Test
  void assigneeUpdateCarriesTheField() {
    long id = id(createWithDue("2020-01-01"));

    MvcTestResult result =
        api.put("/api/tasks/" + id + "/assignee", "{\"assignee\": \"amueller\"}");

    assertThat(overdue(result)).isTrue();
  }
}
