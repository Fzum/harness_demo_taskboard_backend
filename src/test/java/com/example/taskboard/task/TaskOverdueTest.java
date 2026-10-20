package com.example.taskboard.task;

import static com.example.taskboard.task.TaskApi.body;
import static com.example.taskboard.task.TaskApi.id;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The computed {@code overdue} field of every task response: true when the due date lies before
 * today in the JVM's default zone, false otherwise, whatever the status.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskOverdueTest {

  private final TaskApi api;

  TaskOverdueTest(MockMvcTester mvc) {
    this.api = new TaskApi(mvc);
  }

  @ParameterizedTest
  @ValueSource(strings = {"OPEN", "IN_PROGRESS", "DONE"})
  void aTaskDueBeforeTodayIsOverdueWhateverItsStatus(String status) {
    long id = id(api.create(withDueDate(LocalDate.now().minusDays(1).toString())));

    MvcTestResult updated =
        api.put(
            "/api/tasks/" + id,
            "{\"title\": \"Pay the premium\", \"status\": \"%s\", \"dueDate\": \"%s\"}"
                .formatted(status, LocalDate.now().minusDays(1)));

    assertThat(updated).hasStatusOk().bodyJson().extractingPath("$.overdue").isEqualTo(true);
  }

  @Test
  void aTaskDueTodayOrLaterIsNotOverdue() {
    String today = LocalDate.now().toString();
    String tomorrow = LocalDate.now().plusDays(1).toString();

    assertThat(api.create(withDueDate(today)))
        .bodyJson()
        .extractingPath("$.overdue")
        .isEqualTo(false);
    assertThat(api.create(withDueDate(tomorrow)))
        .bodyJson()
        .extractingPath("$.overdue")
        .isEqualTo(false);
  }

  @Test
  void aTaskWithoutDueDateIsNotOverdue() {
    assertThat(api.create("{\"title\": \"Call back about the claim\"}"))
        .bodyJson()
        .extractingPath("$.overdue")
        .isEqualTo(false);
  }

  @Test
  void everyTaskResponseCarriesABooleanOverdue() {
    long overdue = id(api.create(withDueDate(LocalDate.now().minusDays(3).toString())));
    long none = api.createTask();

    MvcTestResult list = api.get("/api/tasks");

    assertThat(list).hasStatusOk();
    assertThat(JsonPath.<Object>read(body(list), "$[?(@.id == %d)].overdue".formatted(overdue)))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .containsExactly(true);
    assertThat(JsonPath.<Object>read(body(list), "$[?(@.id == %d)].overdue".formatted(none)))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .containsExactly(false);
    assertThat(JsonPath.<Object>read(body(list), "$[*].overdue"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .allMatch(value -> value instanceof Boolean);
  }

  @Test
  void theSingleTaskAndEveryWriteAnswerWithOverdue() {
    MvcTestResult created = api.create(withDueDate(LocalDate.now().plusDays(7).toString()));
    long id = id(created);
    assertThat(created).bodyJson().extractingPath("$.overdue").isEqualTo(false);

    assertThat(api.get("/api/tasks/" + id))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.overdue")
        .isEqualTo(false);

    MvcTestResult updated =
        api.put(
            "/api/tasks/" + id,
            "{\"title\": \"Pay the premium\", \"status\": \"OPEN\", \"dueDate\": \"%s\"}"
                .formatted(LocalDate.now().minusDays(2)));
    assertThat(updated).hasStatusOk().bodyJson().extractingPath("$.overdue").isEqualTo(true);

    MvcTestResult assigned = api.put("/api/tasks/" + id + "/assignee", "{\"assignee\": \"bkoch\"}");
    assertThat(assigned).hasStatusOk().bodyJson().extractingPath("$.overdue").isEqualTo(true);
  }

  @Test
  void overdueFollowsTheDueDateOnEveryRead() {
    MvcTestResult created = api.create(withDueDate(LocalDate.now().plusDays(1).toString()));
    long id = id(created);

    assertThat(
            api.put(
                "/api/tasks/" + id,
                "{\"title\": \"Pay the premium\", \"status\": \"DONE\", \"dueDate\": \"%s\"}"
                    .formatted(LocalDate.now().minusDays(1))))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.overdue")
        .isEqualTo(true);
    assertThat(api.get("/api/tasks/" + id)).bodyJson().extractingPath("$.overdue").isEqualTo(true);
  }

  private static String withDueDate(String dueDate) {
    return "{\"title\": \"Pay the premium\", \"dueDate\": \"%s\"}".formatted(dueDate);
  }
}
