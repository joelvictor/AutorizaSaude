# Event Bus Interno e Resiliencia

## Topicos Internos

- `authorization.events`
- `tiss.events`
- `dispatch.events`
- `audit.events`

## Particionamento e Ordenacao

- Chave de particao: `tenantId + authorizationId`.
- Garantia: ordem por chave dentro do topico.
- Sem garantia de ordem global entre topicos.

## Retry

- Backoff exponencial com jitter: `5s`, `15s`, `45s`, `120s`, `300s`.
- Maximo de 5 tentativas por dispatch tecnico.

## Circuit Breaker

- Janela: 20 requisicoes.
- Threshold de falha: 50%.
- Tempo aberto: 60s.
- Half-open: 5 chamadas de teste.

## Dead Letter

- Evento vai para DLQ apos esgotar tentativas.
- Alertas operacionais com severidade alta.
- Reprocessamento manual com comando explicito e auditado.

## Outbox Relay (Implementado na Fase 4)

- Worker interno processa eventos pendentes de `outbox_events`.
- Publicacao bem-sucedida marca `published_at`.
- Falhas incrementam `publish_attempts` e registram `last_error`.
- Ao atingir limite de tentativas, evento recebe `dead_letter_at` e espelha em `outbox_dead_letters`.
- Operacao manual disponivel via API:
  - `GET /v1/operations/outbox`
  - `POST /v1/operations/outbox/process`
