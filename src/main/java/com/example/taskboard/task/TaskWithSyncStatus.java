package com.example.taskboard.task;

import com.example.taskboard.outbox.OutboxStatus;

/** A task and its sync status, as {@link TaskRepository} selects them. */
record TaskWithSyncStatus(Task task, SyncStatus syncStatus) {

  /**
   * The constructor the repository's queries call, with the status of the task's latest outbox
   * event, null if it has none.
   */
  TaskWithSyncStatus(Task task, OutboxStatus latestEventStatus) {
    this(task, SyncStatus.of(latestEventStatus));
  }
}
