package com.example.taskboard.task;

import com.example.taskboard.outbox.Outbox;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The task use cases. Each change runs in one transaction that writes TASK and appends exactly one
 * event to the outbox, so the task and its event are committed together or not at all.
 */
@Service
@Transactional
class TaskService {

  /** OUTBOX_EVENT.AGGREGATE_TYPE of a task's events. */
  private static final String AGGREGATE_TYPE = "TASK";

  private final TaskRepository tasks;
  private final Outbox outbox;
  private final Clock clock;

  TaskService(TaskRepository tasks, Outbox outbox, Clock clock) {
    this.clock = clock;
    this.tasks = tasks;
    this.outbox = outbox;
  }

  @Transactional(readOnly = true)
  public List<TaskResponse> list() {
    return tasks.findAllWithSyncStatus().stream()
        .map(row -> TaskResponse.of(row, today()))
        .toList();
  }

  @Transactional(readOnly = true)
  public TaskResponse get(long id) {
    return tasks
        .findWithSyncStatusById(id)
        .map(row -> TaskResponse.of(row, today()))
        .orElseThrow(() -> new TaskNotFoundException(id));
  }

  public TaskResponse create(CreateTaskRequest request) {
    Instant now = now();
    Task task =
        tasks.save(
            Task.create(
                request.title(),
                request.description(),
                request.assignee(),
                request.dueDate(),
                now));
    appendEvent(TaskEventType.TASK_CREATED, task, now);
    return pending(task);
  }

  public TaskResponse update(long id, UpdateTaskRequest request) {
    Task task = lock(id);
    Instant now = now();
    task.update(
        request.title(),
        request.description(),
        request.status(),
        request.assignee(),
        request.dueDate(),
        now);
    appendEvent(TaskEventType.TASK_UPDATED, task, now);
    return pending(task);
  }

  public TaskResponse assign(long id, String assignee) {
    Task task = lock(id);
    Instant now = now();
    task.assign(assignee, now);
    appendEvent(TaskEventType.TASK_UPDATED, task, now);
    return pending(task);
  }

  public void delete(long id) {
    Task task = lock(id);
    appendEvent(TaskEventType.TASK_DELETED, task, now());
    tasks.delete(task);
  }

  private void appendEvent(TaskEventType type, Task task, Instant occurredAt) {
    outbox.append(AGGREGATE_TYPE, task.getId(), type.name(), TaskSnapshot.of(task, occurredAt));
  }

  /**
   * The task to change, locked until the transaction ends. A change takes its time only once it
   * holds the lock, so the changes of one task are stamped in the order they are made.
   */
  private Task lock(long id) {
    return tasks.findLockedById(id).orElseThrow(() -> new TaskNotFoundException(id));
  }

  /** The task's latest event is the one just appended: the host has yet to catch up. */
  private TaskResponse pending(Task task) {
    return TaskResponse.of(task, SyncStatus.PENDING, today());
  }

  /** Milliseconds, as the API shows them, so a task reads the same before and after a reload. */
  private static Instant now() {
    return Instant.now().truncatedTo(ChronoUnit.MILLIS);
  }

  /** The server's current local date, against which a due date is overdue. */
  private LocalDate today() {
    return LocalDate.now(clock);
  }
}
