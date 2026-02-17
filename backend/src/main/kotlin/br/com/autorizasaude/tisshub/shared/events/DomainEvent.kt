package br.com.autorizasaude.tisshub.shared.events

import java.time.OffsetDateTime
import java.util.UUID

data class DomainEvent(
    val eventId: UUID,
    val eventType: String,
    val eventVersion: Int,
    val occurredAt: OffsetDateTime,
    val tenantId: UUID,
    val correlationId: UUID,
    val payload: Map<String, Any?>
)
