package com.example.taskboard.task;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface TaskRepository extends JpaRepository<Task, Long> {

  /**
   * Selects tasks together with the status of their latest outbox event, in a single query. The
   * index IX_OUTBOX_EVENT_AGGREGATE (AGGREGATE_ID, ID) finds each task's latest event.
   */
  String WITH_SYNC_STATUS =
      """
      select new com.example.taskboard.task.TaskWithSyncStatus(t,
          (select e.status from OutboxEvent e
           where e.id = (select max(latest.id) from OutboxEvent latest
                         where latest.aggregateType = 'TASK' and latest.aggregateId = t.id)))
      from Task t
      """;

  @Query(WITH_SYNC_STATUS + "order by t.id")
  List<TaskWithSyncStatus> findAllWithSyncStatus();

  @Query(WITH_SYNC_STATUS + "where t.id = :id")
  Optional<TaskWithSyncStatus> findWithSyncStatusById(long id);

  /**
   * Loads a task to change it and locks its row until the transaction ends (SELECT ... FOR UPDATE).
   * Changes to one task thus run one after another: each event's snapshot contains the changes
   * before it, and the events' IDs follow the order of the changes.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Task> findLockedById(long id);
}
