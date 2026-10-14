package com.example.taskboard.outbox;

import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * The transactional outbox. A change appends its event in its own transaction, so the event is
 * committed together with the change or not at all. The datasync polls OUTBOX_EVENT and relays the
 * pending events to the host.
 */
@Component
public class Outbox {

  private final OutboxEventRepository events;
  private final JsonMapper json;

  Outbox(OutboxEventRepository events, JsonMapper json) {
    this.events = events;
    this.json = json;
  }

  /**
   * Appends an event with {@code payload} as its JSON payload. Joins the transaction of the change
   * the event describes and fails without one.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void append(String aggregateType, long aggregateId, String eventType, Object payload) {
    String body = json.writeValueAsString(payload);
    events.save(new OutboxEvent(aggregateType, aggregateId, eventType, body, Instant.now()));
  }
}
