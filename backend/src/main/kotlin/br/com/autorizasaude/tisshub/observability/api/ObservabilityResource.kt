package br.com.autorizasaude.tisshub.observability.api

import br.com.autorizasaude.tisshub.observability.application.ObservabilityService
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

data class OperatorLatencyP95ResponseItem(
    val operatorCode: String,
    val p95LatencyMs: Long,
    val samples: Int
)

data class OperatorSuccessRateResponseItem(
    val operatorCode: String,
    val dispatchType: String,
    val successful: Long,
    val total: Long,
    val successRate: Double
)

data class QueueSnapshotResponse(
    val dispatchRetryQueue: Long,
    val dispatchDeadLetter: Long,
    val outboxRetryQueue: Long,
    val outboxDeadLetter: Long
)

data class ObservabilitySummaryResponse(
    val latencyP95ByOperator: List<OperatorLatencyP95ResponseItem>,
    val successRateByOperatorAndType: List<OperatorSuccessRateResponseItem>,
    val queues: QueueSnapshotResponse
)

@Path("/v1/operations/observability")
@Produces(MediaType.APPLICATION_JSON)
class ObservabilityResource(
    private val observabilityService: ObservabilityService
) {
    @GET
    @Path("/summary")
    fun summary(
        @HeaderParam("X-Tenant-Id") tenantIdHeader: String?
    ): ObservabilitySummaryResponse {
        val tenantId = parseRequiredUuidHeader("X-Tenant-Id", tenantIdHeader)
        val summary = observabilityService.summary(tenantId)
        return ObservabilitySummaryResponse(
            latencyP95ByOperator = summary.latencyP95ByOperator.map {
                OperatorLatencyP95ResponseItem(
                    operatorCode = it.operatorCode,
                    p95LatencyMs = it.p95LatencyMs,
                    samples = it.samples
                )
            },
            successRateByOperatorAndType = summary.successRateByOperatorAndType.map {
                OperatorSuccessRateResponseItem(
                    operatorCode = it.operatorCode,
                    dispatchType = it.dispatchType,
                    successful = it.successful,
                    total = it.total,
                    successRate = it.successRate
                )
            },
            queues = QueueSnapshotResponse(
                dispatchRetryQueue = summary.queues.dispatchRetryQueue,
                dispatchDeadLetter = summary.queues.dispatchDeadLetter,
                outboxRetryQueue = summary.queues.outboxRetryQueue,
                outboxDeadLetter = summary.queues.outboxDeadLetter
            )
        )
    }

    private fun parseRequiredUuidHeader(name: String, value: String?): UUID {
        val raw = value?.trim().takeUnless { it.isNullOrEmpty() }
            ?: throw WebApplicationException("$name header is required", Response.Status.BAD_REQUEST)
        return try {
            UUID.fromString(raw)
        } catch (_: IllegalArgumentException) {
            throw WebApplicationException("$name must be a valid UUID", Response.Status.BAD_REQUEST)
        }
    }
}
