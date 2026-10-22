package com.example.taskboard.task;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The payload of a task's outbox event: the whole task after the change, or for a deletion its last
 * state before it. Every key is always present, null when empty.
 *
 * @param hostId TASK.HOST_ID at the time of the change
 * @param occurredAt when the change happened
 */
record TaskSnapshot(
    long taskId,
    String hostId,
    String title,
    String description,
    TaskStatus status,
    String assignee,
    LocalDate dueDate,
    TaskSource source,
    TaskPriority priority,
    Instant occurredAt) {

  static TaskSnapshot of(Task task, Instant occurredAt) {
    return new TaskSnapshot(
        task.getId(),
        task.getHostId(),
        task.getTitle(),
        task.getDescription(),
        task.getStatus(),
        task.getAssignee(),
        task.getDueDate(),
        task.getSource(),
        task.getPriority(),
        occurredAt);
  }
}
