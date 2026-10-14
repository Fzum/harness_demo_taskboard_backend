package com.example.taskboard.outbox;

/** OUTBOX_EVENT.STATUS: how far the datasync has got with relaying an event to the host. */
public enum OutboxStatus {
  /** Not on the host yet; the datasync keeps trying. */
  PENDING,
  /** Written to the host. */
  PROCESSED,
  /** The datasync gave up after repeated failures: a host inconsistency. */
  FAILED
}
