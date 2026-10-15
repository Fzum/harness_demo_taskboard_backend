package com.example.taskboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
class VersionApiTest {

  private final MockMvcTester mvc;

  VersionApiTest(MockMvcTester mvc) {
    this.mvc = mvc;
  }

  @Test
  void versionEndpointAnswersWithTheRunningBuildVersion() throws Exception {
    MvcTestResult result = mvc.get().uri("/api/version").exchange();
    String version = JsonPath.read(result.getResponse().getContentAsString(), "$.version");

    assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
    assertThat(version).isNotBlank();
  }
}
