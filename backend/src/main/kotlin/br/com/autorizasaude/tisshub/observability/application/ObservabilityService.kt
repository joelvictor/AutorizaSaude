package br.com.autorizasaude.tisshub.observability.application

import br.com.autorizasaude.tisshub.observability.infrastructure.ObservabilityRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt

data class OperatorLatencyP95(
    val operatorCode: String,
    val p95LatencyMs: Long,
    val samples: Int
)

data class OperatorSuccessRate(
    val operatorCode: String,
    val dispatchType: String,
    val successful: Long,
    val total: Long,
    val successRate: Double
)

data class QueueSnapshot(
    val dispatchRetryQueue: Long,
    val dispatchDeadLetter: Long,
    val outboxRetryQueue: Long,
    val outboxDeadLetter: Long
)

data class ObservabilitySummary(
    val latencyP95ByOperator: List<OperatorLatencyP95>,
    val successRateByOperatorAndType: List<OperatorSuccessRate>,
    val queues: QueueSnapshot
)

@ApplicationScoped
class ObservabilityService(
    private val repository: ObservabilityRepository
) {
    fun summary(tenantId: UUID): ObservabilitySummary {
        val latencyP95ByOperator = repository.fetchDispatchLatencySamples(tenantId)
            .groupBy { it.operatorCode }
            .map { (operatorCode, samples) ->
                val sorted = samples.map { it.latencyMs }.sorted()
                val p95Index = (ceil(0.95 * sorted.size.toDouble()).toInt() - 1).coerceAtLeast(0)
                OperatorLatencyP95(
                    operatorCode = operatorCode,
                    p95LatencyMs = sorted[p95Index],
                    samples = sorted.size
                )
            }
            .sortedBy { it.operatorCode }

        val successRateByOperatorAndType = repository.fetchDispatchSuccessBuckets(tenantId)
            .map { bucket ->
                val successRate = if (bucket.total == 0L) {
                    0.0
                } else {
                    ((bucket.successful.toDouble() / bucket.total.toDouble()) * 10000.0).roundToInt() / 100.0
                }
                OperatorSuccessRate(
                    operatorCode = bucket.operatorCode,
                    dispatchType = bucket.dispatchType,
                    successful = bucket.successful,
                    total = bucket.total,
                    successRate = successRate
                )
            }
            .sortedWith(compareBy<OperatorSuccessRate> { it.operatorCode }.thenBy { it.dispatchType })

        return ObservabilitySummary(
            latencyP95ByOperator = latencyP95ByOperator,
            successRateByOperatorAndType = successRateByOperatorAndType,
            queues = QueueSnapshot(
                dispatchRetryQueue = repository.countDispatchRetryQueue(tenantId),
                dispatchDeadLetter = repository.countDispatchDeadLetter(tenantId),
                outboxRetryQueue = repository.countOutboxRetryQueue(tenantId),
                outboxDeadLetter = repository.countOutboxDeadLetter(tenantId)
            )
        )
    }
}
