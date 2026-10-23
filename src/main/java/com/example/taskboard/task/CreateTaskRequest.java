package com.example.taskboard.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Body of POST /api/tasks. The limits are those of the host record (copybook TSKREC): TSK-TITLE PIC
 * X(40), TSK-DESC PIC X(200), TSK-USER and TSK-DUE-DATE. A missing
 * or null priority means MEDIUM. They apply to the title and description as
 * stored, the way the host keeps them ({@link HostText}). Every message is spelled out, so that it
 * is English like the rest of the API: Bean Validation's defaults would follow the client's
 * Accept-Language.
 */
record CreateTaskRequest(
    @NotBlank(message = "must not be blank")
        @Size(max = 40, message = "must be at most {max} characters")
        String title,
    @Size(max = 200, message = "must be at most {max} characters") String description,
    @UserId String assignee,
    @HostDate LocalDate dueDate,
    TaskPriority priority) {

  /** A request without a priority: it becomes MEDIUM. */
  CreateTaskRequest(String title, String description, String assignee, LocalDate dueDate) {
    this(title, description, assignee, dueDate, null);
  }

  CreateTaskRequest {
    title = HostText.of(title);
    description = HostText.of(description);
  }
}
