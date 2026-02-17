# Authorization Aggregate

## Entidade Raiz

- `Authorization`

## Estados

- `DRAFT`
- `VALIDATED`
- `DISPATCHED`
- `PENDING_OPERATOR`
- `AUTHORIZED`
- `DENIED`
- `CANCELLED`
- `EXPIRED`
- `FAILED_TECHNICAL`

## Invariantes

- Uma autorizacao pertence a exatamente um `tenant_id`.
- Transicao para `DISPATCHED` requer XML TISS valido.
- `AUTHORIZED` e `DENIED` sao estados finais de negocio.
- Cancelamento so pode ocorrer antes de estado final ou conforme regra da operadora.
- Toda alteracao de estado gera evento de dominio e auditoria.

## Transicoes Permitidas

- `DRAFT -> VALIDATED`
- `VALIDATED -> DISPATCHED`
- `DISPATCHED -> PENDING_OPERATOR`
- `PENDING_OPERATOR -> AUTHORIZED | DENIED | FAILED_TECHNICAL | EXPIRED`
- `DISPATCHED | PENDING_OPERATOR -> CANCELLED`
- `FAILED_TECHNICAL -> DISPATCHED` (retry)

## Chaves de Idempotencia

- `tenant_id + external_request_key`
- `tenant_id + idempotency_key` em comandos de API
