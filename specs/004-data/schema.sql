-- PostgreSQL schema base para Fase 1

create table if not exists tenants (
  id bigserial primary key,
  tenant_id uuid not null unique,
  legal_name text not null,
  created_at timestamptz not null default now()
);

create table if not exists authorizations (
  id bigserial primary key,
  authorization_id uuid not null,
  tenant_id uuid not null,
  patient_id text not null,
  operator_code text not null,
  status text not null,
  external_protocol text,
  external_request_key text,
  clinical_justification text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uq_authorizations_tenant_external_id unique (tenant_id, authorization_id),
  constraint uq_authorization_external_request unique (tenant_id, external_request_key)
);

create table if not exists authorization_procedures (
  id bigserial primary key,
  authorization_pk bigint not null references authorizations(id),
  tenant_id uuid not null,
  procedure_code text not null,
  constraint uq_authorization_procedures unique (authorization_pk, procedure_code)
);

create table if not exists tiss_guides (
  id bigserial primary key,
  tiss_guide_id uuid not null unique,
  tenant_id uuid not null,
  authorization_id uuid not null,
  tiss_version text not null,
  xml_content text not null,
  xml_hash text not null,
  validation_status text not null,
  validation_report text,
  created_at timestamptz not null default now(),
  constraint fk_tiss_guide_authorization
    foreign key (tenant_id, authorization_id) references authorizations(tenant_id, authorization_id)
);

create table if not exists operator_dispatches (
  id bigserial primary key,
  dispatch_id uuid not null unique,
  tenant_id uuid not null,
  authorization_id uuid not null,
  operator_code text not null,
  dispatch_type text not null,
  technical_status text not null,
  attempt_count integer not null default 0,
  last_error_code text,
  last_error_message text,
  next_attempt_at timestamptz,
  sent_at timestamptz,
  ack_at timestamptz,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint fk_operator_dispatch_authorization
    foreign key (tenant_id, authorization_id) references authorizations(tenant_id, authorization_id)
);

create table if not exists outbox_events (
  id bigserial primary key,
  event_id uuid not null unique,
  tenant_id uuid not null,
  aggregate_type text not null,
  aggregate_id uuid not null,
  event_type text not null,
  event_version integer not null,
  correlation_id uuid not null,
  payload jsonb not null,
  occurred_at timestamptz not null,
  published_at timestamptz,
  publish_attempts integer not null default 0,
  last_error text,
  dead_letter_at timestamptz
);

create index if not exists idx_outbox_unpublished
  on outbox_events (published_at, occurred_at)
  where published_at is null;

create index if not exists idx_outbox_pending
  on outbox_events (occurred_at, published_at, dead_letter_at);

create table if not exists outbox_dead_letters (
  id bigserial primary key,
  outbox_event_id bigint not null unique references outbox_events(id),
  event_id uuid not null,
  tenant_id uuid not null,
  event_type text not null,
  payload jsonb not null,
  failure_reason text not null,
  failed_at timestamptz not null default now()
);

create table if not exists idempotency_keys (
  id bigserial primary key,
  tenant_id uuid not null,
  idempotency_key text not null,
  request_hash text not null,
  authorization_id uuid,
  response_snapshot jsonb,
  created_at timestamptz not null default now(),
  constraint uq_idempotency_tenant_key unique (tenant_id, idempotency_key)
);

create table if not exists audit_trail (
  id bigserial primary key,
  tenant_id uuid not null,
  aggregate_type text not null,
  aggregate_id uuid not null,
  action text not null,
  actor text not null,
  correlation_id uuid not null,
  metadata jsonb,
  created_at timestamptz not null default now()
);
