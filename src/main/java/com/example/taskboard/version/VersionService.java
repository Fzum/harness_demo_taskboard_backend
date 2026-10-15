package com.example.taskboard.version;

import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

/** Reads version metadata of the running backend build. */
@Service
class VersionService {

  private final BuildProperties buildProperties;

  VersionService(BuildProperties buildProperties) {
    this.buildProperties = buildProperties;
  }

  public VersionResponse get() {
    return new VersionResponse(buildProperties.getVersion());
  }
}
