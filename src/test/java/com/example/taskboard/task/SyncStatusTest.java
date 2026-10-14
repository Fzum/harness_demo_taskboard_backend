package com.example.taskboard.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskboard.outbox.OutboxStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SyncStatusTest {

  @ParameterizedTest(name = "latest event {0}: {1}")
  @CsvSource(
      nullValues = "none",
      value = {"none, SYNCED", "PENDING, PENDING", "PROCESSED, SYNCED", "FAILED, FAILED"})
  void followsTheStatusOfTheLatestOutboxEvent(OutboxStatus latestEvent, SyncStatus expected) {
    assertThat(SyncStatus.of(latestEvent)).isEqualTo(expected);
  }
}
