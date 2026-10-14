package com.example.taskboard.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Calls the task API through {@link MockMvcTester} with JSON bodies. The tests share one database,
 * so each creates its own tasks and never assumes an empty one.
 */
final class TaskApi {

  /** An id no test reaches. */
  static final long UNKNOWN_ID = 999_999_999L;

  private final MockMvcTester mvc;

  TaskApi(MockMvcTester mvc) {
    this.mvc = mvc;
  }

  MvcTestResult get(String uri) {
    return mvc.get().uri(uri).exchange();
  }

  MvcTestResult post(String uri, String json) {
    return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
  }

  MvcTestResult put(String uri, String json) {
    return mvc.put().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
  }

  MvcTestResult delete(String uri) {
    return mvc.delete().uri(uri).exchange();
  }

  /** Creates a task from this JSON body and returns the response. */
  MvcTestResult create(String json) {
    MvcTestResult result = post("/api/tasks", json);
    assertThat(result).hasStatus(HttpStatus.CREATED);
    return result;
  }

  /** Creates a task with nothing but a title and returns its id. */
  long createTask() {
    return id(create("{\"title\": \"Call back about the claim\"}"));
  }

  static long id(MvcTestResult result) {
    return JsonPath.<Number>read(body(result), "$.id").longValue();
  }

  static String text(MvcTestResult result, String path) {
    return JsonPath.read(body(result), path);
  }

  static Instant instant(MvcTestResult result, String path) {
    return Instant.parse(text(result, path));
  }

  static String body(MvcTestResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }
}
