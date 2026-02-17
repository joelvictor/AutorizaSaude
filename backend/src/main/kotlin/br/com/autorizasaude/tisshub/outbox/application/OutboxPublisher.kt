package br.com.autorizasaude.tisshub.outbox.application

import br.com.autorizasaude.tisshub.authorization.infrastructure.OutboxPendingEvent

fun interface OutboxPublisher {
    fun publish(event: OutboxPendingEvent)
}
