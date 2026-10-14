package com.example.taskboard.task;

/** TASK.SOURCE: where a task was created. */
public enum TaskSource {
  /** In the Taskboard app, through this API. */
  TASKBOARD,
  /** On the IBM host; the datasync's ETL loaded it. */
  HOST
}
