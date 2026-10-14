package com.example.taskboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import org.h2.Driver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The backend as {@code ./mvnw spring-boot:run} starts it for the local system: healthy, and
 * sharing its database with the datasync through H2's TCP server, which listens on a free port here
 * instead of 9092.
 */
@SpringBootTest(properties = "taskboard.h2.tcp.enabled=true")
@AutoConfigureMockMvc
class LocalRuntimeTest {

  private static final int TCP_PORT = freePort();

  @DynamicPropertySource
  static void tcpPort(DynamicPropertyRegistry registry) {
    registry.add("taskboard.h2.tcp.port", () -> TCP_PORT);
  }

  private final MockMvcTester mvc;

  LocalRuntimeTest(MockMvcTester mvc) {
    this.mvc = mvc;
  }

  @Test
  void healthIsUp() {
    assertThat(mvc.get().uri("/actuator/health"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("UP");
  }

  @Test
  void theDatasyncSeesTasksAndTheirEventsThroughTheTcpServer() {
    MvcTestResult created =
        mvc.post()
            .uri("/api/tasks")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\": \"Renew the standing order\"}")
            .exchange();
    assertThat(created).hasStatus(HttpStatus.CREATED);
    String body = new String(created.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    long id = JsonPath.<Number>read(body, "$.id").longValue();

    // The datasync's URL, user and password, with the test's port.
    JdbcTemplate datasync =
        new JdbcTemplate(
            new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:tcp://localhost:%d/mem:taskboard;MODE=DB2".formatted(TCP_PORT),
                "sa",
                ""));

    assertThat(datasync.queryForObject("SELECT TITLE FROM TASK WHERE ID = ?", String.class, id))
        .isEqualTo("Renew the standing order");
    assertThat(
            datasync.queryForObject(
                "SELECT EVENT_TYPE FROM OUTBOX_EVENT WHERE AGGREGATE_ID = ?", String.class, id))
        .isEqualTo("TASK_CREATED");
  }

  private static int freePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
