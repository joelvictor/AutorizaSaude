# Criterios de Aceite - Fase 1

## Engine TISS

- Dado um pedido valido, quando gerar XML, entao o XSD correspondente valida com sucesso.
- Dado erro de schema, quando validar XML, entao a autorizacao nao pode ser despachada.

## Eventos Governados

- Todo evento publicado respeita envelope padrao e `eventType` catalogado.
- Eventos breaking possuem versao incrementada.

## APIs Governadas

- OpenAPI 3.1 versionada e publicada para todos endpoints `/v1`.
- `X-Tenant-Id`, `X-Correlation-Id` e `X-Idempotency-Key` aplicados onde obrigatorio.

## Persistencia Consistente

- Escrita de estado e outbox no mesmo commit.
- Reexecucao de mensagens e idempotente.

## Observabilidade Ativa

- Latencia p95 por operadora.
- Taxa de sucesso por operadora e por tipo de integracao.
- Fila de retries e tamanho da DLQ visiveis no dashboard.
