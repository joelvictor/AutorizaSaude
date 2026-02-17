package br.com.autorizasaude.tisshub.outbox.application

import br.com.autorizasaude.tisshub.authorization.infrastructure.OutboxEventRepository
import br.com.autorizasaude.tisshub.authorization.infrastructure.OutboxProcessingStats
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import java.time.OffsetDateTime

data class OutboxRelayResult(
    val scanned: Int,
    val published: Int,
    val failed: Int,
    val deadLettered: Int
)

@ApplicationScoped
class OutboxRelayService(
    private val outboxEventRepository: OutboxEventRepository,
    private val outboxPublisher: OutboxPublisher
) {
    @Scheduled(every = "{tisshub.outbox.interval}")
    fun scheduledRelay() {
        processPending()
    }

    fun processPending(): OutboxRelayResult {
        val batchSize = OUTBOX_BATCH_SIZE
        val maxAttempts = OUTBOX_MAX_ATTEMPTS
        val pending = outboxEventRepository.findPending(batchSize)

        var published = 0
        var failed = 0
        var deadLettered = 0

        pending.forEach { event ->
            try {
                outboxPublisher.publish(event)
                outboxEventRepository.markPublished(event.id, OffsetDateTime.now())
                published += 1
            } catch (ex: Exception) {
                failed += 1
                val moved = outboxEventRepository.markFailure(
                    event = event,
                    reason = ex.message ?: "unknown-publish-error",
                    maxAttempts = maxAttempts
                )
                if (moved) {
                    deadLettered += 1
                }
            }
        }

        return OutboxRelayResult(
            scanned = pending.size,
            published = published,
            failed = failed,
            deadLettered = deadLettered
        )
    }

    fun stats(): OutboxProcessingStats = outboxEventRepository.stats()

    companion object {
        private const val OUTBOX_BATCH_SIZE = 100
        private const val OUTBOX_MAX_ATTEMPTS = 3
    }
}
