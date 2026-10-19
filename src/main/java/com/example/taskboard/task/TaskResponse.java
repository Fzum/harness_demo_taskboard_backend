package com.example.taskboard.task;

import java.time.Instant;
import java.time.LocalDate;

/** A task as the API returns it. */
record TaskResponse(
    long id,
    String title,
    String description,
    TaskStatus status,
    String assignee,
    LocalDate dueDate,
    boolean overdue,
    TaskSource source,
    String hostId,
    SyncStatus syncStatus,
    Instant createdAt,
    Instant updatedAt) {

  static TaskResponse of(Task task, SyncStatus syncStatus) {
    return new TaskResponse(
        task.getId(),
        task.getTitle(),
        task.getDescription(),
        task.getStatus(),
        task.getAssignee(),
        task.getDueDate(),
        isOverdue(task.getDueDate()),
        task.getSource(),
        task.getHostId(),
        syncStatus,
        task.getCreatedAt(),
        task.getUpdatedAt());
  }

  /** True iff there is a due date before today (server local date), whatever the status. */
  static boolean isOverdue(LocalDate dueDate) {
    return dueDate != null && dueDate.isBefore(LocalDate.now());
  }

  static TaskResponse of(TaskWithSyncStatus row) {
    return of(row.task(), row.syncStatus());
  }
}
