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
    TaskSource source,
    String hostId,
    SyncStatus syncStatus,
    boolean overdue,
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
        task.getSource(),
        task.getHostId(),
        syncStatus,
        isOverdue(task.getDueDate()),
        task.getCreatedAt(),
        task.getUpdatedAt());
  }

  static TaskResponse of(TaskWithSyncStatus row) {
    return of(row.task(), row.syncStatus());
  }

  /** Computed on read, whatever the status: the due date lies before today in the JVM's zone. */
  private static boolean isOverdue(LocalDate dueDate) {
    return dueDate != null && dueDate.isBefore(LocalDate.now());
  }
}
