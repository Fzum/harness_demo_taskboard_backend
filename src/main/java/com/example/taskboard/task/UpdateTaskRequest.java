package com.example.taskboard.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Body of PUT /api/tasks/{id}: replaces every editable field, and null (or a missing key) clears
 * one. The limits are those of {@link CreateTaskRequest}.
 */
record UpdateTaskRequest(
    @NotBlank @Size(max = 40, message = "must be at most {max} characters") String title,
    @Size(max = 200, message = "must be at most {max} characters") String description,
    @NotNull TaskStatus status,
    @UserId String assignee,
    LocalDate dueDate) {}
