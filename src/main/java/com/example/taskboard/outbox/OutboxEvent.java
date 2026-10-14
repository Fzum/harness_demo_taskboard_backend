package com.example.taskboard.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * A row of OUTBOX_EVENT: one change of one aggregate, for the datasync to relay to the host.
 *
 * <p>The backend only inserts events and leaves the processing columns to the datasync: a new row
 * gets STATUS PENDING and ATTEMPTS 0 from the column defaults, LAST_ERROR and PROCESSED_AT are not
 * mapped at all, and STATUS is mapped read-only, for the tasks' sync status.
 */
@Entity
class OutboxEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String aggregateType;

  private long aggregateId;

  private String eventType;

  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(insertable = false, updatable = false)
  private OutboxStatus status;

  private Instant createdAt;

  protected OutboxEvent() {}

  OutboxEvent(
      String aggregateType, long aggregateId, String eventType, String payload, Instant createdAt) {
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payload = payload;
    this.createdAt = createdAt;
  }
}
