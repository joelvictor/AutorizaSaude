# AutorizaSaude - TISS Hub

Plataforma de integracao generica para autorizacoes medicas com operadoras de saude, baseada em `Spec-Driven Development (SDD)`, `Modular Monolith` e `EDA interna`.

## Estrutura

- `/specs`: contratos oficiais da Fase 1
  - `000-foundations`: arquitetura e mapa de modulos
  - `001-domain`: aggregates e invariantes
  - `002-events`: envelope e catalogo `EVT-001`..`EVT-016`
  - `003-api`: OpenAPI 3.1 (`/v1`)
  - `004-data`: schema PostgreSQL e mapeamento evento->persistencia
  - `005-ports`: portas e contratos internos entre modulos
  - `006-resilience`: event bus interno, retry, circuit breaker e DLQ
  - `007-acceptance`: criterios de aceite executaveis
- `/backend`: scaffold Kotlin + Quarkus para API e dominio inicial de autorizacoes
- `/frontend`: scaffold Next.js para dashboard operacional inicial

## Backend

Requisitos:

- JDK 25
- Gradle 9.2+
- PostgreSQL 15+

Passos:

1. `cd backend`
2. Configure `src/main/resources/application.properties` para seu banco local.
3. `./gradlew quarkusDev`

Endpoints iniciais:

- `POST /v1/authorizations`
- `GET /v1/authorizations/{authorizationId}`
- `GET /v1/authorizations/{authorizationId}/status`
- `POST /v1/authorizations/{authorizationId}/cancel`
- `POST /v1/authorizations/{authorizationId}/poll`
- `GET /v1/operations/outbox`
- `POST /v1/operations/outbox/process`
- `GET /v1/operations/outbox/dead-letters`
- `POST /v1/operations/outbox/dead-letters/requeue`

## Frontend

Requisitos:

- Node.js 22+

Passos:

1. `cd frontend`
2. `npm install`
3. `npm run dev`

## Decisoes da Fase 1

- Multi-tenant por `X-Tenant-Id` e `tenant_id` em persistencia/eventos.
- Consistencia forte intra-aggregate e eventual inter-modulos.
- Outbox e idempotencia como obrigatorios de plataforma.

## Proximos Incrementos

1. Integrar adaptadores reais por operadora (Tipo A/B/C) com endpoints externos (SOAP/REST/portal).
2. Expandir regras de validacao TISS por operadora e versao de layout.
3. Integrar dashboard com API real e stream de eventos.
4. Evoluir relay de outbox para publicacao em broker real (Kafka/Rabbit) e politicas avancadas de retry.
