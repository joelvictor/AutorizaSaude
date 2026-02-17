-- PostgreSQL schema base para Fase 1

create extension if not exists "pgcrypto";

create table if not exists tenants (
  tenant_id uuid primary key,
  legal_name text not null,
  created_at timestamptz not null default now()
);

create table if not exists authorizations (
  authorization_id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(tenant_id),
  patient_id text not null,
  operator_code text not null,
  status text not null,
  external_protocol text,
  external_request_key text,
  clinical_justification text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uq_authorization_external_request unique (tenant_id, external_request_key)
);

create table if not exists authorization_procedures (
  authorization_id uuid not null references authorizations(authorization_id),
  tenant_id uuid not null,
  procedure_code text not null,
  primary key (authorization_id, procedure_code)
);

create table if not exists tiss_guides (
  tiss_guide_id uuid primary key default gen_random_uuid(),
  authorization_id uuid not null references authorizations(authorization_id),
  tenant_id uuid not null,
  tiss_version text not null,
  xml_content text not null,
  xml_hash text not null,
  validation_status text not null,
  validation_report jsonb,
  created_at timestamptz not null default now()
);

create table if not exists operator_dispatches (
  dispatch_id uuid primary key default gen_random_uuid(),
  authorization_id uuid not null references authorizations(authorization_id),
  tenant_id uuid not null,
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
  updated_at timestamptz not null default now()
);

create table if not exists outbox_events (
  event_id uuid primary key,
  tenant_id uuid not null,
  aggregate_type text not null,
  aggregate_id uuid not null,
  event_type text not null,
  event_version integer not null,
  correlation_id uuid not null,
  payload jsonb not null,
  occurred_at timestamptz not null,
  published_at timestamptz,
  publish_attempts integer not null default 0
);

create index if not exists idx_outbox_unpublished
  on outbox_events (published_at, occurred_at)
  where published_at is null;

create table if not exists idempotency_keys (
  tenant_id uuid not null,
  idempotency_key text not null,
  request_hash text not null,
  response_snapshot jsonb,
  created_at timestamptz not null default now(),
  primary key (tenant_id, idempotency_key)
);

create table if not exists audit_trail (
  audit_id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null,
  aggregate_type text not null,
  aggregate_id uuid not null,
  action text not null,
  actor text not null,
  correlation_id uuid not null,
  metadata jsonb,
  created_at timestamptz not null default now()
);
