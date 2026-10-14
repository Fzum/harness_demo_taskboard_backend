package com.example.taskboard.task;

/** Body of PUT /api/tasks/{id}/assignee; {@code {"assignee": null}} unassigns the task. */
record AssigneeRequest(@UserId String assignee) {}
