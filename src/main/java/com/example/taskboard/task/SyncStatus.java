package com.example.taskboard.task;

import com.example.taskboard.outbox.OutboxStatus;

/**
 * Whether the host has caught up with a task, after the status of the task's latest outbox event
 * (the one with the highest ID).
 */
public enum SyncStatus {
  /** The host has the latest change, or there is none to send: the task came from the host. */
  SYNCED,
  /** The datasync has not written the latest change to the host yet. */
  PENDING,
  /** The datasync gave up on the latest change: a host inconsistency. */
  FAILED;

  /** {@code latestEventStatus} is null for a task without outbox events. */
  static SyncStatus of(OutboxStatus latestEventStatus) {
    if (latestEventStatus == null) {
      return SYNCED;
    }
    return switch (latestEventStatus) {
      case PENDING -> PENDING;
      case PROCESSED -> SYNCED;
      case FAILED -> FAILED;
    };
  }
}
