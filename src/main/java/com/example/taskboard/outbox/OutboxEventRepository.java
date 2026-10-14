package com.example.taskboard.outbox;

import org.springframework.data.repository.Repository;

/** Insert only: the backend never changes or deletes an outbox event. */
interface OutboxEventRepository extends Repository<OutboxEvent, Long> {

  OutboxEvent save(OutboxEvent event);
}
