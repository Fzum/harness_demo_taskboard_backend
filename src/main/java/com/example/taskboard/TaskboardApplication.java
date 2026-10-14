package com.example.taskboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** The Taskboard backend: the REST API for tasks, owner of the taskboard schema and its outbox. */
@SpringBootApplication
public class TaskboardApplication {

  public static void main(String[] args) {
    SpringApplication.run(TaskboardApplication.class, args);
  }
}
