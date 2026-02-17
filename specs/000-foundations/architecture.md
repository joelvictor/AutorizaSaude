# TISS Hub - Arquitetura Base

## Estilo Arquitetural

- `Modular Monolith` para reduzir latencia de comunicacao interna e simplificar deploy na Fase 1.
- `EDA Interna` para desacoplamento entre modulos por eventos de dominio/versionados.
- `Spec-Driven Development` como fonte de verdade dos contratos antes de implementacao.

## Modulos do Monolito

- `tenant`: isolamento logico por tenant e politicas de acesso.
- `authorization`: ciclo de vida da autorizacao medica.
- `tiss-guide`: geracao/validacao TISS XML e regras XSD.
- `operator-dispatch`: estrategia tecnica por operadora (Tipo A/B/C).
- `audit`: trilha de auditoria LGPD.
- `integration`: adaptadores HTTP/SOAP/portal.
- `observability`: metricas, logs estruturados e tracing.
- `idempotency`: deduplicacao por chave de requisicao.

## Fronteiras

- Cada modulo expoe `ports` internos e publica eventos no bus interno.
- Sem acesso direto entre persistencias internas; interacao por comandos/eventos.
- Consistencia forte dentro de aggregate, eventual entre modulos.

## Tenancy

- Todas as tabelas de dominio possuem `tenant_id`.
- Todos os eventos possuem `tenantId`.
- Todas as APIs exigem `X-Tenant-Id`.

## Versionamento de Contratos

- OpenAPI com versao semantica.
- Eventos com `eventType` + `eventVersion`.
- Mudancas breaking exigem nova versao de endpoint/evento.
