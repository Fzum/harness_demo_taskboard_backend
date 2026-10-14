package com.example.taskboard.task;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The REST API for tasks. */
@RestController
@RequestMapping("/api/tasks")
class TaskController {

  private final TaskService tasks;

  TaskController(TaskService tasks) {
    this.tasks = tasks;
  }

  @GetMapping
  List<TaskResponse> list() {
    return tasks.list();
  }

  @GetMapping("/{id}")
  TaskResponse get(@PathVariable long id) {
    return tasks.get(id);
  }

  @PostMapping
  ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
    TaskResponse task = tasks.create(request);
    return ResponseEntity.created(URI.create("/api/tasks/" + task.id())).body(task);
  }

  @PutMapping("/{id}")
  TaskResponse update(@PathVariable long id, @Valid @RequestBody UpdateTaskRequest request) {
    return tasks.update(id, request);
  }

  @PutMapping("/{id}/assignee")
  TaskResponse assign(@PathVariable long id, @Valid @RequestBody AssigneeRequest request) {
    return tasks.assign(id, request.assignee());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable long id) {
    tasks.delete(id);
  }
}
