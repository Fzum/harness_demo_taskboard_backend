package com.example.taskboard.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Body of POST /api/tasks. The limits are those of the host record (copybook TSKREC): TSK-TITLE PIC
 * X(40), TSK-DESC PIC X(200).
 */
record CreateTaskRequest(
    @NotBlank @Size(max = 40, message = "must be at most {max} characters") String title,
    @Size(max = 200, message = "must be at most {max} characters") String description,
    @UserId String assignee,
    LocalDate dueDate) {}
