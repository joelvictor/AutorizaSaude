# Ports e Contratos Internos

## authorization module

- `CreateAuthorizationUseCase`
- `GetAuthorizationStatusQuery`
- `CancelAuthorizationUseCase`
- publica `EVT-001`, `EVT-002`, `EVT-009`, `EVT-010`, `EVT-011`

## tiss-guide module

- `GenerateTissXmlPort`
- `ValidateTissXmlPort`
- consome `EVT-001` e publica `EVT-003`/`EVT-004`

## operator-dispatch module

- `DispatchToOperatorPort`
- `PollOperatorStatusPort`
- `RetryPolicyPort`
- consome `EVT-003` e publica `EVT-005`..`EVT-014`

## audit module

- `RecordAuditPort`
- consome todos os eventos de dominio e publica `EVT-016`

## idempotency module

- `IdempotencyGuardPort`
- executado no boundary de API para comandos mutaveis
- publica `EVT-015` quando detectar conflito
