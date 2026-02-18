alter table operator_dispatches
  add column if not exists last_error_code text;

alter table operator_dispatches
  add column if not exists last_error_message text;

alter table operator_dispatches
  add column if not exists next_attempt_at timestamp with time zone;

alter table operator_dispatches
  add column if not exists sent_at timestamp with time zone;

alter table operator_dispatches
  add column if not exists ack_at timestamp with time zone;

alter table operator_dispatches
  add column if not exists completed_at timestamp with time zone;
