# Mapa de Modulos e Dependencias

## Regra de Dependencia

- `api -> application -> domain`
- `domain` nao depende de infraestrutura.
- `infrastructure` implementa portas de `application`.

## Dependencias Permitidas

| Modulo origem | Pode depender de |
|---|---|
| api | application, idempotency |
| authorization | tiss-guide (via evento), audit (via evento) |
| tiss-guide | authorization (somente contratos), audit |
| operator-dispatch | authorization (somente contratos), tiss-guide (somente contratos), audit |
| audit | shared-kernel |
| integration | operator-dispatch |
| observability | todos (somente leitura de eventos/metricas) |

## Shared Kernel

- Tipos de identificador e correlacao (`tenantId`, `authorizationId`, `correlationId`)
- Envelope de eventos
- Erros de dominio padronizados
