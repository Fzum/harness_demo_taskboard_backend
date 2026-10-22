package com.example.taskboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.h2.Driver;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/** PRIO-1: tasks that exist before the priority migration report MEDIUM afterwards. */
class PriorityMigrationTest {

  private final SimpleDriverDataSource dataSource =
      new SimpleDriverDataSource(
          new Driver(), "jdbc:h2:mem:priority-migration-test;MODE=DB2;DB_CLOSE_DELAY=-1", "sa", "");
  private final JdbcTemplate jdbc = new JdbcTemplate(dataSource);

  @Test
  void existingTasksGetMediumAndTheColumnIsRequired() {
    Flyway.configure().dataSource(dataSource).target("2").load().migrate();
    jdbc.update(
        "INSERT INTO TASK (TITLE, STATUS, SOURCE, CREATED_AT, UPDATED_AT)"
            + " VALUES ('Old task', 'OPEN', 'TASKBOARD', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

    Flyway.configure().dataSource(dataSource).load().migrate();

    assertThat(
            jdbc.queryForObject("SELECT PRIORITY FROM TASK WHERE TITLE = 'Old task'", String.class))
        .isEqualTo("MEDIUM");
    assertThat(
            jdbc.queryForObject(
                "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS"
                    + " WHERE TABLE_NAME = 'TASK' AND COLUMN_NAME = 'PRIORITY'",
                String.class))
        .isEqualTo("NO");
    assertThat(
            jdbc.queryForObject(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS"
                    + " WHERE TABLE_NAME = 'TASK' AND COLUMN_NAME = 'PRIORITY'",
                Integer.class))
        .isEqualTo(10);
  }
}
