package com.example.taskboard.version;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The REST API for lightweight backend metadata needed by the UI. */
@RestController
@RequestMapping("/api/version")
class VersionController {

  private final VersionService versions;

  VersionController(VersionService versions) {
    this.versions = versions;
  }

  @GetMapping
  VersionResponse get() {
    return versions.get();
  }
}
