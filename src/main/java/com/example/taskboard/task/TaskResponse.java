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
        task.getCreatedAt(),
        task.getUpdatedAt());
  }

  static TaskResponse of(TaskWithSyncStatus row) {
    return of(row.task(), row.syncStatus());
  }
}
