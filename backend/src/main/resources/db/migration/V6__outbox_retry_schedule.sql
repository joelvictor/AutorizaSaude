alter table outbox_events
  add column if not exists next_attempt_at timestamp with time zone;

create index if not exists idx_outbox_pending_due
  on outbox_events (published_at, dead_letter_at, next_attempt_at, occurred_at);
