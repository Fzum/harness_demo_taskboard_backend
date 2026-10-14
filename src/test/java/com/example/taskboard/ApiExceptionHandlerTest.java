package com.example.taskboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The answer to a bug, with a controller that fails and no application context. */
@ExtendWith(OutputCaptureExtension.class)
class ApiExceptionHandlerTest {

  @RestController
  static class FailingController {

    @GetMapping("/fail")
    void fail() {
      throw new IllegalStateException("connection to db2.example.com refused");
    }
  }

  private final MockMvcTester mvc =
      MockMvcTester.create(
          MockMvcBuilders.standaloneSetup(new FailingController())
              .setControllerAdvice(new ApiExceptionHandler())
              .build());

  @Test
  void anUnexpectedErrorIsAnInternalServerErrorProblemWithoutDetails(CapturedOutput output) {
    assertThat(mvc.get().uri("/fail"))
        .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"title": "Internal Server Error", "status": 500, "instance": "/fail"}""");
    assertThat(output)
        .contains("Unexpected error while handling a request")
        .contains("java.lang.IllegalStateException: connection to db2.example.com refused");
  }
}
