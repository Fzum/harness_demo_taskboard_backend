package com.example.taskboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
class VersionBuildMetadataApiTest {

  private final MockMvcTester mvc;
  private final BuildProperties buildProperties;

  VersionBuildMetadataApiTest(MockMvcTester mvc, BuildProperties buildProperties) {
    this.mvc = mvc;
    this.buildProperties = buildProperties;
  }

  @Test
  void versionEndpointExposesTheGeneratedBuildMetadataVersion() throws Exception {
    MvcTestResult result = mvc.get().uri("/api/version").exchange();
    String version = JsonPath.read(result.getResponse().getContentAsString(), "$.version");

    assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
    assertThat(version).isEqualTo(buildProperties.getVersion());
  }
}
