package com.example.taskboard.version;

/** The running backend build version as returned by {@code GET /api/version}. */
record VersionResponse(String version) {}
