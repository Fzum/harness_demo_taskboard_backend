package com.example.taskboard.task;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** 404: there is no task with this id, or no longer. */
class TaskNotFoundException extends ErrorResponseException {

  TaskNotFoundException(long id) {
    super(
        HttpStatus.NOT_FOUND,
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Task " + id + " does not exist."),
        null);
  }
}
