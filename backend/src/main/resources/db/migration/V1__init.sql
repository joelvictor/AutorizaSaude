create extension if not exists "pgcrypto";

create table if not exists authorizations (
  authorization_id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null,
  patient_id text not null,
  operator_code text not null,
  status text not null,
  clinical_justification text not null,
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
  published_at timestamptz
);

create table if not exists idempotency_keys (
  tenant_id uuid not null,
  idempotency_key text not null,
  request_hash text not null,
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
