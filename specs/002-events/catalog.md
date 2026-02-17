# Catalogo de Eventos Internos (EVT-001 a EVT-016)

Todos os eventos seguem o envelope definido em `specs/002-events/event-envelope.schema.json`.

| Codigo | Nome | Quando ocorre | Payload minimo |
|---|---|---|---|
| EVT-001 | `AuthorizationCreated` | Criacao da autorizacao | `authorizationId`, `patientId`, `procedureCodes[]` |
| EVT-002 | `AuthorizationValidated` | Validacao de regras de negocio concluida | `authorizationId`, `validationSummary` |
| EVT-003 | `TissXmlGenerated` | XML TISS gerado | `authorizationId`, `tissGuideId`, `xmlHash`, `tissVersion` |
| EVT-004 | `TissXmlValidationFailed` | Falha de XSD/regra TISS | `authorizationId`, `tissGuideId`, `errors[]` |
| EVT-005 | `OperatorDispatchRequested` | Pedido de envio para adaptador | `authorizationId`, `operatorCode`, `dispatchType` |
| EVT-006 | `OperatorDispatchSent` | Envio tecnico realizado | `authorizationId`, `dispatchId`, `attempt`, `sentAt` |
| EVT-007 | `OperatorAckReceived` | Operadora retornou ACK/protocolo | `authorizationId`, `dispatchId`, `externalProtocol` |
| EVT-008 | `AuthorizationStatusPolled` | Polling executado | `authorizationId`, `dispatchId`, `externalStatus` |
| EVT-009 | `AuthorizationApproved` | Operadora aprovou | `authorizationId`, `authorizedAt`, `operatorReference` |
| EVT-010 | `AuthorizationDenied` | Operadora negou | `authorizationId`, `deniedAt`, `denialReasonCode`, `denialReason` |
| EVT-011 | `AuthorizationCancelled` | Cancelamento solicitado e confirmado | `authorizationId`, `cancelledAt`, `reason` |
| EVT-012 | `DispatchRetryScheduled` | Retry agendado apos falha tecnica | `authorizationId`, `dispatchId`, `nextAttemptAt` |
| EVT-013 | `DispatchFailedTechnical` | Falha tecnica apos tentativa | `authorizationId`, `dispatchId`, `errorCode`, `errorMessage` |
| EVT-014 | `DispatchMovedToDeadLetter` | Exaustao de retries | `authorizationId`, `dispatchId`, `attempts`, `lastErrorCode` |
| EVT-015 | `IdempotencyConflictDetected` | Chave ja processada com payload divergente | `idempotencyKey`, `authorizationId`, `detectedAt` |
| EVT-016 | `AuditTrailRecorded` | Registro de auditoria persistido | `aggregateType`, `aggregateId`, `action`, `actor` |

## Regras de Versionamento

- Alteracao breaking em payload incrementa `eventVersion`.
- Alteracao nao breaking (novo campo opcional) mantem `eventVersion`.
- Consumidores devem ignorar campos desconhecidos.
