# Mapeamento Evento -> Persistencia

| Evento | Escrita transacional | Read model impactado |
|---|---|---|
| EVT-001 | `authorizations` + `authorization_procedures` | `authorization_summary` |
| EVT-002 | update status para `VALIDATED` | `authorization_timeline` |
| EVT-003 | insert em `tiss_guides` | `authorization_documents` |
| EVT-004 | update `tiss_guides.validation_status=FAILED` | `authorization_alerts` |
| EVT-005 | insert em `operator_dispatches` | `dispatch_monitor` |
| EVT-006 | update envio em `operator_dispatches` | `dispatch_monitor` |
| EVT-007 | update protocolo externo | `authorization_summary` |
| EVT-008 | append timeline tecnico | `authorization_timeline` |
| EVT-009 | update status `AUTHORIZED` | `authorization_summary` |
| EVT-010 | update status `DENIED` | `authorization_summary` |
| EVT-011 | update status `CANCELLED` | `authorization_summary` |
| EVT-012 | update `next_attempt_at` | `dispatch_monitor` |
| EVT-013 | update erro tecnico | `dispatch_monitor` |
| EVT-014 | move para DLQ logica | `dispatch_monitor` |
| EVT-015 | insert auditoria de conflito | `audit_console` |
| EVT-016 | insert em `audit_trail` | `audit_console` |

## Determinismo

- Cada evento deve gerar no maximo um write handler idempotente por aggregate.
- Leitura deve ser reconstruivel por replay do `outbox_events` em ordem de `occurred_at`.
