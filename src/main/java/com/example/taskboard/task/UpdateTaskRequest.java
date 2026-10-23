package com.example.taskboard.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Body of PUT /api/tasks/{id}: replaces every editable field, and null (or a missing key) clears
 * one, except for {@code priority}: without one the task keeps its priority. The texts, limits and
 * messages are those of {@link CreateTaskRequest}.
 */
record UpdateTaskRequest(
    @NotBlank(message = "must not be blank")
        @Size(max = 40, message = "must be at most {max} characters")
        String title,
    @Size(max = 200, message = "must be at most {max} characters") String description,
    @NotNull(message = "must not be null") TaskStatus status,
    @UserId String assignee,
    @HostDate LocalDate dueDate,
    TaskPriority priority) {

  /** A request without a priority: the task keeps its own. */
  UpdateTaskRequest(
      String title, String description, TaskStatus status, String assignee, LocalDate dueDate) {
    this(title, description, status, assignee, dueDate, null);
  }

  UpdateTaskRequest {
    title = HostText.of(title);
    description = HostText.of(description);
  }
}
