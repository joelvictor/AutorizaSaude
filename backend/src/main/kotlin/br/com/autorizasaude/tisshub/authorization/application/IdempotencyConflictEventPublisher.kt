package br.com.autorizasaude.tisshub.authorization.application

import br.com.autorizasaude.tisshub.authorization.infrastructure.OutboxEventRepository
import br.com.autorizasaude.tisshub.shared.events.DomainEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@ApplicationScoped
class IdempotencyConflictEventPublisher(
    private val outboxEventRepository: OutboxEventRepository
) {
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun publish(
        tenantId: UUID,
        correlationId: UUID,
        idempotencyKey: String,
        authorizationId: UUID
    ) {
        outboxEventRepository.append(
            aggregateType = "IDEMPOTENCY",
            aggregateId = UUID.randomUUID(),
            event = DomainEvent(
                eventId = UUID.randomUUID(),
                eventType = "EVT-015",
                eventVersion = 1,
                occurredAt = OffsetDateTime.now(),
                tenantId = tenantId,
                correlationId = correlationId,
                payload = mapOf(
                    "idempotencyKey" to idempotencyKey,
                    "authorizationId" to authorizationId,
                    "detectedAt" to OffsetDateTime.now().toString()
                )
            )
        )
    }
}
