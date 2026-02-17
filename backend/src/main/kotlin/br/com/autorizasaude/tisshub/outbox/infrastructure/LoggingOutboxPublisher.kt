package br.com.autorizasaude.tisshub.outbox.infrastructure

import br.com.autorizasaude.tisshub.authorization.infrastructure.OutboxPendingEvent
import br.com.autorizasaude.tisshub.outbox.application.OutboxPublisher
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.util.Optional

@ApplicationScoped
class LoggingOutboxPublisher(
    @param:ConfigProperty(name = "tisshub.outbox.fail-event-types")
    private val failEventTypesRaw: Optional<String>
) : OutboxPublisher {
    private val logger = Logger.getLogger(LoggingOutboxPublisher::class.java)

    override fun publish(event: OutboxPendingEvent) {
        val failEventTypes = failEventTypesRaw.orElse("")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        if (event.eventType in failEventTypes) {
            throw IllegalStateException("Simulated publish failure for ${event.eventType}")
        }

        logger.infof(
            "Outbox published eventId=%s type=%s tenant=%s",
            event.eventId,
            event.eventType,
            event.tenantId
        )
    }
}
