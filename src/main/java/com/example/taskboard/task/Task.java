package com.example.taskboard.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A row of TASK. The backend writes every column but HOST_ID, the task's key on the host, which
 * only the datasync sets.
 */
@Entity
public class Task {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String title;

  private String description;

  @Enumerated(EnumType.STRING)
  private TaskStatus status;

  private String assignee;

  private LocalDate dueDate;

  @Enumerated(EnumType.STRING)
  @Column(updatable = false)
  private TaskSource source;

  @Column(insertable = false, updatable = false)
  private String hostId;

  @Column(updatable = false)
  private Instant createdAt;

  private Instant updatedAt;

  protected Task() {}

  /** A task created in the Taskboard: open, and unknown to the host until the datasync syncs it. */
  static Task create(
      String title, String description, String assignee, LocalDate dueDate, Instant now) {
    Task task = new Task();
    task.title = title;
    task.description = description;
    task.status = TaskStatus.OPEN;
    task.assignee = assignee;
    task.dueDate = dueDate;
    task.source = TaskSource.TASKBOARD;
    task.createdAt = now;
    task.updatedAt = now;
    return task;
  }

  /** Replaces every editable field; null clears one. */
  void update(
      String title,
      String description,
      TaskStatus status,
      String assignee,
      LocalDate dueDate,
      Instant now) {
    this.title = title;
    this.description = description;
    this.status = status;
    this.assignee = assignee;
    this.dueDate = dueDate;
    this.updatedAt = now;
  }

  /** Assigns the task, or unassigns it if {@code assignee} is null. */
  void assign(String assignee, Instant now) {
    this.assignee = assignee;
    this.updatedAt = now;
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public TaskStatus getStatus() {
    return status;
  }

  public String getAssignee() {
    return assignee;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public TaskSource getSource() {
    return source;
  }

  public String getHostId() {
    return hostId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
