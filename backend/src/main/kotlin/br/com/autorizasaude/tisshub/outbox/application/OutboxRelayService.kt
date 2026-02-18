package br.com.autorizasaude.tisshub.outbox.application

import br.com.autorizasaude.tisshub.authorization.infrastructure.OutboxEventRepository
import br.com.autorizasaude.tisshub.authorization.infrastructure.OutboxDeadLetterEntry
import br.com.autorizasaude.tisshub.authorization.infrastructure.OutboxProcessingStats
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.OffsetDateTime
import java.util.UUID
import java.util.Optional

data class OutboxRelayResult(
    val scanned: Int,
    val published: Int,
    val failed: Int,
    val deadLettered: Int
)

data class OutboxDeadLetterRequeueResult(
    val requeued: Int
)

@ApplicationScoped
class OutboxRelayService(
    private val outboxEventRepository: OutboxEventRepository,
    private val outboxPublisher: OutboxPublisher,
    @param:ConfigProperty(name = "tisshub.outbox.retry-delays-seconds")
    private val retryDelaysRaw: Optional<String>
) {
    private val retryDelaysSeconds = parseRetryDelays(retryDelaysRaw.orElse(DEFAULT_RETRY_DELAYS_SECONDS))

    @Scheduled(every = "{tisshub.outbox.interval}")
    fun scheduledRelay() {
        processPending()
    }

    fun processPending(): OutboxRelayResult {
        val batchSize = OUTBOX_BATCH_SIZE
        val retryDelays = retryDelaysSeconds
        // Number of failures allowed before DLQ = initial failure + configured retry windows.
        val maxAttempts = retryDelays.size + 1
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
                val nextAttempts = event.publishAttempts + 1
                val nextAttemptAt = if (nextAttempts >= maxAttempts) {
                    null
                } else {
                    OffsetDateTime.now().plusSeconds(retryDelays[nextAttempts - 1])
                }
                val moved = outboxEventRepository.markFailure(
                    event = event,
                    reason = ex.message ?: "unknown-publish-error",
                    maxAttempts = maxAttempts,
                    nextAttemptAt = nextAttemptAt
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

    fun deadLetters(tenantId: UUID, limit: Int): List<OutboxDeadLetterEntry> {
        val safeLimit = limit.coerceIn(1, 200)
        return outboxEventRepository.findDeadLetters(tenantId, safeLimit)
    }

    fun requeueDeadLetters(tenantId: UUID, correlationId: UUID, limit: Int): OutboxDeadLetterRequeueResult {
        val safeLimit = limit.coerceIn(1, 200)
        val requeued = outboxEventRepository.requeueDeadLetters(tenantId, correlationId, safeLimit)
        return OutboxDeadLetterRequeueResult(requeued = requeued)
    }

    companion object {
        private const val OUTBOX_BATCH_SIZE = 100
        private const val DEFAULT_RETRY_DELAYS_SECONDS = "5,15,45,120,300"
    }

    private fun parseRetryDelays(raw: String): List<Long> {
        val delays = raw.split(",")
            .mapNotNull { it.trim().takeIf { value -> value.isNotEmpty() } }
            .mapNotNull { it.toLongOrNull() }
            .filter { it >= 0 }
        return if (delays.isEmpty()) {
            DEFAULT_RETRY_DELAYS_SECONDS.split(",").map { it.toLong() }
        } else {
            delays
        }
    }
}
