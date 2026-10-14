package com.example.taskboard.task;

/** OUTBOX_EVENT.EVENT_TYPE of a task's events. */
enum TaskEventType {
  TASK_CREATED,
  /** Edits, status changes, assigning and unassigning. */
  TASK_UPDATED,
  TASK_DELETED
}
