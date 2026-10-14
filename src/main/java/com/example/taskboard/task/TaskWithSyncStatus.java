package com.example.taskboard.task;

import com.example.taskboard.outbox.OutboxStatus;

/** A task and its sync status, as {@link TaskRepository} selects them. */
record TaskWithSyncStatus(Task task, SyncStatus syncStatus) {

  /** {@code latestEventStatus}: the status of the task's latest outbox event, null if none. */
  TaskWithSyncStatus(Task task, OutboxStatus latestEventStatus) {
    this(task, SyncStatus.of(latestEventStatus));
  }
}
